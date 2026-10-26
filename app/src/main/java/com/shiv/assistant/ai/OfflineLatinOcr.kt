package com.shiv.assistant.ai

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Bundled on-device Latin OCR for an explicitly owner-selected photo.
 * No upload, background camera capture, or persistent raw photo/text storage. */
class OfflineLatinOcr(private val context:Context) {
    suspend fun extract(uri:Uri):String = suspendCancellableCoroutine { continuation ->
        val reader=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val image=InputImage.fromFilePath(context,uri)
            reader.process(image).addOnSuccessListener { output ->
                if(continuation.isActive)
                    continuation.resume(output.text.take(2_000))
            }.addOnFailureListener {error->
                if(continuation.isActive)continuation.resumeWithException(error)
            }.addOnCompleteListener {reader.close()}
        } catch(e:Exception) {
            reader.close()
            if(continuation.isActive)continuation.resumeWithException(e)
        }
    }
}
