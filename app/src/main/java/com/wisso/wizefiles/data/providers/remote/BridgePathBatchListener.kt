package com.wisso.wizefiles.provider.remote

import android.os.BadParcelableException
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import com.wisso.wizefiles.util.ParcelableArgs
import com.wisso.wizefiles.util.ParcelableListParceler
import com.wisso.wizefiles.util.RemoteCallback
import com.wisso.wizefiles.util.getArgs
import com.wisso.wizefiles.util.putArgs
import com.wisso.wizefiles.util.readParcelable
import java.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith

class BridgePathBatchListener(val value: (List<Path>) -> Unit) : Parcelable {
    private constructor(parcel: Parcel) : this(readSender(parcel))

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        val receiver = RemoteCallback { bundle ->
            value(bundle.getArgs<BatchArgs>().paths)
        }
        parcel.writeParcelable(receiver, flags)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<BridgePathBatchListener> =
            object : Parcelable.Creator<BridgePathBatchListener> {
                override fun createFromParcel(parcel: Parcel) = BridgePathBatchListener(parcel)
                override fun newArray(size: Int): Array<BridgePathBatchListener?> = arrayOfNulls(size)
            }

        private fun readSender(parcel: Parcel): (List<Path>) -> Unit {
            val sender = parcel.readParcelable<RemoteCallback>()
                ?: throw BadParcelableException("Missing path batch callback")
            return { paths ->
                sender.sendResult(Bundle().putArgs(BatchArgs(paths)))
            }
        }
    }

    @Parcelize
    private class BatchArgs(
        val paths: @WriteWith<ParcelableListParceler> List<Path>
    ) : ParcelableArgs
}

fun ((List<Path>) -> Unit).toParcelable(): BridgePathBatchListener =
    BridgePathBatchListener(this)
