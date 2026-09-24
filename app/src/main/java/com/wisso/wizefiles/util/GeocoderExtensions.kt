package com.wisso.wizefiles.util

import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

val isGeocoderPresent by lazy { Geocoder.isPresent() }

@Throws(IOException::class)
suspend fun Geocoder.awaitGetFromLocation(
    latitude: Double,
    longitude: Double,
    maxResults: Int
): List<Address> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            getFromLocation(latitude, longitude, maxResults)
                ?: throw IOException(NullPointerException())
        }
    }
    return suspendCancellableCoroutine { continuation ->
        getFromLocation(latitude, longitude, maxResults, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) {
                continuation.resume(addresses)
            }

            override fun onError(errorMessage: String?) {
                continuation.resumeWithException(IOException(errorMessage))
            }
        })
    }
}
