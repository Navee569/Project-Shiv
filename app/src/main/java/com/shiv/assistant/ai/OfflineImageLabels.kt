package com.shiv.assistant.ai

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bundled on-device ML Kit image LABELS, not captioning/OCR.
 * No cloud image upload. Text in bills and local-language scripts is not inferred.
 */
class OfflineImageLabels(private val context:Context) {
    suspend fun labels(uri:Uri):List<String> = suspendCancellableCoroutine { continuation ->
        val labeler=ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        try {
            val image=InputImage.fromFilePath(context,uri)
            labeler.process(image).addOnSuccessListener { output ->
                if(continuation.isActive)continuation.resume(
                    output.filter{it.confidence>=0.60f}.take(12).map {
                        it.text+" ("+(it.confidence*100).toInt()+"%)" })
            }.addOnFailureListener { error ->
                if(continuation.isActive)continuation.resumeWithException(error)
            }.addOnCompleteListener {labeler.close()}
        } catch (e:Exception) {
            labeler.close()
            if(continuation.isActive)continuation.resumeWithException(e)
        }
    }
}
