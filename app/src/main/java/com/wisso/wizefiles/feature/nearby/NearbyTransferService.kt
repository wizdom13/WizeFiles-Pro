package com.wisso.wizefiles.feature.nearby

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.Strategy
import com.wisso.wizefiles.R
import com.wisso.wizefiles.feature.transfer.LongRunningOperationLimiter
import com.wisso.wizefiles.feature.sync.SyncPathResolver
import com.wisso.wizefiles.feature.transfer.TransferDatabase
import com.wisso.wizefiles.feature.transfer.TransferItemState
import com.wisso.wizefiles.feature.transfer.TransferOperationState
import com.wisso.wizefiles.feature.transfer.TransferOperationType
import com.wisso.wizefiles.feature.transfer.TransferProgressCheckpoint
import com.wisso.wizefiles.feature.transfer.TransferProgress
import com.wisso.wizefiles.feature.transfer.TransferRepository
import com.wisso.wizefiles.storage.OperationCancellation
import com.wisso.wizefiles.storage.OperationCancelledException
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.storage.path.toUriString
import org.json.JSONObject
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.max

class NearbyTransferService : Service() {
    private val domainSession = NearbySessionDomainController()
    private lateinit var connections: ConnectionsClient
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val store by lazy { NearbySessionStore(this) }
    private val recovery by lazy { NearbyTransferRecovery(store) }
    private val foregroundNotifier by lazy { NearbyTransferForegroundNotifier(this) }
    private val sessionRegistry = NearbySessionRegistry()
    private val payloadStore = NearbyPayloadStore(MAX_PENDING_INCOMING_PAYLOADS)
    private val incomingWriter by lazy {
        NearbyIncomingStreamWriter(
            operationId={operationId},
            activeSessionId={sessionId},
            conflictPolicy={conflictPolicy},
            totalBytes={offer?.totalBytes ?: 0},
            pauseRequested={pauseRequested},
            sendControl=::sendControl,
            onProgress={item,transferred,total ->
                handler.post {
                    publish(
                        NearbyPhase.TRANSFERRING,
                        transferred=transferred,
                        total=total,
                        message=item.relativePath
                    )
                }
            },
            onCompleted={complete ->
                payloadStore.completeIncoming()
                if(complete) {
                    handler.post {
                        publish(NearbyPhase.CONNECTED,message="Finalizing transfer…")
                    }
                }
            },
            onFailure={failure ->
                handler.post {
                    failSession(failure.message ?: "Incoming file failed",true)
                }
            }
        )
    }
    private var role: NearbyRole? = null
    private var endpointId: String?
        get() = sessionRegistry.connectedEndpointId
        set(value) { sessionRegistry.connectedEndpointId = value }
    private var peerName = ""
    private var sessionId = ""
    private var operationId = ""
    private var offer: NearbyOffer? = null
    private var conflictPolicy = NearbyConflictPolicy.KEEP_BOTH
    private var remoteOffsets = mutableMapOf<String, Long>()
    private val activePayloadId: Long?
        get() = payloadStore.activePayloadId
    @Volatile private var pauseRequested = false
    private var slotHeld = false
    private var resumeOperationId = ""
    private val discoveryTimeout = Runnable {
        if (endpointId == null && snapshot.phase in setOf(NearbyPhase.DISCOVERING, NearbyPhase.ADVERTISING)) {
            failSession("Nearby visibility timed out", recoverable = operationId.isNotEmpty())
        }
    }
    private val authenticationTimeout = Runnable {
        if (sessionRegistry.pendingAuthEndpointId != null) {
            rejectAuthentication("Verification QR expired")
        }
    }

    override fun onCreate() {
        super.onCreate()
        connections = Nearby.getConnectionsClient(this)
        instance = this
        foregroundNotifier.createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SEND -> startSend(intent.getStringArrayListExtra(EXTRA_SOURCE_URIS).orEmpty())
            ACTION_RECEIVE -> startReceive()
            ACTION_CONNECT -> connect(requireNotNull(intent.getStringExtra(EXTRA_ENDPOINT_ID)))
            ACTION_AUTH_APPROVE -> approveAuthenticationAndShowQr()
            ACTION_AUTH_SCAN -> scanAndAcceptAuthentication(
                requireNotNull(intent.getStringExtra(EXTRA_AUTH_QR))
            )
            ACTION_AUTH_REJECT -> rejectAuthentication("Connection declined")
            ACTION_ACCEPT_OFFER -> acceptOffer(
                requireNotNull(intent.getStringExtra(EXTRA_DESTINATION_URI)),
                NearbyConflictPolicy.valueOf(
                    requireNotNull(intent.getStringExtra(EXTRA_CONFLICT_POLICY))
                )
            )
            ACTION_REJECT_OFFER -> rejectOffer("Declined by recipient")
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume(intent.getStringExtra(EXTRA_OPERATION_ID).orEmpty())
            ACTION_CANCEL -> cancel("Cancelled")
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startSend(sourceUris: List<String>) {
        requireReady()
        resetTransport()
        role = NearbyRole.SEND
        pauseRequested = false
        startAsForeground("Preparing files…")
        publish(NearbyPhase.PLANNING, message = "Preparing files…")
        worker.execute {
            runCatching {
                sessionId = UUID.randomUUID().toString()
                offer = NearbyTransferPlanner.planSend(
                    sourceUris,
                    sessionId,
                    OperationCancellation { Thread.currentThread().isInterrupted }
                )
                operationId = requireNotNull(offer).operationId
                store.save(snapshotForStore())
            }.onSuccess { handler.post(::startDiscovery) }
                .onFailure { failure ->
                    handler.post {
                        if (failure is OperationCancelledException) {
                            cancel("Cancelled while preparing files", notifyPeer = false)
                        } else {
                            failSession(failure.message ?: "Could not prepare files")
                        }
                    }
                }
        }
    }

    private fun startReceive() {
        requireReady()
        resetTransport()
        role = NearbyRole.RECEIVE
        pauseRequested = false
        sessionId = "pending-${UUID.randomUUID()}"
        startAsForeground("Visible to nearby WizeFiles devices")
        startAdvertising()
    }

    private fun resume(requestedOperationId: String) {
        pauseRequested = false
        val active = operationId == requestedOperationId && endpointId != null
        if (active) {
            if (domainSession.state == com.wisso.wizefiles.storage.NearbySessionState.PAUSED) {
                domainSession.resumeTransfer()
            }
            val currentSession = sessionId
            recovery.markRunning(operationId)
            sendControl(NearbyProtocol.command("RESUME_REQUEST", currentSession))
            publish(NearbyPhase.TRANSFERRING, message = "Resuming transfer…")
            if (role == NearbyRole.SEND) sendNext()
            return
        }
        val saved = recovery.load(requestedOperationId)
            ?: return failSession("This nearby session can no longer be resumed")
        recovery.reconcilePayloads(saved, maxPendingIncoming = MAX_PENDING_INCOMING_PAYLOADS)
        requireReady()
        resetTransport()
        role = saved.role
        sessionId = saved.sessionId
        operationId = saved.operationId
        peerName = saved.peerName
        conflictPolicy = saved.conflictPolicy
        resumeOperationId = saved.operationId
        startAsForeground("Reconnect both devices to resume")
        runCatching { TransferRepository.transition(operationId, TransferOperationState.QUEUED) }
        if (role == NearbyRole.SEND) {
            worker.execute {
                runCatching { offer = NearbyTransferPlanner.rebuildSend(operationId, sessionId) }
                    .onSuccess { handler.post(::startDiscovery) }
                    .onFailure { handler.post { failSession(it.message ?: "Resume data is unavailable") } }
            }
        } else {
            startAdvertising()
        }
    }

    private fun startDiscovery() {
        domainSession.reset()
        domainSession.beginDiscovery()
        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        sessionRegistry.reset()
        val until = NearbyDeadlinePolicy.deadline(System.currentTimeMillis(), NEARBY_DISCOVERY_MILLIS)
        connections.startDiscovery(NEARBY_SERVICE_ID, transportAdapter.discoveryCallback, options)
            .addOnSuccessListener {
                publish(NearbyPhase.DISCOVERING, message = "Choose a nearby device", endsAt = until)
                handler.removeCallbacks(discoveryTimeout)
                handler.postDelayed(discoveryTimeout, NEARBY_DISCOVERY_MILLIS)
            }
            .addOnFailureListener { failSession(it.message ?: "Could not find nearby devices") }
    }

    private fun startAdvertising() {
        domainSession.reset()
        domainSession.beginDiscovery()
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        val until = NearbyDeadlinePolicy.deadline(System.currentTimeMillis(), NEARBY_DISCOVERY_MILLIS)
        connections.startAdvertising(nearbyDeviceName(), NEARBY_SERVICE_ID, transportAdapter.connectionCallback, options)
            .addOnSuccessListener {
                publish(
                    NearbyPhase.ADVERTISING,
                    message = "Visible to nearby WizeFiles devices",
                    endsAt = until
                )
                handler.removeCallbacks(discoveryTimeout)
                handler.postDelayed(discoveryTimeout, NEARBY_DISCOVERY_MILLIS)
            }
            .addOnFailureListener { failSession(it.message ?: "Could not become visible") }
    }

    private fun connect(id: String) {
        val target = sessionRegistry.endpoint(id) ?: return
        peerName = target.name
        connections.requestConnection(nearbyDeviceName(), id, transportAdapter.connectionCallback)
            .addOnFailureListener { failSession(it.message ?: "Could not connect") }
    }

    private fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
        if (info.serviceId != NEARBY_SERVICE_ID) return
        sessionRegistry.discovered(NearbyEndpoint(id, info.endpointName.take(64)))
        publish(snapshot.phase, endpoints = sessionRegistry.endpoints())
    }

    private fun onEndpointLost(id: String) {
        sessionRegistry.lost(id)
        publish(snapshot.phase, endpoints = sessionRegistry.endpoints())
    }

    private fun onConnectionInitiated(id: String, info: ConnectionInfo) {
        if (!domainSession.peerFound()) {
            connections.rejectConnection(id)
            failSession("Nearby connection callback arrived out of order")
            return
        }
        clearPendingAuthentication()
        val rawToken = info.rawAuthenticationToken
        if (rawToken.isEmpty()) {
            connections.rejectConnection(id)
            failSession("Nearby could not create a secure verification token", operationId.isNotEmpty())
            return
        }
        val authenticationEndsAtMillis = NearbyDeadlinePolicy.deadline(
            System.currentTimeMillis(),
            AUTHENTICATION_MILLIS
        )
        sessionRegistry.beginAuthentication(id, rawToken, authenticationEndsAtMillis)
        peerName = info.endpointName.take(64)
        handler.postDelayed(authenticationTimeout, AUTHENTICATION_MILLIS)
        publish(
            if (role == NearbyRole.RECEIVE) {
                NearbyPhase.AUTH_APPROVAL_REQUIRED
            } else {
                NearbyPhase.AUTH_SCAN_REQUIRED
            },
            authQr = "",
            authEndsAt = authenticationEndsAtMillis,
            message = ""
        )
    }

    private fun onConnectionResult(id: String, resolution: ConnectionResolution) {
        clearPendingAuthentication()
        if (resolution.status.statusCode != ConnectionsStatusCodes.STATUS_OK) {
            failSession("The connection was rejected or could not be established", operationId.isNotEmpty())
            return
        }
        endpointId = id
        if (!domainSession.authenticated()) {
            connections.disconnectFromEndpoint(id)
            failSession("Nearby authentication callback arrived out of order")
            return
        }
        handler.removeCallbacks(discoveryTimeout)
        connections.stopAdvertising()
        connections.stopDiscovery()
        publish(
            NearbyPhase.CONNECTED,
            authQr = "",
            authEndsAt = 0,
            message = "Connected to $peerName"
        )
        if (role == NearbyRole.SEND) {
            sendControl(NearbyProtocol.hello(sessionId, NearbyRole.SEND, resumeOperationId.isNotEmpty()))
            sendControl(NearbyProtocol.offer(requireNotNull(offer)))
        }
    }

    private fun onDisconnected(id: String) {
        endpointId = null
        if (domainSession.state !in setOf(
                com.wisso.wizefiles.storage.NearbySessionState.IDLE,
                com.wisso.wizefiles.storage.NearbySessionState.COMPLETED,
                com.wisso.wizefiles.storage.NearbySessionState.CANCELLED,
                com.wisso.wizefiles.storage.NearbySessionState.FAILED
            )) {
            failSession("Nearby peer disconnected", recoverable = operationId.isNotEmpty())
        }
        if (snapshot.phase !in setOf(NearbyPhase.COMPLETED, NearbyPhase.ERROR, NearbyPhase.IDLE)) {
            failSession("Connection lost. Reopen Nearby Transfer on both devices to resume.", true)
        }
    }

    private fun approveAuthenticationAndShowQr() {
        if (role != NearbyRole.RECEIVE) return
        val id = sessionRegistry.pendingAuthEndpointId ?: return
        val token = sessionRegistry.authenticationToken() ?: return
        if (NearbyDeadlinePolicy.isExpired(
                System.currentTimeMillis(), sessionRegistry.authenticationExpiresAtMillis
            )) {
            rejectAuthentication("Verification QR expired")
            return
        }
        val qr = NearbyQrAuthentication.encode(token)
        connections.acceptConnection(id, payloadCallback)
            .addOnSuccessListener {
                publish(
                    NearbyPhase.AUTH_QR_VISIBLE,
                    authQr = qr,
                    authEndsAt = sessionRegistry.authenticationExpiresAtMillis,
                    message = "Waiting for the sender to scan"
                )
            }
            .addOnFailureListener {
                rejectAuthentication(it.message ?: "Could not approve connection")
            }
    }

    private fun scanAndAcceptAuthentication(scannedQr: String) {
        if (role != NearbyRole.SEND) return
        val id = sessionRegistry.pendingAuthEndpointId ?: return
        val token = sessionRegistry.authenticationToken() ?: return
        if (NearbyDeadlinePolicy.isExpired(
                System.currentTimeMillis(), sessionRegistry.authenticationExpiresAtMillis
            )) {
            rejectAuthentication("Verification QR expired")
            return
        }
        if (!NearbyQrAuthentication.matches(token, scannedQr)) {
            publish(
                NearbyPhase.AUTH_SCAN_REQUIRED,
                authQr = "",
                authEndsAt = sessionRegistry.authenticationExpiresAtMillis,
                message = "This QR belongs to another or expired connection. Scan again."
            )
            return
        }
        connections.acceptConnection(id, payloadCallback)
            .addOnSuccessListener {
                publish(
                    NearbyPhase.CONNECTED,
                    authQr = "",
                    authEndsAt = sessionRegistry.authenticationExpiresAtMillis,
                    message = "QR verified. Establishing secure connection…"
                )
            }
            .addOnFailureListener {
                rejectAuthentication(it.message ?: "Could not accept connection")
            }
    }

    private fun rejectAuthentication(reason: String) {
        val id = sessionRegistry.pendingAuthEndpointId
        if (id != null) connections.rejectConnection(id)
        clearPendingAuthentication()
        failSession(reason, operationId.isNotEmpty())
    }

    private fun clearPendingAuthentication() {
        handler.removeCallbacks(authenticationTimeout)
        sessionRegistry.clearAuthentication()
        snapshot = snapshot.copy(
            authenticationQr = "",
            authenticationExpiresAtMillis = 0
        )
    }

    private val payloadProcessor by lazy {
        NearbyPayloadProcessor(
            store = payloadStore,
            cancelPayload = connections::cancelPayload,
            handleControl = ::handleControl,
            consumeIncoming = ::maybeConsumeStream,
            outgoingProgress = { item ->
                publish(
                    NearbyPhase.TRANSFERRING,
                    transferred = durableTransferredBytes(),
                    total = offer?.totalBytes ?: 0,
                    message = item.relativePath
                )
            },
            correlationChanged = ::persistPayloadCheckpoints,
            fail = ::failSession,
            cancel = ::cancel
        )
    }
    private val payloadCallback: PayloadCallback
        get() = payloadProcessor.callback

    private val transportAdapter by lazy {
        NearbyTransportAdapter(object : NearbyTransportAdapter.Events {
            override fun endpointFound(id: String, info: DiscoveredEndpointInfo) =
                onEndpointFound(id, info)

            override fun endpointLost(id: String) = onEndpointLost(id)

            override fun connectionInitiated(id: String, info: ConnectionInfo) =
                onConnectionInitiated(id, info)

            override fun connectionResult(id: String, resolution: ConnectionResolution) =
                onConnectionResult(id, resolution)

            override fun disconnected(id: String) = onDisconnected(id)
        })
    }

    private fun handleControl(message: NearbyMessage) {
        if (role == NearbyRole.RECEIVE && sessionId.startsWith("pending-")) {
            sessionId = message.sessionId
        }
        if (!sessionId.startsWith("pending-") && message.sessionId != sessionId) return cancel("Session mismatch")
        when (message.type) {
            "HELLO" -> {
                require(message.body.getInt("protocol") == NEARBY_PROTOCOL_VERSION)
                require(NearbyRole.valueOf(message.body.getString("role")) != role)
                if (role == NearbyRole.RECEIVE) {
                    sendControl(NearbyProtocol.hello(sessionId, NearbyRole.RECEIVE, resumeOperationId.isNotEmpty()))
                }
            }
            "OFFER" -> onOffer(NearbyProtocol.decodeOffer(message))
            "OFFER_ACCEPTED" -> onOfferAccepted(message.body)
            "OFFER_REJECTED" -> failSession(message.body.optString("reason", "Offer declined"))
            "FILE_BEGIN" -> {
                val metadata = NearbyIncomingStream(
                    message.sessionId,
                    message.body.getString("itemId"),
                    message.body.getLong("payloadId"),
                    message.body.getLong("offset"),
                    message.body.getLong("length")
                )
                require(role == NearbyRole.RECEIVE)
                require(metadata.sessionId == sessionId)
                require(metadata.offset >= 0 && metadata.length >= 0)
                payloadStore.acceptMetadata(metadata)
                persistPayloadCheckpoints()
                maybeConsumeStream(metadata.payloadId)
            }
            "PROGRESS_ACK" -> acknowledge(
                message.body.getString("itemId"),
                message.body.getLong("offset")
            )
            "FILE_COMPLETE" -> completeSentItem(message.body.getString("itemId"))
            "PAUSE" -> remotePause()
            "RESUME_REQUEST" -> {
                recovery.markRunning(operationId)
                publish(NearbyPhase.TRANSFERRING, message = "Resuming transfer…")
                if (role == NearbyRole.SEND) sendNext()
            }
            "CANCEL" -> cancel("Cancelled by the other device", notifyPeer = false)
            "SESSION_COMPLETE" -> {
                val complete = TransferRepository.items(operationId).all {
                    it.state in setOf(TransferItemState.COPIED, TransferItemState.SKIPPED)
                }
                if (complete) {
                    sendControl(NearbyProtocol.command("SESSION_COMPLETE_ACK", sessionId))
                    finishSession()
                } else {
                    failSession("The sender ended before every item was received", true)
                }
            }
            "SESSION_COMPLETE_ACK" -> finishSession()
        }
    }

    private fun onOffer(incoming: NearbyOffer) {
        require(role == NearbyRole.RECEIVE) { "Only a receiver may accept an offer" }
        sessionId = incoming.sessionId
        offer = incoming
        val saved = recovery.findReceive(resumeOperationId, incoming.sessionId)
        if (saved != null && resumeOperationId == saved.operationId) {
            operationId = saved.operationId
            conflictPolicy = saved.conflictPolicy
            sendControl(
                NearbyProtocol.offerAccepted(
                    sessionId,
                    saved.destinationUri,
                    conflictPolicy,
                    NearbyTransferPlanner.offsets(operationId)
                )
            )
            recovery.markRunning(operationId)
            acquireSlotAndPublish()
        } else {
            publish(
                NearbyPhase.OFFER_PENDING,
                offered = incoming,
                message = "${incoming.entries.size} items • ${incoming.totalBytes} bytes"
            )
            handler.postDelayed({
                if (snapshot.phase == NearbyPhase.OFFER_PENDING) rejectOffer("Offer expired")
            }, NEARBY_OFFER_MILLIS)
        }
    }

    private fun acceptOffer(destinationUri: String, policy: NearbyConflictPolicy) {
        val currentOffer = offer ?: return
        conflictPolicy = policy
        worker.execute {
            runCatching {
                operationId = NearbyTransferPlanner.beginReceive(currentOffer, destinationUri, policy)
                store.save(snapshotForStore(destinationUri))
                NearbyTransferPlanner.offsets(operationId)
            }.onSuccess { offsets -> handler.post {
                sendControl(NearbyProtocol.offerAccepted(sessionId, destinationUri, policy, offsets))
                acquireSlotAndPublish()
            }}.onFailure { handler.post { failSession(it.message ?: "Could not prepare destination") } }
        }
    }

    private fun rejectOffer(reason: String) {
        sendControl(NearbyProtocol.rejected(sessionId, reason))
        cancel(reason, notifyPeer = false)
    }

    private fun onOfferAccepted(body: JSONObject) {
        require(role == NearbyRole.SEND)
        conflictPolicy = NearbyConflictPolicy.valueOf(body.getString("conflictPolicy"))
        remoteOffsets.clear()
        val offsets = body.getJSONObject("offsets")
        offsets.keys().forEach { key -> remoteOffsets[key] = offsets.getLong(key).coerceAtLeast(0) }
        TransferRepository.items(operationId).filter { it.isDirectory }.forEach {
            if (it.state != TransferItemState.COPIED) TransferDatabase.completeItem(it.id, "nearby://$peerName/${NearbyTransferPlanner.remoteId(it)}")
        }
        store.save(snapshotForStore(body.optString("destination")))
        acquireSlotAndPublish { sendNext() }
    }

    private fun acquireSlotAndPublish(after: () -> Unit = {}) {
        recovery.markRunning(operationId)
        worker.execute {
            if (!slotHeld) {
                LongRunningOperationLimiter.acquire()
                slotHeld = true
            }
            handler.post {
                val transitioned = if (domainSession.state == com.wisso.wizefiles.storage.NearbySessionState.CONNECTED) {
                    domainSession.transferStarted()
                } else if (domainSession.state != com.wisso.wizefiles.storage.NearbySessionState.TRANSFERRING) {
                    domainSession.resumeTransfer()
                } else true
                if (!transitioned) {
                    releaseSlot()
                    failSession("Nearby callbacks arrived out of order")
                    return@post
                }
                publish(NearbyPhase.TRANSFERRING, transferred = durableTransferredBytes(), total = offer?.totalBytes ?: 0)
                after()
            }
        }
    }

    private fun sendNext() {
        if (role != NearbyRole.SEND || activePayloadId != null || snapshot.phase == NearbyPhase.PAUSED) return
        val item = TransferRepository.items(operationId).firstOrNull {
            !it.isDirectory && it.state !in setOf(TransferItemState.COPIED, TransferItemState.SKIPPED)
        } ?: run {
            sendControl(NearbyProtocol.command("SESSION_COMPLETE", sessionId))
            publish(NearbyPhase.CONNECTED, message = "Finalizing transfer…")
            return
        }
        val remoteId = NearbyTransferPlanner.remoteId(item)
        val offset = max(item.bytesCompleted, remoteOffsets[remoteId] ?: 0).coerceAtMost(item.sizeBytes)
        if (offset == item.sizeBytes) {
            TransferDatabase.completeItem(item.id, "nearby://$peerName/$remoteId")
            sendNext()
            return
        }
        worker.execute {
            runCatching {
                val source = requireNotNull(SyncPathResolver.resolve(item.sourceUri)) { "Source vanished" }
                require(Files.exists(source, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(source)) {
                    "Source changed or vanished"
                }
                require(Files.size(source) == item.sizeBytes &&
                    Files.getLastModifiedTime(source, LinkOption.NOFOLLOW_LINKS).toMillis() == item.modifiedMillis
                ) { "Source changed after the offer was accepted" }
                val input = Files.newInputStream(source)
                skipNearbyInputFully(input, offset)
                val payload = Payload.fromStream(input)
                payloadStore.trackOutgoing(payload, input, item)
                persistPayloadCheckpoints()
                sendControl(NearbyProtocol.fileBegin(sessionId, remoteId, payload.id, offset, item.sizeBytes - offset))
                connections.sendPayload(requireNotNull(endpointId), payload)
                    .addOnFailureListener { failSession(it.message ?: "Could not send file", true) }
            }.onFailure { handler.post { failSession(it.message ?: "Could not open source", true) } }
        }
    }

    private fun acknowledge(itemId: String, offset: Long) {
        val item = TransferRepository.items(operationId).firstOrNull {
            NearbyTransferPlanner.remoteId(it) == itemId
        } ?: return
        require(offset in 0..item.sizeBytes) { "Invalid resume offset" }
        remoteOffsets[itemId] = offset
        val operationBytes = durableTransferredBytes(item.id, offset)
        val speed = TransferProgress.tracker.sample(
            operationId,
            operationBytes,
            offer?.totalBytes ?: 0
        )
        TransferDatabase.checkpoint(
            TransferProgressCheckpoint(
                operationId,
                item.id,
                offset,
                operationBytes,
                item.relativePath,
                speed.bytesPerSecond,
                speed.etaSeconds
            )
        )
        publish(
            NearbyPhase.TRANSFERRING,
            transferred = durableTransferredBytes(),
            total = offer?.totalBytes ?: 0,
            message = item.relativePath
        )
    }

    private fun completeSentItem(itemId: String) {
        val item = TransferRepository.items(operationId).firstOrNull {
            NearbyTransferPlanner.remoteId(it) == itemId
        } ?: return
        activePayloadId?.let(payloadStore::finishOutgoing)
        persistPayloadCheckpoints()
        TransferDatabase.completeItem(item.id, "nearby://$peerName/$itemId")
        sendNext()
    }

    private fun maybeConsumeStream(payloadId: Long) {
        val (payload, metadata) = payloadStore.claimIncoming(payloadId) ?: return
        worker.execute { incomingWriter.receive(payload,metadata) }
    }

    private fun pause() {
        if (operationId.isEmpty()) return
        pauseRequested = true
        sendControl(NearbyProtocol.command("PAUSE", sessionId))
        payloadStore.clearActive()?.let(connections::cancelPayload)
        runCatching { TransferRepository.transition(operationId, TransferOperationState.PAUSE_REQUESTED) }
        if (!domainSession.pause()) return
        persistDomainState("Transfer paused")
        publishDomainState("Transfer paused")
    }

    private fun remotePause() {
        pauseRequested = true
        payloadStore.clearActive()?.let(connections::cancelPayload)
        runCatching { TransferRepository.transition(operationId, TransferOperationState.PAUSE_REQUESTED) }
        if (!domainSession.pause()) return
        persistDomainState("Paused by the other device")
        publishDomainState("Paused by the other device")
    }

    private fun cancel(reason: String, notifyPeer: Boolean = true) {
        if (!domainSession.cancelled()) return
        if (notifyPeer && endpointId != null && sessionId.isNotEmpty()) {
            sendControl(NearbyProtocol.command("CANCEL", sessionId))
        }
        persistDomainState(reason)
        operationId.takeIf(String::isNotEmpty)?.let(store::remove)
        publishDomainState(reason)
        stopSelf()
    }

    private fun finishSession() {
        if (!domainSession.completed()) {
            failSession("Nearby completion callback arrived out of order")
            return
        }
        persistDomainState()
        operationId.takeIf(String::isNotEmpty)?.let(store::remove)
        releaseSlot()
        publish(
            NearbyPhase.COMPLETED,
            transferred = offer?.totalBytes ?: durableTransferredBytes(),
            total = offer?.totalBytes ?: durableTransferredBytes(),
            message = "Nearby transfer completed"
        )
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            foregroundNotifier.notification("Nearby transfer completed", ongoing = false)
        )
        handler.postDelayed(::stopSelf, 2_000)
    }

    private fun failSession(message: String, recoverable: Boolean = false) {
        if (!domainSession.failed(recoverable)) return
        persistDomainState(message, "NEARBY_CONNECTION")
        releaseSlot()
        publishDomainState(message)
        stopSelf()
    }

    private fun persistDomainState(reason: String? = null, errorCategory: String? = null) {
        val operationState = domainSession.projection().operationState ?: return
        if (operationId.isEmpty()) return
        runCatching {
            TransferRepository.transition(
                operationId,
                operationState,
                reason = reason.orEmpty(),
                errorCategory = errorCategory.orEmpty(),
                errorMessage = reason.orEmpty()
            )
        }
    }

    private fun publishDomainState(message: String) {
        publish(domainSession.projection().phase, message = message)
    }

    private fun sendControl(bytes: ByteArray) {
        val id = endpointId ?: return
        connections.sendPayload(id, Payload.fromBytes(bytes))
            .addOnFailureListener { failSession(it.message ?: "Connection failed", true) }
    }

    private fun durableTransferredBytes(replaceItemId: Long = -1, replacement: Long = -1): Long =
        if (operationId.isEmpty()) 0 else TransferRepository.items(operationId).sumOf {
            if (it.id == replaceItemId) replacement.coerceAtLeast(0) else it.bytesCompleted.coerceAtLeast(0)
        }

    private fun snapshotForStore(destinationUri: String = "") = NearbySessionSnapshot(
        operationId,
        sessionId,
        requireNotNull(role),
        peerName,
        destinationUri,
        conflictPolicy,
        payloadStore.checkpoints()
    )

    private fun persistPayloadCheckpoints() {
        if (operationId.isNotEmpty() && role != null) store.save(snapshotForStore())
    }

    private fun requireReady() {
        require(NearbyPermissions.granted(this)) { "Nearby permissions are required" }
        require(NearbyPermissions.playServicesAvailable(this)) {
            "Nearby Transfer requires Google Play services"
        }
    }

    private fun publish(
        phase: NearbyPhase,
        endpoints: List<NearbyEndpoint> = sessionRegistry.endpoints(),
        authQr: String = snapshot.authenticationQr,
        authEndsAt: Long = snapshot.authenticationExpiresAtMillis,
        offered: NearbyOffer? = offer,
        message: String = snapshot.message,
        endsAt: Long = snapshot.visibilityEndsAtMillis,
        transferred: Long = snapshot.transferredBytes,
        total: Long = snapshot.totalBytes
    ) {
        snapshot = NearbyUiSnapshot(
            phase = phase,
            role = role,
            operationId = operationId,
            peerName = peerName,
            authenticationQr = authQr,
            authenticationExpiresAtMillis = authEndsAt,
            endpoints = endpoints,
            offer = offered,
            message = message,
            visibilityEndsAtMillis = endsAt,
            transferredBytes = transferred,
            totalBytes = total
        )
        if (phase !in setOf(NearbyPhase.IDLE, NearbyPhase.COMPLETED, NearbyPhase.CANCELLED, NearbyPhase.ERROR)) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, foregroundNotifier.notification(message))
        }
    }

    private fun resetTransport() {
        connections.stopAdvertising()
        connections.stopDiscovery()
        connections.stopAllEndpoints()
        handler.removeCallbacks(discoveryTimeout)
        sessionRegistry.reset()
        payloadStore.close()
        clearPendingAuthentication()
        peerName = ""
        operationId = ""
        offer = null
        resumeOperationId = ""
        remoteOffsets.clear()
        pauseRequested = false
    }

    override fun onDestroy() {
        handler.removeCallbacks(discoveryTimeout)
        clearPendingAuthentication()
        connections.stopAdvertising()
        connections.stopDiscovery()
        connections.stopAllEndpoints()
        payloadStore.close()
        worker.shutdownNow()
        releaseSlot()
        instance = null
        if (snapshot.phase !in setOf(NearbyPhase.COMPLETED, NearbyPhase.ERROR, NearbyPhase.RECOVERABLE)) {
            snapshot = NearbyUiSnapshot()
        }
        super.onDestroy()
    }

    private fun releaseSlot() {
        if (slotHeld) {
            slotHeld = false
            LongRunningOperationLimiter.release()
        }
    }

    private fun startAsForeground(text: String) {
        startForeground(
            NOTIFICATION_ID,
            foregroundNotifier.notification(text),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )
    }

    companion object {
        private const val CHANNEL_ID = "nearby_transfer"
        private const val NOTIFICATION_ID = 0x574E
        private const val AUTHENTICATION_MILLIS = 60_000L
        private const val MAX_PENDING_INCOMING_PAYLOADS = 2
        const val ACTION_SEND = "com.wisso.wizefiles.nearby.SEND"
        const val ACTION_RECEIVE = "com.wisso.wizefiles.nearby.RECEIVE"
        const val ACTION_CONNECT = "com.wisso.wizefiles.nearby.CONNECT"
        const val ACTION_AUTH_APPROVE = "com.wisso.wizefiles.nearby.AUTH_APPROVE"
        const val ACTION_AUTH_SCAN = "com.wisso.wizefiles.nearby.AUTH_SCAN"
        const val ACTION_AUTH_REJECT = "com.wisso.wizefiles.nearby.AUTH_REJECT"
        const val ACTION_ACCEPT_OFFER = "com.wisso.wizefiles.nearby.ACCEPT_OFFER"
        const val ACTION_REJECT_OFFER = "com.wisso.wizefiles.nearby.REJECT_OFFER"
        const val ACTION_PAUSE = "com.wisso.wizefiles.nearby.PAUSE"
        const val ACTION_RESUME = "com.wisso.wizefiles.nearby.RESUME"
        const val ACTION_CANCEL = "com.wisso.wizefiles.nearby.CANCEL"
        const val ACTION_STOP = "com.wisso.wizefiles.nearby.STOP"
        const val EXTRA_SOURCE_URIS = "source_uris"
        const val EXTRA_ENDPOINT_ID = "endpoint_id"
        const val EXTRA_AUTH_QR = "auth_qr"
        const val EXTRA_DESTINATION_URI = "destination_uri"
        const val EXTRA_CONFLICT_POLICY = "conflict_policy"
        const val EXTRA_OPERATION_ID = "operation_id"

        @Volatile internal var snapshot = NearbyUiSnapshot()
            private set
        @Volatile private var instance: NearbyTransferService? = null

        fun command(context: Context, action: String, configure: Intent.() -> Unit = {}) {
            val intent = Intent(context, NearbyTransferService::class.java).setAction(action).apply(configure)
            if (action in setOf(ACTION_SEND, ACTION_RECEIVE, ACTION_RESUME)) {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun handles(operationType: TransferOperationType): Boolean = operationType in setOf(
            TransferOperationType.NEARBY_SEND,
            TransferOperationType.NEARBY_RECEIVE
        )

        fun pauseOperation(context: Context, operationId: String) {
            if (instance?.operationId == operationId) command(context, ACTION_PAUSE)
        }

        fun cancelOperation(context: Context, operationId: String) {
            if (instance?.operationId == operationId) {
                command(context, ACTION_CANCEL)
            } else {
                runCatching {
                    TransferRepository.transition(
                        operationId,
                        TransferOperationState.CANCELLED,
                        reason = "Cancelled"
                    )
                }
                NearbySessionStore(context).remove(operationId)
            }
        }
    }
}
