package com.wisso.wizefiles.provider.root

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.topjohnwu.superuser.NoShellException
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import com.wisso.wizefiles.BuildConfig
import com.wisso.wizefiles.core.entitlement.ProFeature
import com.wisso.wizefiles.core.entitlement.ProFeatureAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import com.wisso.wizefiles.provider.remote.IFileServiceBridge
import com.wisso.wizefiles.provider.remote.RemoteFileServiceInterface
import com.wisso.wizefiles.provider.remote.BridgeUnavailableException
import com.wisso.wizefiles.util.createIntent
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object LibSuFileServiceLauncher {
    private val lock = Any()

    init {
        Shell.enableVerboseLogging = BuildConfig.ENABLE_ROOT_VERBOSE_LOGGING
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setInitializers(LibSuShellInitializer::class.java)
                .setFlags(Shell.FLAG_MOUNT_MASTER or Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(TimeUnit.MILLISECONDS.toSeconds(RootFileService.TIMEOUT_MILLIS))
        )
    }

    fun isSuAvailable(): Boolean {
        if (!ProFeatureAccess.isAllowed(ProFeature.ROOT_TOOLS)) return false
        // @see com.topjohnwu.superuser.Shell.rootAccess
        return try {
            Runtime.getRuntime().exec("su --version")
            true
        } catch (e: IOException) {
            // java.io.IOException: Cannot run program "su": error=2, No such file or directory
            false
        }
    }

    @Throws(BridgeUnavailableException::class)
    fun launchService(): IFileServiceBridge {
        synchronized(lock) {
            // libsu won't call back when su isn't available.
            if (!isSuAvailable()) {
                throw BridgeUnavailableException("Root isn't available")
            }
            return try {
                runBlocking {
                    try {
                        withTimeout(RootFileService.TIMEOUT_MILLIS) {
                            // Proactively create the shell because RootService doesn't allow us to
                            // handle errors during shell creation.
                            suspendCancellableCoroutine<Unit> { continuation ->
                                // Shell.getShell(GetShellCallback) doesn't allow handling errors.
                                Shell.EXECUTOR.submit {
                                    try {
                                        Shell.getShell()
                                        continuation.resume(Unit)
                                    } catch (e: NoShellException) {
                                        continuation.resumeWithException(
                                            BridgeUnavailableException(e)
                                        )
                                    }
                                }
                            }
                            suspendCancellableCoroutine { continuation ->
                                val intent = LibSuFileService::class.createIntent()
                                val connection = object : ServiceConnection {
                                    override fun onServiceConnected(
                                        name: ComponentName,
                                        service: IBinder
                                    ) {
                                        val serviceInterface =
                                            IFileServiceBridge.Stub.asInterface(service)
                                        continuation.resume(serviceInterface)
                                    }

                                    override fun onServiceDisconnected(name: ComponentName) {
                                        if (continuation.isActive) {
                                            continuation.resumeWithException(
                                                BridgeUnavailableException(
                                                    "libsu service disconnected"
                                                )
                                            )
                                        }
                                    }

                                    override fun onBindingDied(name: ComponentName) {
                                        if (continuation.isActive) {
                                            continuation.resumeWithException(
                                                BridgeUnavailableException("libsu binding died")
                                            )
                                        }
                                    }

                                    override fun onNullBinding(name: ComponentName) {
                                        if (continuation.isActive) {
                                            continuation.resumeWithException(
                                                BridgeUnavailableException("libsu binding is null")
                                            )
                                        }
                                    }
                                }
                                launch(Dispatchers.Main.immediate) {
                                    RootService.bind(intent, connection)
                                    continuation.invokeOnCancellation {
                                        launch(Dispatchers.Main.immediate) {
                                            RootService.unbind(connection)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: TimeoutCancellationException) {
                        throw BridgeUnavailableException(e)
                    }
                }
            } catch (e: InterruptedException) {
                throw BridgeUnavailableException(e)
            }
        }
    }
}

private class LibSuShellInitializer : Shell.Initializer() {
    // Prevent normal shells from being created and set as the main shell.
    override fun onInit(context: Context, shell: Shell): Boolean = shell.isRoot
}

class LibSuFileService : RootService() {
    override fun onCreate() {
        super.onCreate()

        RootFileService.main()
    }

    override fun onBind(intent: Intent): IBinder = RemoteFileServiceInterface()
}
