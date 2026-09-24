package com.wisso.wizefiles.feature.ebook

import java.io.IOException
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.http.HttpClient
import org.readium.r2.shared.util.http.HttpError
import org.readium.r2.shared.util.http.HttpRequest
import org.readium.r2.shared.util.http.HttpStreamResponse
import org.readium.r2.shared.util.http.HttpTry

/** Prevents publication parsing from turning local ebook content into an implicit network request. */
object OfflineHttpClient : HttpClient {
    override suspend fun stream(request: HttpRequest): HttpTry<HttpStreamResponse> =
        Try.failure(HttpError.IO(IOException("Network access is disabled for ebooks")))
}
