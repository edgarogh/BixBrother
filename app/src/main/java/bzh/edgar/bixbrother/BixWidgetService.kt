package bzh.edgar.bixbrother

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class BixWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent) = RemoteViewsFactory(intent, this)

    class RemoteViewsFactory(intent: Intent, val context: Context) : RemoteViewsService.RemoteViewsFactory {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        private var stations = listOf<Station>()

        override fun getCount() = stations.size
        override fun getItemId(index: Int) = (index + 1).toLong()
        override fun getLoadingView() = null

        override fun getViewAt(index: Int) = RemoteViews(context.packageName, R.layout.bix_widget_item).apply {
            val station = stations[index]
            setTextViewText(R.id.widget_item_station_name, station.name)
            setTextViewText(R.id.widget_item_num_bikes, (station.numBikesAvailable - station.numEbikesAvailable).toString())
            setTextViewText(R.id.widget_item_num_ebikes, station.numEbikesAvailable.toString())
            setTextViewText(R.id.widget_item_num_docks, station.numDocksAvailable.toString())
        }

        override fun getViewTypeCount() = 1
        override fun hasStableIds() = true

        override fun onCreate() {
        }

        override fun onDataSetChanged() {
            stations = context.bixApp.db.dao().getWidgetStationsSync(widgetId)
        }

        override fun onDestroy() {
        }
    }
}
