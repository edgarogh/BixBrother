package bzh.edgar.bixbrother

import android.app.Application
import android.content.Context
import androidx.room.Room
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

const val EXTRA_INITIAL_CACHED_STATIONS = "bzh.edgar.bixbrother.intent.extra.INITIAL_CACHED_STATIONS"
const val EXTRA_TARGET_PACKAGE = "bzh.edgar.bixbrother.intent.extra.EXTRA_TARGET_PACKAGE"

val Context.bixApp get() = this.applicationContext as BixApplication

class BixApplication : Application(), SingletonImageLoader.Factory {
    val apiClient by lazy {
        BixClient(Firebase.remoteConfig)
    }

    val db by lazy {
        Room.databaseBuilder(
            applicationContext,
            BixDatabase::class.java, "bix-database"
        ).addMigrations(MIGRATION_1_2).build()
    }

    override fun onCreate() {
        super.onCreate()
        Firebase.remoteConfig.setConfigSettingsAsync(remoteConfigSettings { })
    }

    override fun newImageLoader(context: PlatformContext) = ImageLoader.Builder(context)
        .diskCache(
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("osm-thumbnails"))
                .maxSizeBytes(100 * 1024 * 1024)
                .build()
        )
        .build()
}
