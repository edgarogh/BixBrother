package bzh.edgar.bixbrother

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.net.toUri
import kotlin.random.Random

private const val BIXI_PACKAGE_ID = "com.eightd.biximobile"

object BixWidgetLayout {
    fun inflateWidget(context: Context, appWidgetId: Int) = RemoteViews(
        context.packageName,
        R.layout.bix_widget,
    ).apply {
        val adapter = Intent(context, BixWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = toUri(Intent.URI_INTENT_SCHEME).toUri()
        }

        setRemoteAdapter(R.id.widget_list, adapter)

        setOnClickPendingIntent(
            R.id.widget_unlock_button,
            PendingIntent.getActivity(
                context,
                Random.Default.nextInt(),
                Intent(context, BixWidgetTrampolineActivity::class.java).apply {
                    putExtra(EXTRA_TARGET_PACKAGE, BIXI_PACKAGE_ID)
                },
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }

    fun inflateItem(context: Context, station: StationEntry) = RemoteViews(
        context.packageName,
        R.layout.bix_widget_item
    ).apply {
        setTextViewText(R.id.widget_item_station_name, station.title)
        setTextViewText(R.id.widget_item_num_bikes, station.numRegularBikesAvailable.toString())
        setTextViewText(R.id.widget_item_num_ebikes, station.numEbikesAvailable.toString())
        setTextViewText(R.id.widget_item_num_docks, station.numDocksAvailable.toString())
    }
}
