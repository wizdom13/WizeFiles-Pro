package com.wisso.wizefiles.core.billing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

internal class HttpsLicenseBackendClient(
    private val baseUrl: String,
) : LicenseBackendClient {
    override suspend fun verify(request: BackendLicenseRequest): BackendLicenseResult =
        withContext(Dispatchers.IO) {
            if (baseUrl.isBlank()) {
                return@withContext BackendLicenseResult.Failed(BillingError.BACKEND_UNAVAILABLE)
            }
            runCatching { execute(request) }.getOrElse {
                BackendLicenseResult.Failed(BillingError.BACKEND_UNAVAILABLE)
            }
        }

    private fun execute(request: BackendLicenseRequest): BackendLicenseResult {
        val connection = URL("$baseUrl$VERIFY_PATH").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val body = JSONObject()
                .put("schema_version", REQUEST_SCHEMA_VERSION)
                .put("store", "google_play")
                .put("package_name", request.packageName)
                .put("installation_id", request.installationId)
                .put("product_id", request.productId)
                .put("product_type", request.productType.wireValue())
                .put("purchase_token", request.purchaseToken)
                .put("app_version_code", request.appVersionCode)
                .toString()
                .toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            val responseCode = connection.responseCode
            val responseBody = readLimited(
                if (responseCode in 200..299) connection.inputStream else connection.errorStream,
            )
            return when (responseCode) {
                HttpURLConnection.HTTP_OK -> parseSuccess(responseBody)
                HttpURLConnection.HTTP_BAD_REQUEST,
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN,
                HttpURLConnection.HTTP_CONFLICT ->
                    BackendLicenseResult.Failed(BillingError.VERIFICATION_FAILED)
                else -> BackendLicenseResult.Failed(BillingError.BACKEND_UNAVAILABLE)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSuccess(responseBody: String): BackendLicenseResult = runCatching {
        val document = JSONObject(responseBody).getString("license_document")
        require(document.isNotBlank() && document.length <= MAX_LICENSE_DOCUMENT_CHARS)
        BackendLicenseResult.SignedDocument(document)
    }.getOrElse {
        BackendLicenseResult.Failed(BillingError.VERIFICATION_FAILED)
    }

    private fun readLimited(input: InputStream?): String {
        if (input == null) return ""
        input.use { stream ->
            val bytes = ByteArray(MAX_RESPONSE_BYTES + 1)
            var total = 0
            while (total < bytes.size) {
                val read = stream.read(bytes, total, bytes.size - total)
                if (read < 0) break
                total += read
            }
            require(total <= MAX_RESPONSE_BYTES) { "License response is too large" }
            return bytes.copyOf(total).toString(Charsets.UTF_8)
        }
    }

    private fun BillingProductType.wireValue(): String = when (this) {
        BillingProductType.ONE_TIME -> "one_time"
        BillingProductType.SUBSCRIPTION -> "subscription"
    }

    companion object {
        private const val VERIFY_PATH = "/v1/google-play/licenses:verify"
        private const val REQUEST_SCHEMA_VERSION = 1
        private const val CONNECT_TIMEOUT_MILLIS = 10_000
        private const val READ_TIMEOUT_MILLIS = 15_000
        private const val MAX_RESPONSE_BYTES = 64 * 1024
        private const val MAX_LICENSE_DOCUMENT_CHARS = 16 * 1024
    }
}

