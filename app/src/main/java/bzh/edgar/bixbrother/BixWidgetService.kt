package bzh.edgar.bixbrother

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViewsService

class BixWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent) = RemoteViewsFactory(intent, this)

    class RemoteViewsFactory(intent: Intent, val context: Context) : RemoteViewsService.RemoteViewsFactory {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        private var stations: List<StationEntry> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_INITIAL_CACHED_STATIONS, StationEntry::class.java)?.apply {
                Log.e("BixWidgetService", "${this.size} stations")
            }
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(EXTRA_INITIAL_CACHED_STATIONS)
        } ?: listOf()

        override fun getCount() = stations.size
        override fun getItemId(index: Int) = (index + 1).toLong()
        override fun getLoadingView() = null
        override fun getViewAt(index: Int) = BixWidgetLayout.inflateItem(context, stations[index])
        override fun getViewTypeCount() = 1
        override fun hasStableIds() = true

        override fun onCreate() {
        }

        override fun onDataSetChanged() {
            stations = context.bixApp.db.dao().getWidgetStationsSync(widgetId).map(::StationEntry)
        }

        override fun onDestroy() {
        }
    }
}
