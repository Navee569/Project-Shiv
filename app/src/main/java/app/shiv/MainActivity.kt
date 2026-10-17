package app.shiv

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { ShivChat() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShivChat(vm: ChatViewModel = viewModel()) {
    val context = LocalContext.current
    val messages by vm.messages.collectAsState()
    var draft by remember { mutableStateOf("") }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Shiv", style = MaterialTheme.typography.titleLarge) }) },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f), placeholder = { Text("Message Shiv…") }, shape = RoundedCornerShape(24.dp), maxLines = 4)
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val message = draft
                    draft = ""
                    if (Build.VERSION.SDK_INT >= 33 && LocalEventExtractor.extract(message) != null &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    vm.send(message)
                }, enabled = draft.isNotBlank()) { Text("Send") }
            }
        }
    ) { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            item { Text("Your private assistant", style = MaterialTheme.typography.headlineSmall) }
            items(messages, key = { it.id }) { message ->
                Surface(color = if (message.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(if (message.fromUser) .88f else 1f)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(message.text)
                        if (message.eventLabel != null) {
                            Spacer(Modifier.height(8.dp))
                            AssistChip(onClick = {}, label = { Text("Saved ${message.eventLabel}") })
                        }
                    }
                }
            }
        }
    }
}
