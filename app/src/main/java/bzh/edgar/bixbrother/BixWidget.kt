package bzh.edgar.bixbrother

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BixWidget : AppWidgetProvider() {
    companion object {
        const val ACTION_AFTER_ADDED = "bzh.edgar.bixbrother.intent.action.BIX_WIDGET_AFTER_ADDED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_AFTER_ADDED) {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                onAfterAdded(context, appWidgetId)
            }
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list)
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetId, appWidgetManager)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val dao = context.bixApp.db.dao()
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            for (appWidgetId in appWidgetIds) {
                dao.deleteWidget(appWidgetId)
            }
        }.invokeOnCompletion { pendingResult.finish() }
    }

    fun onAfterAdded(context: Context, appWidgetId: Int) {
        val dao = context.bixApp.db.dao()
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            dao.persistWidget(appWidgetId)
            val intent = Intent(context, BixWidget::class.java)
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            context.sendBroadcast(intent)
        }.invokeOnCompletion { pendingResult.finish() }
    }

    private fun updateWidget(
        context: Context,
        appWidgetId: Int,
        appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context),
    ) {
        val views = BixWidgetLayout.inflateWidget(context, appWidgetId)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
