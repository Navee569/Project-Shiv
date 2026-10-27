package com.shiv.assistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

@Composable
fun StorageCheckingScreen() {
    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Checking encrypted Shiv storage…")
        }
    }
}

/** Owner-visible, fail-closed recovery. Nothing is erased without the exact phrase. */
@Composable
fun StorageRecoveryScreen(message:String,onRetry:()->Unit,onErase:(String)->Unit) {
    var phrase by remember { mutableStateOf("") }
    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center) {
            Text("SHIV STORAGE NEEDS ATTENTION",style=MaterialTheme.typography.headlineSmall,
                modifier=Modifier.semantics { heading() })
            Spacer(Modifier.height(16.dp))
            Text(message)
            Spacer(Modifier.height(12.dp))
            Text("You can retry without changing your data. If the Android encryption key was lost or the database is damaged, an existing portable backup can be restored only after starting with new storage.")
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick=onRetry) { Text("RETRY OPENING") }
            Spacer(Modifier.height(24.dp))
            Text("Erase encrypted Shiv records on this phone only. This cannot be undone. Imported model files may remain and need reimporting. External phone calendar events are not erased.")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value=phrase,onValueChange={phrase=it.take(18)},
                label={Text("Type ERASE SHIV STORAGE")},singleLine=true)
            Spacer(Modifier.height(8.dp))
            Button(enabled=phrase=="ERASE SHIV STORAGE",onClick={
                onErase(phrase)
                phrase=""
            }) { Text("ERASE AND START FRESH") }
        }
    }
}
