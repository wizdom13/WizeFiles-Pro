package com.wisso.wizefiles.feature.filebrowser

import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import java.nio.file.Path
import com.wisso.wizefiles.provider.common.PathObservable
import com.wisso.wizefiles.provider.common.observe
import com.wisso.wizefiles.util.backgroundExecutor
import com.wisso.wizefiles.util.closeSafe
import java.io.Closeable
import java.io.IOException

class PathObserver(path: Path, @MainThread onChange: () -> Unit) : Closeable {
    private var pathObservable: PathObservable? = null

    private var closed = false
    private val lock = Any()

    init {
        backgroundExecutor.execute {
            synchronized(lock) {
                if (closed) {
                    return@execute
                }
                pathObservable = try {
                    path.observe(THROTTLE_INTERVAL_MILLIS)
                } catch (e: UnsupportedOperationException) {
                    // Ignored.
                    return@execute
                } catch (e: IOException) {
                    // Ignored.
                    com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
                    return@execute
                }.apply {
                    val mainHandler = Handler(Looper.getMainLooper())
                    addObserver { mainHandler.post(onChange) }
                }
            }
        }
    }

    override fun close() {
        backgroundExecutor.execute {
            synchronized(lock) {
                if (closed) {
                    return@execute
                }
                closed = true
                pathObservable?.closeSafe()
            }
        }
    }

    companion object {
        private const val THROTTLE_INTERVAL_MILLIS = 1000L
    }
}
