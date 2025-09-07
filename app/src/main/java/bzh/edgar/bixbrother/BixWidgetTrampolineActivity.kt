package bzh.edgar.bixbrother

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import androidx.core.net.toUri

/**
 * Activity whose only goal is to start an app, or the Google Play store if it's missing
 */
class BixWidgetTrampolineActivity : Activity() {
    companion object {
        const val EXTRA_TARGET_PACKAGE = "bzh.edgar.bixbrother.intent.extra.EXTRA_TARGET_PACKAGE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra(EXTRA_TARGET_PACKAGE)!!

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val intentSender = packageManager.getLaunchIntentSenderForPackage(packageName)
            try {
                intentSender.sendIntent(null, 0, null, null, null)
            } catch (_: IntentSender.SendIntentException) {
                launchPlayStore(packageName)
            } finally {
                finish()
            }
        } else {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) {
                launchPlayStore(packageName)
            } else {
                try {
                    startActivity(launchIntent)
                } catch (_: ActivityNotFoundException) {
                    launchPlayStore(packageName)
                }
            }
            finish()
        }
    }

    private fun launchPlayStore(packageName: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri()))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri()))
        }
    }
}
