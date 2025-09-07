package bzh.edgar.bixbrother

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

val Context.bixApp get() = this.applicationContext as BixApplication

class BixApplication : Application() {
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
}
