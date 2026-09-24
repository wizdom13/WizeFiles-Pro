package com.wisso.wizefiles.feature.share

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.entitlement.ProFeature
import com.wisso.wizefiles.core.entitlement.ProFeatureAccess
import com.wisso.wizefiles.util.AppLog
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicLong

class LocalShareService : Service() {
    private var server:LocalHttpServer?=null
    private var ftpServer:LocalFtpServer?=null
    private var registration:NsdManager.RegistrationListener?=null
    private var active:ActiveLocalShare?=null
    private val handler=Handler(Looper.getMainLooper())
    private var timeoutMinutes=15
    private var stopReason=ShareStopReason.USER
    private var networkCallback:ConnectivityManager.NetworkCallback?=null
    private val uploadedBytes=AtomicLong()
    private val downloadedBytes=AtomicLong()
    private val timeoutCheck=object:Runnable{override fun run(){val http=server;val ftp=ftpServer;val clients=(http?.authenticatedClients?:0)+(ftp?.connectedClients?:0);val last=maxOf(http?.lastActivityMillis?:0,ftp?.lastActivityMillis?:0);if(http!=null&&timeoutMinutes>0&&clients==0&&System.currentTimeMillis()-last>=timeoutMinutes*60_000L){stopReason=ShareStopReason.INACTIVITY;stopSelf()}else handler.postDelayed(this,60_000)}}

    override fun onBind(intent:Intent?):IBinder?=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        when(intent?.action){
            ACTION_STOP->{
                AppLog.i(LOG_TAG,"Stop requested")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START->if(ProFeatureAccess.isAllowed(ProFeature.BUILT_IN_SERVERS)){
                AppLog.i(LOG_TAG,"Start requested")
                runCatching{start(intent.getStringExtra(EXTRA_PROFILE_ID).orEmpty())}
                    .onFailure{
                        AppLog.e(LOG_TAG,"Local sharing failed to start",it)
                        stopReason=ShareStopReason.ERROR
                        stopSelf()
                    }
            }else{
                AppLog.w(LOG_TAG,"Start rejected because built-in server access is unavailable")
                stopReason=ShareStopReason.ERROR
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun start(profileId:String) {
        if(server!=null)return
        createNotificationChannel();startForeground(NOTIFICATION_ID,notification("Starting local sharing…"),ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        ShareDatabase.markInterruptedSessions();val profile=requireNotNull(ShareDatabase.profile(profileId)); val roots=ShareDatabase.roots(profileId); require(roots.isNotEmpty());timeoutMinutes=profile.inactivityMinutes
        val network=LocalNetworkSelector.select(this);AppLog.i(LOG_TAG,"Approved LAN selected interface=${network.interfaceName} roots=${roots.size}");val session=ShareSession(profileId=profile.id,networkIdentity=network.identity)
        ShareDatabase.startSession(session); val auth=ShareAuthenticator(); val gateway=ShareGateway(profile,roots)
        require(ShareProtocol.BROWSER in profile.protocols) { "Browser access is required in v1" }
        val tlsIdentity=if(profile.certificateAlias.isNotBlank() || ShareProtocol.FTPS in profile.protocols)LocalTls.identity(profile.certificateAlias.ifBlank{"wizefiles-local-sharing"}) else null
        val http=LocalHttpServer(network.address,profile.browserPort,gateway,auth,session.id,onBytesDownloaded={downloadedBytes.addAndGet(it)},onBytesUploaded={uploadedBytes.addAndGet(it)},onPendingAction={getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID,notification("Approval required for ${it.sourceRelativePath}"))},tlsContext=tlsIdentity?.context)
        http.start();server=http
        val ftpCredentials=auth.ftpCredentials()
        if(ShareProtocol.FTP in profile.protocols || ShareProtocol.FTPS in profile.protocols){ftpServer=LocalFtpServer(network.address,profile.ftpPort,profile.passivePortStart..profile.passivePortEnd,gateway,session.id,ftpCredentials.second,tlsIdentity?.context,requireTls=ShareProtocol.FTPS in profile.protocols&&ShareProtocol.FTP !in profile.protocols,onBytesDownloaded={downloadedBytes.addAndGet(it)},onBytesUploaded={uploadedBytes.addAndGet(it)}).also{it.start()}}
        active=ActiveLocalShare(session.id,profile.id,network.address.hostAddress.orEmpty(),http.port,auth.visiblePairingCode(),auth.qrToken(),if(ftpServer!=null)profile.ftpPort else 0,if(ftpServer!=null)ftpCredentials.first else "",if(ftpServer!=null)ftpCredentials.second else "",tlsIdentity?.fingerprint.orEmpty(),if(tlsIdentity==null)"http" else "https").also { current=it };instance=this
        AppLog.i(LOG_TAG,"Local sharing active scheme=${active?.scheme} browserPort=${http.port} ftpEnabled=${ftpServer!=null}")
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID,notification())
        advertise(http.port)
        ShareDatabase.updateSession(session.copy(state=ShareSessionState.ACTIVE))
        monitorNetwork(network);handler.postDelayed(timeoutCheck,60_000)
    }

    override fun onDestroy(){
        handler.removeCallbacks(timeoutCheck);networkCallback?.let{runCatching{getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it)}};networkCallback=null
        val snapshot=active;val clientCount=(server?.authenticatedClients?:0)+(ftpServer?.connectedClients?:0);AppLog.i(LOG_TAG,"Stopping local sharing reason=$stopReason clients=$clientCount uploadedBytes=${uploadedBytes.get()} downloadedBytes=${downloadedBytes.get()}");server?.close();server=null;ftpServer?.close();ftpServer=null;registration?.let { runCatching { getSystemService(NsdManager::class.java).unregisterService(it) } };registration=null
        if(snapshot!=null) ShareDatabase.updateSession(ShareSession(id=snapshot.sessionId,profileId=snapshot.profileId,networkIdentity="",state=ShareSessionState.STOPPED,endedAtMillis=System.currentTimeMillis(),stopReason=stopReason,uploadedBytes=uploadedBytes.get(),downloadedBytes=downloadedBytes.get(),clientCount=clientCount))
        active=null;current=null;instance=null;super.onDestroy()
    }

    private fun advertise(port:Int){
        val info=NsdServiceInfo().apply{serviceName="WizeFiles";serviceType=if(active?.scheme=="https")"_https._tcp." else "_http._tcp.";setPort(port)}
        registration=object:NsdManager.RegistrationListener{
            override fun onRegistrationFailed(s:NsdServiceInfo,e:Int){AppLog.w(LOG_TAG,"LAN service advertisement failed errorCode=$e")}
            override fun onUnregistrationFailed(s:NsdServiceInfo,e:Int){AppLog.w(LOG_TAG,"LAN service unregistration failed errorCode=$e")}
            override fun onServiceRegistered(s:NsdServiceInfo){AppLog.i(LOG_TAG,"LAN service advertised type=${s.serviceType}")}
            override fun onServiceUnregistered(s:NsdServiceInfo){AppLog.i(LOG_TAG,"LAN service advertisement stopped")}
        }.also{getSystemService(NsdManager::class.java).registerService(info,NsdManager.PROTOCOL_DNS_SD,it)}
    }
    private fun monitorNetwork(selected:SelectedLocalNetwork){
        val manager=getSystemService(ConnectivityManager::class.java)
        networkCallback=object:ConnectivityManager.NetworkCallback(){
            override fun onLost(network:Network){if(network==selected.network)stopForNetworkChange()}
            override fun onCapabilitiesChanged(network:Network,capabilities:NetworkCapabilities){if(network==selected.network&&!LocalNetworkSelector.isApproved(capabilities))stopForNetworkChange()}
            override fun onLinkPropertiesChanged(network:Network,properties:LinkProperties){if(network==selected.network&&!LocalNetworkSelector.matches(selected,properties))stopForNetworkChange()}
        }.also{callback->
            val request=NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build()
            manager.registerNetworkCallback(request,callback)
        }
    }
    private fun stopForNetworkChange(){AppLog.w(LOG_TAG,"Approved LAN changed; stopping local sharing");stopReason=ShareStopReason.NETWORK_CHANGED;stopSelf()}
    private fun createNotificationChannel(){getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID,getString(R.string.app_name),NotificationManager.IMPORTANCE_LOW))}
    private fun notification(message:String="${active?.scheme}://${active?.address}:${active?.port} • Tap Stop when finished"):Notification {val stop=PendingIntent.getService(this,1,Intent(this,LocalShareService::class.java).setAction(ACTION_STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);return NotificationCompat.Builder(this,CHANNEL_ID).setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("PC Access & Local Sharing").setContentText(message).setOngoing(true).addAction(0,"Stop",stop).build()}

    companion object {private const val LOG_TAG="LocalShare";private const val CHANNEL_ID="local_share";private const val NOTIFICATION_ID=47;const val ACTION_START="com.wisso.wizefiles.share.START";const val ACTION_STOP="com.wisso.wizefiles.share.STOP";const val EXTRA_PROFILE_ID="profile_id";@Volatile internal var current:ActiveLocalShare?=null;@Volatile private var instance:LocalShareService?=null;fun activeClients():List<String> = instance?.server?.activeClientIds().orEmpty();fun revokeClient(clientId:String){instance?.server?.revokeClient(clientId)}}
}

internal data class ActiveLocalShare(val sessionId:String,val profileId:String,val address:String,val port:Int,val pairingCode:String,val qrToken:String,val ftpPort:Int=0,val ftpUser:String="",val ftpPassword:String="",val certificateFingerprint:String="",val scheme:String="http")
