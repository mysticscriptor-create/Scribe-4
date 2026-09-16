package com.primaloptima.scribe.ui.screens.themeeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.primaloptima.scribe.ui.theme.colorToPerceptualLightness
import com.primaloptima.scribe.ui.theme.computeBgLuminanceField
import com.primaloptima.scribe.ui.theme.computeGlobalDominantColor
import com.primaloptima.scribe.ui.theme.computeZonalDominantColorMatrix
import com.primaloptima.scribe.ui.theme.computeZonalLuminanceMatrix
import com.primaloptima.scribe.ui.theme.computeZonalVarianceMatrix
import com.primaloptima.scribe.util.ThemeGenerationEngine
import com.primaloptima.scribe.util.model.ImageUnderstanding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

data class BgAnalysisResult(
    val avgLightness: Float,
    val zonalLuminance: List<Float>,
    val zonalVariance: List<Float>,
    val dominantColor: String?,
    val zonalColors: List<String>,
    val bgLuminanceField: List<Float> = emptyList()
)

/**
 * Loads [imageUri] through Coil at an aspect-ratio-correct 200px resolution and returns the
 * average perceptual OKLCH lightness, 3x3 zonal matrix, variance, and dominant colors.
 */
suspend fun computeBgAnalysis(context: Context, imageUri: String): BgAnalysisResult {
    return withContext(Dispatchers.IO) {
        try {
            val dm = context.resources.displayMetrics
            val screenW = dm.widthPixels.coerceAtLeast(1)
            val screenH = dm.heightPixels.coerceAtLeast(1)
            val targetW = 200
            val targetH = ((200f * screenH) / screenW).toInt().coerceAtLeast(200)

            val request = ImageRequest.Builder(context)
                .data(imageUri)
                .size(coil3.size.Size(targetW, targetH))
                .allowHardware(false)
                .build()
            val bitmap = (ImageLoader(context).execute(request) as? SuccessResult)
                ?.image
                ?.let { (it as? BitmapImage)?.bitmap }
                ?: return@withContext BgAnalysisResult(-1f, emptyList(), emptyList(), null, emptyList(), emptyList())

            val w = bitmap.width
            val h = bitmap.height
            if (w == 0 || h == 0) return@withContext BgAnalysisResult(-1f, emptyList(), emptyList(), null, emptyList(), emptyList())

            var total = 0.0
            for (x in 0 until w) {
                for (y in 0 until h) {
                    val pixel = bitmap.getPixel(x, y)
                    total += colorToPerceptualLightness(pixel)
                }
            }
            val avgL = (total / (w * h)).toFloat().coerceIn(0f, 1f)
            val zonal = computeZonalLuminanceMatrix(bitmap)
            val zonalVar = computeZonalVarianceMatrix(bitmap)
            val globalDomInt = computeGlobalDominantColor(bitmap)
            val globalDomHex = String.format("#%06X", 0xFFFFFF and globalDomInt)
            val zonalDomInts = computeZonalDominantColorMatrix(bitmap)
            val zonalDomHexes = zonalDomInts.map { String.format("#%06X", 0xFFFFFF and it) }
            val lumField = computeBgLuminanceField(bitmap, 8, 8)

            bitmap.recycle()
            BgAnalysisResult(avgL, zonal, zonalVar, globalDomHex, zonalDomHexes, lumField)
        } catch (_: Exception) {
            BgAnalysisResult(-1f, emptyList(), emptyList(), null, emptyList(), emptyList())
        }
    }
}

/**
 * Decodes a downscaled software [Bitmap] directly from [imageUri] (content or file URI)
 * targeting [targetMaxDim] on its longest edge. Preserves exact aspect ratio and fine details
 * without hardware bitmap or Coil execution failures.
 */
private fun decodeSoftwareBitmap(context: Context, imageUri: String, targetMaxDim: Int = 384): Bitmap? {
    val openStream: () -> java.io.InputStream? = {
        try {
            when {
                imageUri.startsWith("content://") -> context.contentResolver.openInputStream(Uri.parse(imageUri))
                imageUri.startsWith("file://") -> {
                    val path = Uri.parse(imageUri).path
                    if (path != null) java.io.FileInputStream(java.io.File(path)) else null
                }
                imageUri.startsWith("/") -> java.io.FileInputStream(java.io.File(imageUri))
                else -> context.contentResolver.openInputStream(Uri.parse(imageUri))
            }
        } catch (_: Exception) {
            null
        }
    }

    // 1. Probe dimensions
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openStream()?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    } ?: return null

    val rawW = options.outWidth
    val rawH = options.outHeight
    if (rawW <= 0 || rawH <= 0) return null

    // 2. Compute sample size (power-of-2 approximation)
    var sampleSize = 1
    val maxDim = maxOf(rawW, rawH)
    while (maxDim / (sampleSize * 2) >= targetMaxDim) {
        sampleSize *= 2
    }

    // 3. Decode software ARGB_8888 bitmap
    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return openStream()?.use { stream ->
        BitmapFactory.decodeStream(stream, null, decodeOptions)
    }
}

/**
 * Loads [imageUri] at high software fidelity and derives a structured [ImageUnderstanding].
 * Employs direct stream decoding first with aspect-ratio-aware sampling, falling back to
 * Coil 3 software decoding if necessary.
 */
suspend fun computeImageUnderstanding(context: Context, imageUri: String): ImageUnderstanding {
    return withContext(Dispatchers.IO) {
        try {
            // Stage 1: High-fidelity direct decode (preserves fine lines, glowing accents, and non-square aspect ratios)
            val directBitmap = decodeSoftwareBitmap(context, imageUri, targetMaxDim = 384)
            if (directBitmap != null) {
                try {
                    val swBitmap = if (directBitmap.config == Bitmap.Config.HARDWARE) {
                        directBitmap.copy(Bitmap.Config.ARGB_8888, false)
                    } else {
                        directBitmap
                    }
                    if (swBitmap != null) {
                        val understanding = ThemeGenerationEngine.analyzeImage(swBitmap)
                        if (swBitmap !== directBitmap) swBitmap.recycle()
                        directBitmap.recycle()
                        return@withContext understanding
                    }
                } catch (_: Exception) {
                    // proceed to fallback
                } finally {
                    if (!directBitmap.isRecycled) directBitmap.recycle()
                }
            }

            // Stage 2: Coil loader fallback with generous 384x384 target bounds
            val request = ImageRequest.Builder(context)
                .data(imageUri)
                .size(coil3.size.Size(384, 384))
                .allowHardware(false)
                .build()
            val coilBitmap = (ImageLoader(context).execute(request) as? SuccessResult)
                ?.image
                ?.let { (it as? BitmapImage)?.bitmap }

            if (coilBitmap != null) {
                val swBitmap = if (coilBitmap.config == Bitmap.Config.HARDWARE) {
                    coilBitmap.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    coilBitmap
                }
                if (swBitmap != null) {
                    val understanding = ThemeGenerationEngine.analyzeImage(swBitmap)
                    if (swBitmap !== coilBitmap) swBitmap.recycle()
                    return@withContext understanding
                }
            }

            ThemeGenerationEngine.fallbackUnderstanding()
        } catch (_: Exception) {
            ThemeGenerationEngine.fallbackUnderstanding()
        }
    }
}

/**
 * Full-screen crop overlay for custom background images.
 */
@Composable
fun ImageCropScreen(
    imageUri: String,
    themeId: String,
    onConfirm: (croppedUri: String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    val screenW = remember(view) { view.resources.displayMetrics.widthPixels.toFloat() }
    val screenH = remember(view) { view.resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f) }
    val screenAspect = screenW / screenH

    var intrinsicW by remember { mutableFloatStateOf(1f) }
    var intrinsicH by remember { mutableFloatStateOf(1f) }
    var intrinsicsLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(imageUri) {
        intrinsicsLoaded = false
        withContext(Dispatchers.IO) {
            try {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                val src = Uri.parse(imageUri)
                val stream = if (src.scheme == "file") File(src.path!!).inputStream()
                else context.contentResolver.openInputStream(src)
                stream?.use { BitmapFactory.decodeStream(it, null, opts) }
                if (opts.outWidth > 0 && opts.outHeight > 0) {
                    intrinsicW = opts.outWidth.toFloat()
                    intrinsicH = opts.outHeight.toFloat()
                }
            } catch (_: Exception) {}
        }
        intrinsicsLoaded = true
    }

    val imageAspect = (intrinsicW / intrinsicH.coerceAtLeast(1f)).coerceAtLeast(0.01f)
    val initialBoxW = remember(screenAspect, intrinsicW, intrinsicH) {
        (screenAspect / imageAspect).coerceIn(0.1f, 1f)
    }
    val initialBoxH = remember(initialBoxW, screenAspect, intrinsicW, intrinsicH) {
        (initialBoxW * imageAspect / screenAspect.coerceAtLeast(0.01f)).coerceIn(0.01f, 1f)
    }

    var boxLeft by remember(initialBoxW) { mutableFloatStateOf((1f - initialBoxW) / 2f) }
    var boxTop by remember(initialBoxH) { mutableFloatStateOf((1f - initialBoxH) / 2f) }
    var boxW by remember(initialBoxW) { mutableFloatStateOf(initialBoxW) }

    fun boxH(): Float = (boxW * imageAspect / screenAspect.coerceAtLeast(0.01f)).coerceIn(0.01f, 1f)

    fun resizeBox(newW: Float, pivotX: Float = 0.5f, pivotY: Float = 0.5f) {
        val maxW = (screenAspect / imageAspect.coerceAtLeast(0.01f)).coerceIn(0.05f, 1f)
        val clampedW = newW.coerceIn(0.05f, maxW)
        val newH = (clampedW * imageAspect / screenAspect.coerceAtLeast(0.01f)).coerceIn(0.01f, 1f)
        val oldH = boxH()
        val newLeft = (boxLeft + (boxW - clampedW) * pivotX).coerceIn(0f, maxOf(0f, 1f - clampedW))
        val newTop = (boxTop + (oldH - newH) * pivotY).coerceIn(0f, maxOf(0f, 1f - newH))
        boxW = clampedW
        boxLeft = newLeft
        boxTop = newTop
    }

    var isSaving by remember { mutableStateOf(false) }

    fun imageRect(displayW: Float, displayH: Float): FloatArray {
        val imgAsp = intrinsicW / intrinsicH.coerceAtLeast(1f)
        val viewAsp = displayW / displayH.coerceAtLeast(1f)
        return if (imgAsp > viewAsp) {
            val w = displayW; val h = w / imgAsp
            floatArrayOf((displayW - w) / 2f, (displayH - h) / 2f, w, h)
        } else {
            val h = displayH; val w = h * imgAsp
            floatArrayOf((displayW - w) / 2f, (displayH - h) / 2f, w, h)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AsyncImage(
            model = imageUri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val (imgX, imgY, imgW, imgH) = imageRect(size.width, size.height)
            val cL = imgX + boxLeft * imgW
            val cT = imgY + boxTop * imgH
            val cR = imgX + (boxLeft + boxW) * imgW
            val cB = imgY + (boxTop + boxH()) * imgH

            val dim = Color(0x99000000)
            drawRect(dim, topLeft = Offset(0f, 0f), size = Size(size.width, cT))
            drawRect(dim, topLeft = Offset(0f, cB), size = Size(size.width, size.height - cB))
            drawRect(dim, topLeft = Offset(0f, cT), size = Size(cL, cB - cT))
            drawRect(dim, topLeft = Offset(cR, cT), size = Size(size.width - cR, cB - cT))

            val white = Color.White
            drawRect(
                white,
                topLeft = Offset(cL, cT),
                size = Size(cR - cL, cB - cT),
                style = Stroke(width = 2f)
            )

            val arm = 40f; val sw = 5f
            drawLine(white, Offset(cL, cT + arm), Offset(cL, cT), sw)
            drawLine(white, Offset(cL, cT), Offset(cL + arm, cT), sw)
            drawLine(white, Offset(cR - arm, cT), Offset(cR, cT), sw)
            drawLine(white, Offset(cR, cT), Offset(cR, cT + arm), sw)
            drawLine(white, Offset(cL, cB - arm), Offset(cL, cB), sw)
            drawLine(white, Offset(cL, cB), Offset(cL + arm, cB), sw)
            drawLine(white, Offset(cR - arm, cB), Offset(cR, cB), sw)
            drawLine(white, Offset(cR, cB), Offset(cR, cB - arm), sw)

            val thirdW = (cR - cL) / 3f; val thirdH = (cB - cT) / 3f
            val grid = white.copy(alpha = 0.25f)
            drawLine(grid, Offset(cL + thirdW, cT), Offset(cL + thirdW, cB), 1f)
            drawLine(grid, Offset(cL + thirdW * 2, cT), Offset(cL + thirdW * 2, cB), 1f)
            drawLine(grid, Offset(cL, cT + thirdH), Offset(cR, cT + thirdH), 1f)
            drawLine(grid, Offset(cL, cT + thirdH * 2), Offset(cR, cT + thirdH * 2), 1f)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val displayW = size.width.toFloat()
                    val displayH = size.height.toFloat()
                    val cornerHit = 52f

                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        firstDown.consume()

                        val startOffset = firstDown.position
                        val (imgX0, imgY0, imgW0, imgH0) = imageRect(displayW, displayH)
                        val cL0 = imgX0 + boxLeft * imgW0
                        val cT0 = imgY0 + boxTop * imgH0
                        val cR0 = imgX0 + (boxLeft + boxW) * imgW0
                        val cB0 = imgY0 + (boxTop + boxH()) * imgH0

                        val corner = when {
                            startOffset.x in (cL0 - cornerHit)..(cL0 + cornerHit) && startOffset.y in (cT0 - cornerHit)..(cT0 + cornerHit) -> "TL"
                            startOffset.x in (cR0 - cornerHit)..(cR0 + cornerHit) && startOffset.y in (cT0 - cornerHit)..(cT0 + cornerHit) -> "TR"
                            startOffset.x in (cL0 - cornerHit)..(cL0 + cornerHit) && startOffset.y in (cB0 - cornerHit)..(cB0 + cornerHit) -> "BL"
                            startOffset.x in (cR0 - cornerHit)..(cR0 + cornerHit) && startOffset.y in (cB0 - cornerHit)..(cB0 + cornerHit) -> "BR"
                            else -> null
                        }
                        val panHit = corner == null &&
                                startOffset.x in cL0..cR0 && startOffset.y in cT0..cB0

                        var prevCentroid = startOffset
                        var prevSpan = 0f

                        do {
                            val event = awaitPointerEvent()
                            val pointers = event.changes.filter { it.pressed }

                            if (pointers.size >= 2) {
                                val p1 = pointers[0].position
                                val p2 = pointers[1].position
                                val centroid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
                                val span = sqrt(((p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y)).toDouble()).toFloat()

                                if (prevSpan > 0f) {
                                    val zoom = (span / prevSpan.coerceAtLeast(1f)).coerceIn(0.5f, 2f)
                                    if (abs(zoom - 1f) > 0.001f) {
                                        val (imgX, imgY, imgW, imgH) = imageRect(displayW, displayH)
                                        val cx = ((centroid.x - imgX) / imgW.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                        val cy = ((centroid.y - imgY) / imgH.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                        resizeBox(boxW * zoom, pivotX = cx, pivotY = cy)
                                    }
                                }
                                prevSpan = span
                                prevCentroid = centroid
                                event.changes.forEach { it.consume() }
                            } else if (pointers.size == 1) {
                                val change = pointers[0]
                                val drag = change.position - change.previousPosition
                                val (_, _, imgW, imgH) = imageRect(displayW, displayH)
                                val dx = drag.x / imgW.coerceAtLeast(1f)
                                val dy = drag.y / imgH.coerceAtLeast(1f)

                                when (corner) {
                                    "BR" -> {
                                        val maxWbyRight = (1f - boxLeft).coerceAtLeast(0.05f)
                                        val maxWbyBottom = ((1f - boxTop) * screenAspect / imageAspect.coerceAtLeast(0.01f)).coerceAtLeast(0.05f)
                                        boxW = (boxW + dx).coerceIn(0.05f, minOf(maxWbyRight, maxWbyBottom))
                                    }
                                    "BL" -> {
                                        val rightEdge = boxLeft + boxW
                                        val maxWbyLeft = rightEdge.coerceAtLeast(0.05f)
                                        val maxWbyBottom = ((1f - boxTop) * screenAspect / imageAspect.coerceAtLeast(0.01f)).coerceAtLeast(0.05f)
                                        val newW = (boxW - dx).coerceIn(0.05f, minOf(maxWbyLeft, maxWbyBottom))
                                        boxW = newW
                                        boxLeft = (rightEdge - newW).coerceIn(0f, maxOf(0f, 1f - newW))
                                    }
                                    "TR" -> {
                                        val bottomEdge = boxTop + boxH()
                                        val maxWbyRight = (1f - boxLeft).coerceAtLeast(0.05f)
                                        val maxWbyTop = (bottomEdge * screenAspect / imageAspect.coerceAtLeast(0.01f)).coerceAtLeast(0.05f)
                                        val newW = (boxW + dx).coerceIn(0.05f, minOf(maxWbyRight, maxWbyTop))
                                        val newH = (newW * imageAspect / screenAspect.coerceAtLeast(0.01f)).coerceIn(0.01f, 1f)
                                        boxW = newW
                                        boxTop = (bottomEdge - newH).coerceIn(0f, maxOf(0f, 1f - newH))
                                    }
                                    "TL" -> {
                                        val rightEdge = boxLeft + boxW
                                        val bottomEdge = boxTop + boxH()
                                        val maxWbyLeft = rightEdge.coerceAtLeast(0.05f)
                                        val maxWbyTop = (bottomEdge * screenAspect / imageAspect.coerceAtLeast(0.01f)).coerceAtLeast(0.05f)
                                        val newW = (boxW - dx).coerceIn(0.05f, minOf(maxWbyLeft, maxWbyTop))
                                        val newH = (newW * imageAspect / screenAspect.coerceAtLeast(0.01f)).coerceIn(0.01f, 1f)
                                        boxLeft = (rightEdge - newW).coerceIn(0f, maxOf(0f, 1f - newW))
                                        boxTop = (bottomEdge - newH).coerceIn(0f, maxOf(0f, 1f - newH))
                                        boxW = newW
                                    }
                                    null -> if (panHit) {
                                        boxLeft = (boxLeft + dx).coerceIn(0f, maxOf(0f, 1f - boxW))
                                        boxTop = (boxTop + dy).coerceIn(0f, maxOf(0f, 1f - boxH()))
                                    }
                                }
                                change.consume()
                                prevSpan = 0f
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        )

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.55f))
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel", tint = Color.White)
            }
            Text(
                "Crop Background",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                modifier = Modifier.weight(1f)
            )
        }

        // Bottom bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.55f))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Drag to move · pinch or corners to resize",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (!isSaving) {
                        isSaving = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                try {
                                    val src = Uri.parse(imageUri)
                                    val full: Bitmap? = if (src.scheme == "file") {
                                        val f = File(src.path!!)
                                        if (!f.exists()) null else f.inputStream().use { BitmapFactory.decodeStream(it) }
                                    } else {
                                        context.contentResolver.openInputStream(src)?.use {
                                            BitmapFactory.decodeStream(it)
                                        }
                                    }

                                    if (full == null) return@withContext null

                                    val fw = full.width.toFloat()
                                    val fh = full.height.toFloat()

                                    val bx = (boxLeft * fw).toInt().coerceIn(0, full.width - 1)
                                    val by = (boxTop * fh).toInt().coerceIn(0, full.height - 1)
                                    val bw = (boxW * fw).toInt().coerceIn(1, full.width - bx)
                                    val bh = (boxH() * fh).toInt().coerceIn(1, full.height - by)

                                    val cropped = Bitmap.createBitmap(full, bx, by, bw, bh)
                                    full.recycle()

                                    val targetW = screenW.toInt().coerceAtLeast(1)
                                    val targetH = screenH.toInt().coerceAtLeast(1)
                                    val scaled = Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
                                    if (scaled !== cropped) cropped.recycle()

                                    val dir = File(context.filesDir, "bg_images/$themeId").also { it.mkdirs() }
                                    val dest = File(dir, "crop_${System.currentTimeMillis()}.jpg")
                                    dest.outputStream().use { out ->
                                        scaled.compress(Bitmap.CompressFormat.JPEG, 92, out)
                                    }
                                    scaled.recycle()
                                    dir.listFiles { f ->
                                        f.name != dest.name &&
                                                (f.name == "crop.jpg" ||
                                                        (f.name.startsWith("crop_") && f.name.endsWith(".jpg")))
                                    }?.forEach { it.delete() }
                                    Uri.fromFile(dest).toString()
                                } catch (e: Exception) {
                                    "ERROR:${e.javaClass.simpleName}: ${e.message}"
                                }
                            }
                            isSaving = false
                            if (result != null && !result.startsWith("ERROR:")) {
                                onConfirm(result)
                            } else {
                                val msg = if (result != null) result.removePrefix("ERROR:") else "decodeStream returned null"
                                Toast.makeText(context, "Crop failed: $msg", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                enabled = !isSaving && intrinsicsLoaded
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Saving...")
                } else if (!intrinsicsLoaded) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Loading...")
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Use this crop")
                }
            }
        }
    }
}
