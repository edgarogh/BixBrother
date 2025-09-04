package bzh.edgar.bixbrother

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.search.SearchView
import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.asDeferred
import java.io.IOException
import java.util.UUID

class BixWidgetConfigureActivity : ComponentActivity(), ActiveEntryCallback.Callback {
    var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    lateinit var resultValue: Intent

    val activeTouchHelper = ItemTouchHelper(ActiveEntryCallback(this))
    val activeEntryAdapter = ActiveEntryAdapter(activeTouchHelper)

    val searchResults by lazy { findViewById<RecyclerView>(R.id.search_view_results)!! }
    val confirmButton by lazy { findViewById<MaterialButton>(R.id.buttonPositive)!! }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.widget_configure_activity)

        appWidgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, resultValue)

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            confirmButton.setText(R.string.activity_configure_add_new)
        }

        confirmButton.setOnClickListener {
            doConfigure()
        }

        val sv = findViewById<SearchView>(R.id.search_view)

        searchResults.layoutManager = LinearLayoutManager(this)
        val searchResultsAdapter = SearchResultsAdapter(this)
        searchResults.adapter = searchResultsAdapter

        val bixApp = bixApp
        val dao = bixApp.db.dao()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val res = bixApp.apiClient.getStationInfos()
                dao.tmpUpsertStations(res)
                val firstStations = dao.firstStations()

                runOnUiThread {
                    searchResultsAdapter.items = firstStations
                }
            } catch (_: IOException) {
                runOnUiThread {
                    Toast.makeText(this@BixWidgetConfigureActivity, "Network unreachable", Toast.LENGTH_SHORT).show()
                }
            }
        }

        sv.editText.addTextChangedListener {
            if (sv.editText.length() != 0) {
                lifecycleScope.launch {
                    val text = sv.editText.text.toString()
                    val stations = try {
                        if (text.length != 36) throw IllegalArgumentException()
                        val uuid = UUID.fromString(text)
                        listOfNotNull(dao.stationByUuid(uuid))
                    } catch (_: IllegalArgumentException) {
                        val searchTerms = text.replace(Regex("[%_]"), "")
                        dao.stationsByName("%$searchTerms%")
                    }

                    runOnUiThread {
                        searchResultsAdapter.items = stations
                    }
                }
            }
        }

        val activeStations = findViewById<RecyclerView>(R.id.active_entry_list)
        activeTouchHelper.attachToRecyclerView(activeStations)
        activeStations.layoutManager = LinearLayoutManager(this)
        activeStations.adapter = activeEntryAdapter

        searchResultsAdapter.onClickListener = {
            sv.hide()
            activeEntryAdapter.items += it
            activeEntryAdapter.notifyItemInserted(activeEntryAdapter.items.size - 1)
            updateConfirmButton()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                activeEntryAdapter.items = dao.getWidgetStations(appWidgetId).toMutableList()
            }

            runOnUiThread {
                updateConfirmButton()
            }
            // TODO enable search bar
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            sv.setVisible(true)
            sv.requestFocusAndShowKeyboard()
        }
    }

    fun updateConfirmButton() {
        confirmButton.isEnabled = !activeEntryAdapter.items.isEmpty()
    }

    override fun doDelete(index: Int) {
        activeEntryAdapter.items.removeAt(index)
        activeEntryAdapter.notifyItemRemoved(index)
        updateConfirmButton()
    }

    override fun doMove(fromPos: Int, toPos: Int) {
        activeEntryAdapter.onRowMoved(fromPos, toPos)
        updateConfirmButton()
    }

    private fun doConfigure() {
        if (activeEntryAdapter.items.isEmpty()) {
            finish()
            return
        }

        confirmButton.isEnabled = false
        // TODO disable search

        val dao = bixApp.db.dao()
        lifecycleScope.launch(Dispatchers.IO) {
            val (deleted, added) = dao.updateWidget(appWidgetId, activeEntryAdapter.items.map { it.externalId })

            val tasks = mutableListOf<Deferred<Void>>()
            for (d in deleted) {
                tasks += Firebase.messaging.unsubscribeFromTopic("v1status_${d.externalId}").asDeferred()
                Log.d("BixWidgetConfigureActivity", "Unsubscribing from ${d.externalId}")
            }
            for (a in added) {
                tasks += Firebase.messaging.subscribeToTopic("v1status_${a.externalId}").asDeferred()
                Log.d("BixWidgetConfigureActivity", "Subscribing to ${a.externalId}")
            }

            tasks.awaitAll()

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val intent = Intent(bixApp, BixWidget::class.java)
                intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val ids = AppWidgetManager.getInstance(bixApp).getAppWidgetIds(ComponentName(bixApp, BixWidget::class.java))
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                sendBroadcast(intent)
                setResult(RESULT_OK, resultValue)
            } else {
                doAddNew()
            }

            finish()
        }
    }

    private fun doAddNew() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val bixWidget = ComponentName(bixApp, BixWidget::class.java)

        val successCallbackIntent = Intent(this, BixWidget::class.java).apply {
            action = BixWidget.ACTION_AFTER_ADDED
        }
        val successCallback = PendingIntent.getBroadcast(this, 0, successCallbackIntent, PendingIntent.FLAG_MUTABLE)

        val extras = Bundle()
        extras.putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, RemoteViews(
            packageName,
            R.layout.bix_widget
        ).apply {
            setRemoteAdapter(R.id.widget_list, Intent(bixApp, BixWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                data = toUri(Intent.URI_INTENT_SCHEME).toUri()
            })
        })

        appWidgetManager.requestPinAppWidget(bixWidget, extras, successCallback)
        setResult(RESULT_OK, Intent())
    }
}
