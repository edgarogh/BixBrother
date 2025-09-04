package bzh.edgar.bixbrother

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@Serializable
data class StationResponse(
    @SerialName("updated_at") val updatedAt: Long,
    val stations: List<StationInfo>
)

@Serializable
data class StationInfo @OptIn(ExperimentalUuidApi::class) constructor(
    val n: String,
    val i: Uuid,
    val lat: Float,
    val lon: Float,
    val b: Int,
    val e: Int,
    val a: Int,
    val c: Int,
)

class BixClient(private val remoteConfig: FirebaseRemoteConfig) {
    private suspend fun getBackendUrl(): String {
        remoteConfig.fetchAndActivate().await()
        return remoteConfig.getString("backend_url")
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalUuidApi::class)
    suspend fun getStationInfos(): List<Station> {
        return withContext(Dispatchers.IO) {
            val url = URL(getBackendUrl() + "v1/statuses")
            val connection = url.openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.connect()
                val response = Json.decodeFromStream<StationResponse>(connection.inputStream)
                return@withContext response.stations.map {
                    Station(
                        it.i.toJavaUuid(),
                        false,
                        it.n,
                        it.lat,
                        it.lon,
                        it.b,
                        it.e,
                        it.a,
                        it.c,
                        response.updatedAt,
                    )
                }
            } finally {
                connection.inputStream.close()
                connection.disconnect()
            }
        }
    }

    suspend fun getStationThumbnail(stationId: UUID): String {
        return getBackendUrl() + "v1/stations/$stationId/map_thumbnail128.png"
    }
}
