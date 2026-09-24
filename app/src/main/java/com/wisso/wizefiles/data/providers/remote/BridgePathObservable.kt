// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.remote

import android.os.BadParcelableException
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.os.RemoteException
import com.wisso.wizefiles.provider.common.PathObservable
import com.wisso.wizefiles.util.RemoteCallback
import java.io.IOException

class BridgePathObservable : PathObservable, Parcelable {
    private val local: PathObservable?
    private val remote: IPathWatchBridge?
    private val lock = Any()
    private val observers = linkedSetOf<() -> Unit>()
    private var connected = false
    private var closed = false

    constructor(observable: PathObservable) {
        local = observable
        remote = null
    }

    private constructor(parcel: Parcel) {
        local = null
        remote = IPathWatchBridge.Stub.asInterface(parcel.readStrongBinder())
            ?: throw BadParcelableException("Missing path watch bridge binder")
    }

    @Throws(IOException::class)
    fun connectObserver() {
        synchronized(lock) {
            check(local == null) { "Only a transported observable can connect to a bridge" }
            check(!connected) { "Path observer is already connected" }
            check(!closed) { "Path observer is closed" }
            try {
                checkNotNull(remote).registerObserver(RemoteCallback { publishChange() })
            } catch (failure: RemoteException) {
                runCatching { close() }
                throw BridgeUnavailableException(failure)
            }
            connected = true
        }
    }

    override fun addObserver(observer: () -> Unit) {
        local?.let {
            it.addObserver(observer)
            return
        }
        synchronized(lock) {
            check(connected && !closed) { "Path observer bridge is not active" }
            observers += observer
        }
    }

    override fun removeObserver(observer: () -> Unit) {
        local?.let {
            it.removeObserver(observer)
            return
        }
        synchronized(lock) {
            check(connected && !closed) { "Path observer bridge is not active" }
            observers -= observer
        }
    }

    @Throws(IOException::class)
    override fun close() {
        local?.let {
            it.close()
            return
        }
        synchronized(lock) {
            if (closed) return
            checkNotNull(remote).invokeBridge { failure -> closeWatch(failure) }
            observers.clear()
            closed = true
        }
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        check(local != null) { "A transported path observer cannot be transported again" }
        parcel.writeStrongBinder(LocalPathWatchBridge(local).asBinder())
    }

    private fun publishChange() {
        val snapshot = synchronized(lock) {
            if (closed) emptyList() else observers.toList()
        }
        snapshot.forEach { observer -> observer() }
    }

    private class LocalPathWatchBridge(
        private val observable: PathObservable
    ) : IPathWatchBridge.Stub() {
        override fun registerObserver(observer: RemoteCallback) {
            observable.addObserver { observer.sendResult(Bundle()) }
        }

        override fun closeWatch(failure: BridgeFailure) {
            serveBridge(failure) { observable.close() }
        }
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<BridgePathObservable> =
            object : Parcelable.Creator<BridgePathObservable> {
                override fun createFromParcel(parcel: Parcel) = BridgePathObservable(parcel)
                override fun newArray(size: Int): Array<BridgePathObservable?> = arrayOfNulls(size)
            }
    }
}

fun PathObservable.toBridge(): BridgePathObservable = BridgePathObservable(this)
