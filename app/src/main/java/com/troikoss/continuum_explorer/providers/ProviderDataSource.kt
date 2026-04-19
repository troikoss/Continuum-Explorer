package com.troikoss.continuum_explorer.providers

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.troikoss.continuum_explorer.model.UniversalFile
import java.io.InputStream

@UnstableApi
class ProviderDataSource @OptIn(UnstableApi::class) constructor
    (
    private val file: UniversalFile,
) : BaseDataSource(file.provider.capabilities.isRemote) {

    private var stream: InputStream? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var uri: Uri = Uri.parse(file.providerId)

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        stream = when {
            dataSpec.position > 0 && file.provider is WebDavProvider ->
                file.provider.openRangeInput(file.providerId, dataSpec.position)
            dataSpec.position > 0 && file.provider is FtpProvider ->
                file.provider.openRangeInput(file.providerId, dataSpec.position)
            dataSpec.position > 0 ->
                file.provider.openInput(file.providerId).apply { skip(dataSpec.position) }
            else ->
                file.provider.openInput(file.providerId)
        }
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length
        else if (file.length > 0) file.length - dataSpec.position
        else C.LENGTH_UNSET.toLong()
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val toRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) length
        else minOf(length.toLong(), bytesRemaining).toInt()
        val read = stream!!.read(buffer, offset, toRead)
        if (read == -1) return C.RESULT_END_OF_INPUT
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri = uri

    override fun close() {
        stream?.runCatching { close() }
        stream = null
        transferEnded()
    }

    class Factory(private val file: UniversalFile) : DataSource.Factory {
        override fun createDataSource(): DataSource = ProviderDataSource(file)
    }
}
