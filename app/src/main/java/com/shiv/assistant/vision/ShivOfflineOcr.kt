package com.shiv.assistant.vision

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Bundled, offline-only ML Kit Latin-script OCR. Owner selects a single image.
 * Kannada/Telugu script support is NOT claimed. No implicit persistence or networking.
 */
object ShivOfflineOcr {
    suspend fun scan(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context.applicationContext, uri)
        val reader = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            reader.process(image).await().text.take(10_000)
        } finally {
            reader.close()
        }
    }
}
