package app.shiv

import android.content.Context

/** One-shot microphone input using the phone's on-device recognizer only. */
class SpeechInput(context: Context, onText: (String) -> Unit, onError: (Int) -> Unit) {
    private val transcriber = Brain().createOnDeviceTranscriber(context.applicationContext, onText, onError)
    val isAvailable: Boolean get() = transcriber != null
    fun start() { transcriber?.start() }
    fun close() { transcriber?.close() }
}
