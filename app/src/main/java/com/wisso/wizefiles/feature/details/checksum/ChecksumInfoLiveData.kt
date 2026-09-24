package com.wisso.wizefiles.feature.details.checksum

import com.wisso.wizefiles.core.fastops.FastFileOps
import com.wisso.wizefiles.feature.details.PathObserverLiveData
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.util.Failure
import com.wisso.wizefiles.util.Loading
import com.wisso.wizefiles.util.Stateful
import com.wisso.wizefiles.util.Success
import com.wisso.wizefiles.util.toHexString
import com.wisso.wizefiles.util.valueCompat
import com.wisso.wizefiles.util.backgroundExecutor
import java.nio.file.Files
import java.util.concurrent.Future

class ChecksumInfoLiveData(path: AppPath) : PathObserverLiveData<Stateful<ChecksumInfo>>(path) {
    private var future: Future<Unit>? = null

    init {
        loadValue()
        observe()
    }

    override fun loadValue() {
        future?.cancel(true)
        value = Loading(value?.value)
        future = backgroundExecutor.submit<Unit> {
            val value = try {
                val legacyPath = path.toLegacyPathOrNull() ?: error("Unsupported path: $path")
                val checksumValues = FastFileOps.computeChecksums(legacyPath) ?: run {
                    val messageDigests =
                        ChecksumInfo.Algorithm.entries.associateWith { it.createMessageDigest() }
                    Files.newInputStream(legacyPath).use { inputStream ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val readSize = inputStream.read(buffer)
                            if (readSize == -1) {
                                break
                            }
                            messageDigests.values.forEach { it.update(buffer, 0, readSize) }
                        }
                    }
                    messageDigests.mapValues { it.value.digest().toHexString() }
                }
                val checksumInfo = ChecksumInfo(checksumValues)
                Success(checksumInfo)
            } catch (e: Exception) {
                Failure(valueCompat.value, e)
            }
            postValue(value)
        }
    }

    override fun close() {
        super.close()

        future?.cancel(true)
    }
}
