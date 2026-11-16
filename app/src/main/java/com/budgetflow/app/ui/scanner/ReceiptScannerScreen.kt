package com.budgetflow.app.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetflow.app.domain.model.ParsedReceipt
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

@Composable
fun ReceiptScannerScreen(
    onReceiptParsed: (ParsedReceipt) -> Unit,
    onCancel: () -> Unit,
    viewModel: ReceiptScannerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val statusText by viewModel.statusText.collectAsStateWithLifecycle()
    val imageCapture = remember { ImageCapture.Builder().build() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasCameraPermission) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Camera permission is needed to scan receipts.", style = MaterialTheme.typography.bodyLarge)
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.padding(top = 12.dp)
            ) { Text("Grant Camera Permission") }
            OutlinedButton(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) {
                Text("Back")
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().apply {
                            setSurfaceProvider(previewView.surfaceProvider)
                        }
                        runCatching {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture
                            )
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            }
        )

        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(statusText, style = MaterialTheme.typography.bodyMedium)
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        viewModel.setStatus("Scanning receipt...")
                        imageCapture.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                    val mediaImage = imageProxy.image
                                    if (mediaImage == null) {
                                        imageProxy.close()
                                        viewModel.setStatus("Could not read camera frame.")
                                        return
                                    }
                                    val inputImage = InputImage.fromMediaImage(
                                        mediaImage,
                                        imageProxy.imageInfo.rotationDegrees
                                    )
                                    recognizer.process(inputImage)
                                        .addOnSuccessListener { visionText ->
                                            val parsed = viewModel.parseReceiptText(visionText.text)
                                            imageProxy.close()
                                            onReceiptParsed(parsed)
                                        }
                                        .addOnFailureListener {
                                            imageProxy.close()
                                            viewModel.setStatus("OCR failed. Try again.")
                                        }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    viewModel.setStatus("Capture failed: ${exception.message}")
                                }
                            }
                        )
                    }
                ) {
                    Text("Capture & Extract")
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    }
}
