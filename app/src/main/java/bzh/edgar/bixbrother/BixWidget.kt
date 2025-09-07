package bzh.edgar.bixbrother

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val BIXI_PACKAGE_ID = "com.eightd.biximobile"

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
        appWidgetIds.forEach { appWidgetId ->
            val intent = Intent(context, BixWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = toUri(Intent.URI_INTENT_SCHEME).toUri()
            }

            val views = RemoteViews(context.packageName, R.layout.bix_widget).apply {
                setRemoteAdapter(R.id.widget_list, intent)
            }

            views.setOnClickPendingIntent(
                R.id.widget_unlock_button,
                PendingIntent.getActivity(
                    context,
                    Random.Default.nextInt(),
                    Intent(context, BixWidgetTrampolineActivity::class.java).apply {
                        putExtra(BixWidgetTrampolineActivity.EXTRA_TARGET_PACKAGE, BIXI_PACKAGE_ID)
                    },
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
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
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, BixWidget::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }.invokeOnCompletion { pendingResult.finish() }
    }
}
