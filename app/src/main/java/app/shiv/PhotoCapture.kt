package app.shiv

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage

@Composable
fun PhotoCapture(onClose: () -> Unit, onDescription: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    fun permitted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val controller = remember { LifecycleCameraController(context).apply { cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA } }
    var granted by remember { mutableStateOf(permitted()) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result && permitted()
        error = if (granted) null else "Camera permission is required. Allow access in app settings and try again."
    }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = permitted()
                if (!granted) { working = false; error = "Camera permission is disabled. Allow access to take a photo." }
                else if (error?.contains("permission", true) == true) error = null
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(controller, owner, granted) {
        if (granted) try { controller.bindToLifecycle(owner) }
        catch (_: Exception) { error = "The camera is unavailable. Close other camera apps and try again." }
        onDispose { controller.unbind() }
    }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Ask Shiv about a photo", style = MaterialTheme.typography.titleLarge)
        Text("The image is analyzed on this phone and isn’t saved by Shiv.")
        Spacer(Modifier.height(12.dp))
        if (granted) AndroidView(
            factory = { PreviewView(it).apply { controller = controller } },
            update = { it.controller = controller },
            modifier = Modifier.fillMaxWidth().height(380.dp)
        ) else Text("Allow camera access to preview and analyze a photo.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onClose, enabled = !working) { Text("Close") }
            Button(onClick = {
                if (!permitted()) {
                    granted = false
                    permissionRequest.launch(Manifest.permission.CAMERA)
                    return@Button
                }
                granted = true
                error = null
                working = true
                controller.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                    @ExperimentalGetImage
                    override fun onCaptureSuccess(proxy: androidx.camera.core.ImageProxy) {
                        val done = PhotoCaptureCompletion(
                            closeImage = { proxy.close() },
                            onComplete = { failure ->
                                working = false
                                if (failure != null) error = "The photo couldn’t be processed. Please try again."
                            }
                        )
                        try {
                            val media = proxy.image
                            if (media == null) { done.finish(IllegalStateException("Empty camera image")); return }
                            Brain().describePhoto(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)) { description ->
                                done.finishSuccess { onDescription(description); onClose() }
                            }
                        } catch (failure: Exception) { done.finish(failure) }
                    }
                    override fun onError(exception: ImageCaptureException) {
                        working = false
                        error = "The photo couldn’t be captured. Please try again."
                    }
                })
            }, enabled = !working) {
                Text(if (working) "Looking…" else if (granted) "Take photo" else "Allow camera")
            }
        }
    }
}
