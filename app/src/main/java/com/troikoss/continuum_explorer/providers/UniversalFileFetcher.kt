package com.troikoss.continuum_explorer.providers

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.troikoss.continuum_explorer.model.UniversalFile
import okio.buffer
import okio.source

class UniversalFileFetcher(
    private val file: UniversalFile,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult = SourceResult(
        source = ImageSource(
            source = file.provider.openInput(file.providerId).source().buffer(),
            context = options.context,
        ),
        mimeType = file.mimeType,
        dataSource = if (file.provider.capabilities.isRemote) DataSource.NETWORK else DataSource.DISK,
    )

    class Factory : Fetcher.Factory<UniversalFile> {
        override fun create(data: UniversalFile, options: Options, imageLoader: ImageLoader): Fetcher =
            UniversalFileFetcher(data, options)
    }
}
