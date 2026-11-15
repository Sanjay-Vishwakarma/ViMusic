package it.sjmusic.vimusic

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import it.sjmusic.vimusic.enums.CoilDiskCacheMaxSize
import it.sjmusic.vimusic.utils.coilDiskCacheMaxSizeKey
import it.sjmusic.vimusic.utils.getEnum
import it.sjmusic.vimusic.utils.preferences

class MainApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        DatabaseInitializer()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            // Cover art embedded in songs stored on the phone ("localart://<id>").
            .components { add(it.sjmusic.vimusic.local.LocalArtworkFetcher.Factory()) }
            .crossfade(true)
            .respectCacheHeaders(false)
            .diskCache(
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil"))
                    .maxSizeBytes(
                        preferences.getEnum(
                            coilDiskCacheMaxSizeKey,
                            CoilDiskCacheMaxSize.`128MB`
                        ).bytes
                    )
                    .build()
            )
            .build()
    }
}
