package app.shiv

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var storageReady by mutableStateOf(false)
    private var storageFailure by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                when {
                    storageReady -> ShivApp()
                    storageFailure != null -> Surface(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("Shiv could not unlock its encrypted local storage. Your database has not been deleted or reset. " + storageFailure + "\n\nIf the original Android Keystore key is unavailable, restore from a valid encrypted backup if you have one.")
                        }
                    }
                    else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                ShivDatabase.get(applicationContext)
                try { ProactiveWorker.schedule(applicationContext) }
                catch (failure: Exception) { Log.e("ShivMainActivity", "Unable to schedule proactive work", failure) }
                withContext(Dispatchers.Main) { storageReady = true }
            } catch (failure: Exception) {
                Log.e("ShivMainActivity", "Encrypted storage initialization failed", failure)
                withContext(Dispatchers.Main) { storageFailure = failure.message ?: failure.javaClass.simpleName }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!storageReady) return
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
