package app.shiv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = ShivRepository(app)
    val messages = repository.messages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            repository.add(ChatMessage(text = text, fromUser = true))
            val extracted = LocalEventExtractor.extract(text)
            if (extracted != null) {
                repository.addEvent(extracted)
                repository.add(ChatMessage(text = "I saved this as a ${extracted.kind.lowercase()}. You can review it in your private memory.", fromUser = false, eventLabel = extracted.kind.lowercase()))
                if (extracted.kind == "REMINDER") ReminderScheduler.schedule(getApplication(), extracted)
            } else {
                repository.add(ChatMessage(text = "I’m here. Tell me what you’d like me to remember or help you plan.", fromUser = false))
            }
        }
    }
}
