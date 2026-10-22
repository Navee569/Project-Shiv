package app.shiv

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // WorkManager initialization can touch its database; keep it off the cold-start UI thread.
        lifecycleScope.launch(Dispatchers.IO) {
            try { ProactiveWorker.schedule(applicationContext) }
            catch (failure: Exception) { Log.e("ShivMainActivity", "Unable to schedule proactive work", failure) }
        }
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { ShivApp() } }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("shiv_preferences", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_unlock_check", 0L) >= 60 * 60 * 1000L) {
            prefs.edit().putLong("last_unlock_check", now).apply()
            lifecycleScope.launch(Dispatchers.IO) {
                try { ProactiveWorker.checkSoon(applicationContext) }
                catch (failure: Exception) { Log.e("ShivMainActivity", "Unable to enqueue proactive check", failure) }
            }
        }
    }
}
