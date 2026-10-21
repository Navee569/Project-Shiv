package app.shiv

import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.core.CameraSelector
import androidx.camera.view.CameraController
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage

@Composable
fun PhotoCapture(onClose: () -> Unit, onDescription: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }
    var working by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(controller, lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Ask Shiv about a photo", style = MaterialTheme.typography.titleLarge)
        Text("The image is analyzed on this phone and isn’t saved by Shiv.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        AndroidView(
            factory = { viewContext -> PreviewView(viewContext).apply { this.controller = controller } },
            modifier = Modifier.fillMaxWidth().height(380.dp)
        )
        cameraError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onClose, enabled = !working) { Text("Close") }
            Button(onClick = {
                working = true
                controller.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(imageProxy: androidx.camera.core.ImageProxy) {
                        val mediaImage = imageProxy.image
                        if (mediaImage == null) {
                            imageProxy.close(); working = false; cameraError = "The camera returned an empty photo."; return
                        }
                        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        Brain().describePhoto(input) { description ->
                            imageProxy.close()
                            working = false
                            onDescription(description)
                            onClose()
                        }
                    }
                    override fun onError(exception: ImageCaptureException) {
                        working = false
                        cameraError = "The photo didn’t work. Please try again."
                    }
                })
            }, enabled = !working) { Text(if (working) "Looking…" else "Take photo") }
        }
    }
}
