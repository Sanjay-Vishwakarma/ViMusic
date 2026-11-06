package it.vfsfitvnm.vimusic.local

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size as AndroidSize
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.Dimension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coil fetcher for "localart://<MediaStore audio id>": the cover art embedded
 * in a song file on the phone.
 *
 * The old "content://media/external/audio/albumart/<id>" URI is not readable
 * on Android 10+, so local songs showed no image. Registered in
 * MainApplication's ImageLoader, it makes the art appear everywhere Coil
 * loads artwork: lists, the player, the notification and the lock screen.
 */
class LocalArtworkFetcher(
    private val uri: Uri,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val id = uri.host?.toLongOrNull() ?: return null
        val sizePx = listOf(options.size.width, options.size.height)
            .mapNotNull { (it as? Dimension.Pixels)?.px }
            .maxOrNull()
            ?.coerceIn(64, 1024)
            ?: 512

        val bitmap = withContext(Dispatchers.IO) { load(options.context, id, sizePx) } ?: return null

        return DrawableResult(
            drawable = BitmapDrawable(options.context.resources, bitmap),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    private fun load(context: Context, id: Long, sizePx: Int): Bitmap? {
        val audioUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

        if (Build.VERSION.SDK_INT >= 29) {
            runCatching {
                return context.contentResolver.loadThumbnail(audioUri, AndroidSize(sizePx, sizePx), null)
            }
        }

        // Older Android, or loadThumbnail found nothing: read the embedded picture.
        return runCatching {
            MediaMetadataRetriever().run {
                try {
                    setDataSource(context, audioUri)
                    embeddedPicture
                } finally {
                    release()
                }
            }
        }.getOrNull()?.let { bytes ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.scheme == SCHEME) LocalArtworkFetcher(data, options) else null
    }

    companion object {
        const val SCHEME = "localart"

        fun uriFor(mediaStoreId: Long): Uri = Uri.parse("$SCHEME://$mediaStoreId")
    }
}
