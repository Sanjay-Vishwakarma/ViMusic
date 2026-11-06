package it.vfsfitvnm.vimusic.local

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.DateUtils
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Songs stored on the device, read from Android's MediaStore and grouped by
 * folder. A local song's id everywhere in the app (queue, favourites,
 * history) is "local:<MediaStore id>"; PlayerService recognises the prefix
 * and plays the file directly instead of resolving a YouTube stream.
 */
object LocalMusic {
    const val PREFIX = "local:"

    fun isLocal(mediaId: String?) = mediaId?.startsWith(PREFIX) == true

    /** content:// URI of the audio file behind a "local:<id>" media id. */
    fun contentUri(mediaId: String): Uri? =
        mediaId.removePrefix(PREFIX).toLongOrNull()
            ?.let { ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it) }

    val permission: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** All music on the device, grouped by folder (largest folders first). */
    suspend fun loadFolders(context: Context): List<LocalFolder> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<LocalSong>()

        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            @Suppress("DEPRECATION")
            add(MediaStore.Audio.Media.DATA)
            if (Build.VERSION.SDK_INT >= 29) add(MediaStore.Audio.Media.RELATIVE_PATH)
        }.toTypedArray()

        // Music only (not ringtones/notifications/voice notes), at least 30 s long.
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 30000"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            @Suppress("DEPRECATION")
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val relCol = if (Build.VERSION.SDK_INT >= 29) cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH) else -1

            while (cursor.moveToNext()) {
                val path = when {
                    relCol >= 0 && !cursor.isNull(relCol) -> cursor.getString(relCol).trimEnd('/')
                    dataCol >= 0 && !cursor.isNull(dataCol) -> File(cursor.getString(dataCol)).parent.orEmpty()
                    else -> ""
                }.ifEmpty { "Other" }

                val artist = cursor.getString(artistCol)?.takeUnless { it.isBlank() || it == MediaStore.UNKNOWN_STRING }

                songs += LocalSong(
                    id = cursor.getLong(idCol),
                    title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() }
                        ?: cursor.getString(nameCol)?.substringBeforeLast('.')
                        ?: "Unknown",
                    artist = artist,
                    album = cursor.getString(albumCol),
                    albumId = cursor.getLong(albumIdCol),
                    durationMs = cursor.getLong(durationCol),
                    folderPath = path,
                )
            }
        }

        songs
            .groupBy { it.folderPath }
            .map { (path, folderSongs) ->
                LocalFolder(
                    path = path,
                    name = path.substringAfterLast('/').ifEmpty { path },
                    songs = folderSongs,
                )
            }
            .sortedWith(compareByDescending<LocalFolder> { it.songs.size }.thenBy { it.name.lowercase() })
    }
}

@Immutable
data class LocalSong(
    val id: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumId: Long,
    val durationMs: Long,
    val folderPath: String,
) {
    val mediaId: String get() = LocalMusic.PREFIX + id

    val durationText: String
        get() = DateUtils.formatElapsedTime(durationMs / 1000).removePrefix("0")

    /** Cover art embedded in the file, loaded by [LocalArtworkFetcher]. */
    val artworkUri: Uri
        get() = LocalArtworkFetcher.uriFor(id)

    val asMediaItem: MediaItem
        get() = MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id))
            .setCustomCacheKey(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(artworkUri)
                    // Only durationText: no albumId/artistIds, so local songs
                    // never create YouTube album/artist rows in the database.
                    .setExtras(bundleOf("durationText" to durationText))
                    .build()
            )
            .build()
}

@Immutable
data class LocalFolder(
    /** "Music/Bollywood" (Android 10+) or the absolute parent directory. */
    val path: String,
    val name: String,
    val songs: List<LocalSong>,
)
