package com.safemode.llconnect.ui.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.safemode.llconnect.data.scan.DocumentDetector
import com.safemode.llconnect.data.scan.ReceiptImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.min

/** How long the detected boundary box lingers after detection drops out, to avoid flicker. */
private const val BOX_HOLD_MS = 500L

/**
 * Camera screen that captures a receipt, flattens it with OpenCV document detection, and hands
 * the compressed JPEG back to the caller as a cache file path via [onResult].
 *
 * A live analysis stream detects the document on each frame and draws a boundary box over the
 * preview, so the user can see what will be cropped. When a box is showing at capture time, the
 * still is flattened using those same corners (what you see is what you get).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanReceiptScreen(
    onBack: () -> Unit,
    onResult: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Keep the whole frame visible (FIT_CENTER) and request 4:3 on every use case so the preview,
    // the analysis frames, and the captured still share a field of view. That lets the overlay and
    // the capture use the same detected corners without a coordinate mismatch.
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
    }
    val resolutionSelector = remember {
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO),
            )
            .build()
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(resolutionSelector)
            .build()
    }
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(resolutionSelector)
            .build()
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // Latest detected document corners (ordered, in upright analysis-frame coordinates) and the
    // upright frame size they were measured against, for mapping onto the preview and the still.
    var quad by remember { mutableStateOf<FloatArray?>(null) }
    var frameW by remember { mutableIntStateOf(0) }
    var frameH by remember { mutableIntStateOf(0) }

    var review by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Timestamp of the last successful detection, so the box stays put through brief misses
    // instead of flickering on and off frame to frame.
    val lastHit = remember { longArrayOf(0L) }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        imageAnalysis.setAnalyzer(analysisExecutor) { proxy ->
            try {
                val upright = rotate(proxy.toBitmap(), proxy.imageInfo.rotationDegrees)
                val detected = DocumentDetector.detectQuad(upright)
                val now = System.currentTimeMillis()
                if (detected != null) {
                    frameW = upright.width
                    frameH = upright.height
                    // Exponential smoothing against the previous box to damp corner wobble.
                    val prev = quad
                    quad = if (prev != null && prev.size == 8) {
                        FloatArray(8) { i -> prev[i] * 0.5f + detected[i] * 0.5f }
                    } else {
                        detected
                    }
                    lastHit[0] = now
                } else if (now - lastHit[0] > BOX_HOLD_MS) {
                    // Only drop the box once detection has been missing for a short grace period.
                    quad = null
                }
            } catch (_: Throwable) {
                // Skip this frame; detection resumes on the next one.
            } finally {
                proxy.close()
            }
        }
        val provider = context.awaitCameraProvider()
        val preview = Preview.Builder().setResolutionSelector(resolutionSelector).build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis,
                imageCapture,
            )
        }.onFailure { error = "Couldn't start the camera." }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan receipt") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !hasPermission -> PermissionPrompt(
                    onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                )

                review != null -> ReviewPane(
                    bitmap = review!!,
                    busy = busy,
                    onRetake = { review = null; error = null },
                    onUse = {
                        busy = true
                        scope.launch {
                            val path = withContext(Dispatchers.IO) {
                                val bytes = ReceiptImage.compress(review!!)
                                ReceiptImage.writeToCache(context, bytes)
                            }
                            onResult(path)
                        }
                    },
                )

                else -> CapturePane(
                    previewView = previewView,
                    quad = quad,
                    frameW = frameW,
                    frameH = frameH,
                    busy = busy,
                    onCapture = {
                        busy = true
                        error = null
                        // Snapshot the live detection so the still is cropped to the box on screen.
                        val liveQuad = quad
                        val liveW = frameW
                        val liveH = frameH
                        imageCapture.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val degrees = image.imageInfo.rotationDegrees
                                    val raw = image.toBitmap()
                                    image.close()
                                    scope.launch {
                                        val flattened = withContext(Dispatchers.Default) {
                                            flattenCapture(raw, degrees, liveQuad, liveW, liveH)
                                        }
                                        review = flattened
                                        busy = false
                                    }
                                }

                                override fun onError(exc: ImageCaptureException) {
                                    error = "Capture failed. Try again."
                                    busy = false
                                }
                            },
                        )
                    },
                )
            }

            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun CapturePane(
    previewView: PreviewView,
    quad: FloatArray?,
    frameW: Int,
    frameH: Int,
    busy: Boolean,
    onCapture: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Boundary box over the preview, mapping upright frame coordinates to the view with the
        // same FIT_CENTER math the PreviewView uses.
        Canvas(modifier = Modifier.matchParentSize()) {
            val q = quad ?: return@Canvas
            if (frameW <= 0 || frameH <= 0) return@Canvas
            val scale = min(size.width / frameW, size.height / frameH)
            val dx = (size.width - frameW * scale) / 2f
            val dy = (size.height - frameH * scale) / 2f
            fun corner(i: Int) = Offset(q[i * 2] * scale + dx, q[i * 2 + 1] * scale + dy)
            val path = Path().apply {
                val c0 = corner(0); moveTo(c0.x, c0.y)
                val c1 = corner(1); lineTo(c1.x, c1.y)
                val c2 = corner(2); lineTo(c2.x, c2.y)
                val c3 = corner(3); lineTo(c3.x, c3.y)
                close()
            }
            drawPath(path, color = Color(0xFF4CAF50), style = Stroke(width = 3.dp.toPx()))
        }

        if (quad != null) {
            Text(
                text = "Document detected",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .background(Color(0xFF4CAF50), MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            FilledIconButton(
                onClick = onCapture,
                enabled = !busy,
                modifier = Modifier.size(72.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        Icons.Filled.CameraAlt,
                        contentDescription = "Capture",
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewPane(
    bitmap: Bitmap,
    busy: Boolean,
    onRetake: () -> Unit,
    onUse: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Scanned receipt preview",
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f))
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onUse,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Use this scan")
                }
            }
            OutlinedButton(
                onClick = onRetake,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Retake")
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Camera access is needed to scan a receipt.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onGrant) { Text("Grant camera access") }
    }
}

/**
 * Flattens a captured still. Prefers the live-detected [liveQuad] (scaled from the analysis frame
 * into the still's coordinate space) so the crop matches the on-screen box; falls back to detecting
 * on the full-resolution still, then to the un-cropped photo.
 */
private fun flattenCapture(
    raw: Bitmap,
    degrees: Int,
    liveQuad: FloatArray?,
    liveW: Int,
    liveH: Int,
): Bitmap {
    val upright = rotate(raw, degrees)
    if (liveQuad != null && liveW > 0 && liveH > 0) {
        val sx = upright.width.toFloat() / liveW
        val sy = upright.height.toFloat() / liveH
        val scaled = FloatArray(8) { i -> if (i % 2 == 0) liveQuad[i] * sx else liveQuad[i] * sy }
        DocumentDetector.flattenWithQuad(upright, scaled)?.let { return it }
    }
    return DocumentDetector.detectAndFlatten(upright) ?: upright
}

/** Awaits the CameraX provider without blocking the main thread. */
private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(this))
    }

private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
