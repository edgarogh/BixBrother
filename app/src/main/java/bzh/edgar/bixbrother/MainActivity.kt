package bzh.edgar.bixbrother

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.google.android.material.button.MaterialButton

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        val button = findViewById<MaterialButton>(R.id.add_widget_button)

        button.setOnClickListener {
            val options = ActivityOptions.makeSceneTransitionAnimation(this, button, "mainTransition")
            startActivity(Intent(this, BixWidgetConfigureActivity::class.java), options.toBundle())
            finish()
        }
    }
}
