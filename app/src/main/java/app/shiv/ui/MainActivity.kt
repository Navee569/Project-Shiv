package app.shiv.ui

import app.shiv.platform.ProactiveWorker
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme

open class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProactiveWorker.schedule(applicationContext)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { ShivApp() } }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("shiv_preferences", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_unlock_check", 0L) >= 60 * 60 * 1000L) {
            prefs.edit().putLong("last_unlock_check", now).apply()
            ProactiveWorker.checkSoon(applicationContext)
        }
    }
}
