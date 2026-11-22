package net.hilson.qrieux.scanner

import android.graphics.BitmapFactory
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import net.hilson.qrieux.AndroidContext
import net.hilson.qrieux.R
import net.hilson.qrieux.vibrate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.camera.core.FocusMeteringAction
import java.util.concurrent.TimeUnit
import org.jetbrains.compose.resources.stringResource as sharedStringResource
import qr_scanner.composeapp.generated.resources.Res
import qr_scanner.composeapp.generated.resources.incognito_mode
import qr_scanner.composeapp.generated.resources.incognito_turn_off
import qr_scanner.composeapp.generated.resources.incognito_turn_on

@Composable
fun CameraPreview(
    onQrCodeDetected: (String) -> Unit,
    isScanning: Boolean,
    onGalleryClick: () -> Unit,
    modifier: Modifier = Modifier,
    demoMode: Boolean = false,
    demoBackgroundPath: String? = null,
    incognito: Boolean = false,
    onToggleIncognito: () -> Unit = {}
) {
    if (demoMode) {
        DemoCameraPreview(
            onGalleryClick = onGalleryClick,
            modifier = modifier,
            backgroundPath = demoBackgroundPath,
            incognito = incognito,
            onToggleIncognito = onToggleIncognito
        )
    } else {
        LiveCameraPreview(
            onQrCodeDetected = onQrCodeDetected,
            isScanning = isScanning,
            onGalleryClick = onGalleryClick,
            modifier = modifier,
            incognito = incognito,
            onToggleIncognito = onToggleIncognito
        )
    }
}

@Composable
private fun DemoCameraPreview(
    onGalleryClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundPath: String? = null,
    incognito: Boolean = false,
    onToggleIncognito: () -> Unit = {}
) {
    val bgBitmap = remember(backgroundPath) {
        backgroundPath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier = modifier.fillMaxSize()) {
            if (bgBitmap != null) {
                Image(
                    bitmap = bgBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(Color(0xFF1A1A2E))
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF16213E), Color(0xFF0F3460)),
                            center = center,
                            radius = size.minDimension * 0.8f
                        )
                    )
                }
            }

            Box(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                ScanOverlay()
            }
            CameraButtons(
                flashEnabled = false,
                onFlashClick = {},
                onGalleryClick = onGalleryClick,
                incognito = incognito,
                onToggleIncognito = onToggleIncognito
            )
        }
    }
}

@Composable
private fun LiveCameraPreview(
    onQrCodeDetected: (String) -> Unit,
    isScanning: Boolean,
    onGalleryClick: () -> Unit,
    modifier: Modifier = Modifier,
    incognito: Boolean = false,
    onToggleIncognito: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var flashEnabled by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    // Decoding runs on the analysis executor, so hand the result back on the main thread.
    val qrAnalyzer = remember { QrAnalyzer { qrValue ->
        ContextCompat.getMainExecutor(context).execute {
            vibrate(AndroidContext(context))
            onQrCodeDetected(qrValue)
        }
    } }

    LaunchedEffect(isScanning) {
        qrAnalyzer.isEnabled = isScanning
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                flashEnabled = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val cameraExecutor = remember(lifecycleOwner) { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    // Tap to focus, double tap / pinch to zoom: small or far codes come out blurry otherwise.
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            kotlinx.coroutines.delay(1200)
            focusPoint = null
        }
    }

    fun setZoom(ratio: Float) {
        val cam = camera ?: return
        val state = cam.cameraInfo.zoomState.value
        val clamped = ratio.coerceIn(state?.minZoomRatio ?: 1f, state?.maxZoomRatio ?: 1f)
        zoomRatio = clamped
        cam.cameraControl.setZoomRatio(clamped)
    }

    fun focusAt(point: Offset) {
        val view = previewViewRef ?: return
        val cam = camera ?: return
        val meteringPoint = view.meteringPointFactory.createPoint(point.x, point.y)
        val action = FocusMeteringAction.Builder(
            meteringPoint,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
        cam.cameraControl.startFocusAndMetering(action)
        focusPoint = point
    }

    // Force LTR so button positions don't flip in RTL locales (camera UI is ergonomic, not directional)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier = modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(cameraExecutor, qrAnalyzer)
                            }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewViewRef = previewView
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { testTag = "camera_gestures" }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { focusAt(it) },
                            onDoubleTap = {
                                setZoom(
                                    when {
                                        zoomRatio < 1.9f -> 2f
                                        zoomRatio < 3.9f -> 4f
                                        else -> 1f
                                    }
                                )
                                focusAt(it)
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            if (zoom != 1f) setZoom(zoomRatio * zoom)
                        }
                    }
            )

            ScanOverlay()

            focusPoint?.let { p ->
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Color.White, radius = 36.dp.toPx(), center = p, style = Stroke(width = 3.dp.toPx()))
                }
            }

            // Just under the scan frame (same geometry as ScanOverlay), clear of the tab bar.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val frameSize = minOf(maxWidth, maxHeight) * 0.7f
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = (maxHeight + frameSize) / 2 + 12.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Big, visible zoom control: gestures alone are hard to discover.
                    androidx.compose.material3.Button(
                        onClick = {
                            setZoom(
                                when {
                                    zoomRatio < 1.9f -> 2f
                                    zoomRatio < 3.9f -> 4f
                                    else -> 1f
                                }
                            )
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.height(56.dp).semantics { testTag = "zoom_button" }
                    ) {
                        Icon(
                            imageVector = if (zoomRatio < 3.9f) Icons.Default.ZoomIn else Icons.Default.ZoomOut,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                        Text("%.0f×".format(zoomRatio), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = stringResource(R.string.camera_gesture_hint),
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .background(Color.Black.copy(alpha = 0.45f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
            CameraButtons(
                flashEnabled = flashEnabled,
                onFlashClick = {
                    flashEnabled = !flashEnabled
                    camera?.cameraControl?.enableTorch(flashEnabled)
                },
                onGalleryClick = onGalleryClick,
                incognito = incognito,
                onToggleIncognito = onToggleIncognito
            )
        }
    }
}

@Composable
private fun CameraButtons(
    flashEnabled: Boolean,
    onFlashClick: () -> Unit,
    onGalleryClick: () -> Unit,
    incognito: Boolean = false,
    onToggleIncognito: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
    ) {
        FilledIconButton(
            onClick = onFlashClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .size(64.dp)
                .semantics { testTag = "flash_button" },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (flashEnabled) Color.White else Color.Black.copy(alpha = 0.5f),
                contentColor = if (flashEnabled) Color.Black else Color.White
            )
        ) {
            Icon(
                painter = painterResource(
                    if (flashEnabled) R.drawable.ic_flash_off else R.drawable.ic_flash_on
                ),
                contentDescription = stringResource(
                    if (flashEnabled) R.string.flash_turn_off else R.string.flash_turn_on
                ),
                modifier = Modifier.size(32.dp)
            )
        }

        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FilledIconButton(
                onClick = onToggleIncognito,
                modifier = Modifier
                    .size(64.dp)
                    .semantics { testTag = "incognito_button" },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (incognito) Color(0xFF6A1B9A) else Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = if (incognito) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = sharedStringResource(
                        if (incognito) Res.string.incognito_turn_off else Res.string.incognito_turn_on
                    ),
                    modifier = Modifier.size(32.dp)
                )
            }
            if (incognito) {
                Text(
                    text = sharedStringResource(Res.string.incognito_mode),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .background(Color(0xFF6A1B9A), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                        .semantics { testTag = "incognito_label" }
                )
            }
        }

        FilledIconButton(
            onClick = onGalleryClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
                .size(64.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = stringResource(R.string.gallery_pick_photo),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
internal fun ScanOverlay() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val frameSize = minOf(maxWidth, maxHeight) * 0.7f
        val frameTopOffset = (maxHeight - frameSize) / 2

        Canvas(modifier = Modifier.fillMaxSize()) {
            val overlayColor = Color.Black.copy(alpha = 0.6f)
            val frameSizePx = size.minDimension * 0.7f
            val frameLeft = (size.width - frameSizePx) / 2
            val frameTop = (size.height - frameSizePx) / 2

            drawRect(color = overlayColor)

            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(frameLeft, frameTop),
                size = Size(frameSizePx, frameSizePx),
                cornerRadius = CornerRadius(24.dp.toPx()),
                blendMode = BlendMode.Clear
            )

            drawRoundRect(
                color = Color.White,
                topLeft = Offset(frameLeft, frameTop),
                size = Size(frameSizePx, frameSizePx),
                cornerRadius = CornerRadius(24.dp.toPx()),
                style = Stroke(width = 4.dp.toPx())
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(frameTopOffset)
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = stringResource(R.string.scan_instruction),
                modifier = Modifier.padding(bottom = 16.dp),
                style = TextStyle(
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    shadow = Shadow(
                        color = Color.Black,
                        offset = Offset(2f, 2f),
                        blurRadius = 4f
                    )
                )
            )
        }
    }
}
