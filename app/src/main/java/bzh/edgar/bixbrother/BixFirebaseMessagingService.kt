package bzh.edgar.bixbrother

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import com.google.firebase.Firebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.messaging.messaging
import java.util.UUID

@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class BixFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data.contains("i")) {
            val stationId = UUID.fromString(data["i"]!!)
            val numBikes = data["b"]?.toInt() ?: 0
            val numEbikes = data["e"]?.toInt() ?: 0
            val numDocks = data["a"]?.toInt() ?: 0
            val updatedAt = data["u"]?.toLong() ?: 0

            val dao = (application as BixApplication).db.dao()

            val widgets = dao.getStationWidgetsSync(stationId)
            if (widgets.isEmpty()) {
                // As a safeguard, if we receive an update from a station we don't care about, we
                // unsubscribe from it. This should not happen, but we're never too cautious.
                Firebase.messaging.unsubscribeFromTopic("v1status_$stationId")
            } else {
                dao.updateStationStatus(stationId, numBikes, numEbikes, numDocks, updatedAt)

                val intent = Intent(bixApp, BixWidget::class.java)
                intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val ids = AppWidgetManager.getInstance(bixApp).getAppWidgetIds(ComponentName(bixApp, BixWidget::class.java))
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                sendBroadcast(intent)
            }
        }
    }
}
