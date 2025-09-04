package bzh.edgar.bixbrother

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import java.util.UUID

@Entity(tableName = "stations")
data class Station(
    @ColumnInfo(name = "external_id") @PrimaryKey val externalId: UUID,
    val deleted: Boolean = false,
    @ColumnInfo(index = true) val name: String,
    val lat: Float,
    val lon: Float,
    @ColumnInfo(name = "num_bikes_available") val numBikesAvailable: Int,
    @ColumnInfo(name = "num_ebikes_available") val numEbikesAvailable: Int,
    @ColumnInfo(name = "num_docks_available") val numDocksAvailable: Int,
    @ColumnInfo(name = "capacity") val capacity: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "widget_station")
data class WidgetStation(
    @PrimaryKey(autoGenerate = true) val autoId: Int? = null,
    @ColumnInfo(name = "widget_id") val widgetId: Int,
    val station: UUID,
    val title: String?,
    val position: Int,
)

@Dao
interface BixDao {
    @Upsert
    suspend fun tmpUpsertStations(stations: List<Station>)

    @Query("update stations set num_bikes_available = :bikes, num_ebikes_available = :ebikes, num_docks_available = :docks where external_id = :stationId and updated_at < :updatedAt")
    fun updateStationStatus(stationId: UUID, bikes: Int, ebikes: Int, docks: Int, updatedAt: Long)

    @Query("select * from stations where name like :searchTerms limit :limit")
    suspend fun stationsByName(searchTerms: String, limit: Int = 20): List<Station>

    @Query("select * from stations where external_id = :stationId")
    suspend fun stationByUuid(stationId: UUID): Station?

    @Query("select * from stations order by capacity desc, name limit :limit")
    suspend fun firstStations(limit: Int = 20): List<Station>

    @Query("update widget_station set widget_id = :widgetId where widget_id = 0")
    suspend fun persistWidget(widgetId: Int)

    @Query("delete from widget_station where widget_id = :widgetId")
    suspend fun deleteWidget(widgetId: Int)

    @Insert
    suspend fun insertWidgetStations(stations: List<WidgetStation>)

    @Query("select distinct s.* from widget_station w left join stations s on s.external_id = w.station where w.widget_id <> 0")
    suspend fun uniqueActiveStations(): List<Station>

    @Transaction
    suspend fun updateWidget(widgetId: Int, stations: List<UUID>): Pair<Set<Station>, Set<Station>> {
        val before = uniqueActiveStations()
        deleteWidget(widgetId)
        insertWidgetStations(stations.mapIndexed { idx, id ->
            WidgetStation(
                widgetId = widgetId,
                station = id,
                title = null,
                position = idx,
            )
        })
        val after = uniqueActiveStations()
        val added = after subtract before
        val deleted = before subtract after
        return deleted to added
    }

    @Query("select s.* from widget_station w left join stations s on s.external_id = w.station where widget_id = :widgetId order by w.position")
    suspend fun getWidgetStations(widgetId: Int): List<Station>
    @Query("select s.* from widget_station w left join stations s on s.external_id = w.station where widget_id = :widgetId order by w.position")
    fun getWidgetStationsSync(widgetId: Int): List<Station>

    @Query("select widget_id from widget_station where (station = :stationId) and (widget_id <> 0)")
    fun getStationWidgetsSync(stationId: UUID): List<Int>
}

@Database(entities = [Station::class, WidgetStation::class], version = 1, exportSchema = false)
abstract class BixDatabase : RoomDatabase() {
    abstract fun dao(): BixDao
}
