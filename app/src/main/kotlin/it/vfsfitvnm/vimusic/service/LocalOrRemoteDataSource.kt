package it.vfsfitvnm.vimusic.service

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Routes each open() by URI scheme: device files (content://, file://) go to
 * [local] — a plain DefaultDataSource, so they are not copied into the stream
 * cache — and everything else to [remote], the YouTube cache/HTTP chain.
 */
@UnstableApi
class LocalOrRemoteDataSource(
    private val local: DataSource,
    private val remote: DataSource,
) : DataSource {
    private var current: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        local.addTransferListener(transferListener)
        remote.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = if (isLocal(dataSpec.uri)) local else remote
        current = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(current) { "read() before open()" }.read(buffer, offset, length)

    override fun getUri(): Uri? = current?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            current?.close()
        } finally {
            current = null
        }
    }

    private fun isLocal(uri: Uri) = when (uri.scheme) {
        "content", "file", "android.resource" -> true
        else -> false
    }
}
