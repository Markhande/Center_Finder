package com.satnamapps.shapecenterfinder

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect as CvRect
import java.util.concurrent.Executors
import kotlin.math.*

@Composable
fun ObjectCounterScreen(initialPhoto: Bitmap? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val holder = remember { CounterFrameHolder() }
    val scope = rememberCoroutineScope()
    var active by remember { mutableStateOf(true) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewCase by remember { mutableStateOf<Preview?>(null) }
    var analysisCase by remember { mutableStateOf<ImageAnalysis?>(null) }
    var ready by remember { mutableStateOf(false) }
    var captured by remember { mutableStateOf(initialPhoto) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ClusterCounter.CountResult?>(null) }
    var resultRoi by remember { mutableStateOf<CvRect?>(null) }
    var scene by remember { mutableStateOf<VisualScene?>(null) }
    var exampleId by remember { mutableStateOf<Int?>(null) }
    var matchColor by remember { mutableStateOf(false) }
    var threshold by remember { mutableStateOf(0.78f) }
    var correctMode by remember { mutableStateOf(false) }
    var excluded by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var added by remember { mutableStateOf<List<Offset>>(emptyList()) }
    fun clearCorrections() { excluded = emptySet(); added = emptyList() }
    var selectedGroup by remember { mutableStateOf<Int?>(null) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    // Selection is normalized to the captured image, so rotation/layout changes cannot move it.
    var selection by remember { mutableStateOf<Rect?>(null) }

    fun count(bitmap: Bitmap) {
        busy = true
        result = null
        scene = null
        exampleId = null
        correctMode = false
        clearCorrections()
        selectedGroup = null
        message = null
        val region = selection
        scope.launch {
            try {
                val pair = withContext(Dispatchers.Default) {
                    val source = Mat()
                    try {
                        Utils.bitmapToMat(bitmap, source)
                        val left = ((region?.left ?: 0f) * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                        val top = ((region?.top ?: 0f) * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                        val right = ((region?.right ?: 1f) * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
                        val bottom = ((region?.bottom ?: 1f) * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
                        val roi = CvRect(left, top, right - left, bottom - top)
                        val detection = ClusterCounter.count(source, roi)
                        Triple(detection, roi, VisualCounter.analyze(context.applicationContext, bitmap, roi, detection))
                    } finally { source.release() }
                }
                result = pair.first
                resultRoi = pair.second
                scene = pair.third
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e("ObjectCounter", "Counting failed", e)
                message = "Couldn’t analyze this photo. Try again with a clearer background."
            } finally { busy = false }
        }
    }

    LaunchedEffect(initialPhoto) { initialPhoto?.let { count(it) } }

    DisposableEffect(lifecycleOwner) {
        active = true
        onDispose {
            active = false
            analysisCase?.clearAnalyzer()
            previewCase?.let { provider?.unbind(it) }
            analysisCase?.let { provider?.unbind(it) }
            holder.close()
            executor.shutdown()
        }
    }

    val palette = remember { listOf(Color(0xFF69F0AE), Color(0xFFFFD740), Color(0xFF40C4FF), Color(0xFFFF80AB), Color(0xFFB388FF)) }
        val photo = captured
        val groups = remember(scene, matchColor) { scene?.let { VisualSimilarity.groups(it.items, matchColor) } ?: emptyList() }
        val visibleIds = remember(scene, groups, selectedGroup, exampleId, matchColor, threshold) {
            val currentScene = scene
            when {
                currentScene == null -> emptyList()
                exampleId != null -> currentScene.matching(exampleId!!, matchColor, threshold.toDouble())
                selectedGroup != null -> groups.getOrNull(selectedGroup!!)?.map { currentScene.items[it].id } ?: emptyList()
                else -> currentScene.items.map { it.id }
            }
        }
        val displayedCount = visibleIds.count { it !in excluded } + added.size
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(12.dp)
            .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(12.dp)).padding(12.dp)) {
            Text(if (photo == null) "Capture items to count" else "Review detected items", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text(message ?: if (photo == null) "Keep all items visible. A plain, contrasting background works best."
                else if (correctMode) "Tap a box to remove or restore it. Tap a missed item to add it."
                else "Tap a detected object to count similar items. Drag to select a smaller area.", color = Color.White)
            result?.let { found ->
                Text(if (excluded.isNotEmpty() || added.isNotEmpty()) "Reviewed count: $displayedCount"
                    else if (exampleId != null) "$displayedCount similar items"
                    else if (selectedGroup != null) "$displayedCount items in this group"
                    else "${found.count} items • ${groups.size} ${if (groups.size == 1) "group" else "groups"}", color = Color.White, style = MaterialTheme.typography.titleMedium)
                if (found.count == 0) Text("No clear objects found. Try a tighter area or a clearer background.", color = Color.White)
                if (found.excludedAtBorder > 0) Text("${found.excludedAtBorder} outlines touch the edge and were excluded.", color = Color.White)
                if (found.possibleMergedObjects > 0) Text("Some outlines may contain touching items. Separate them and retake for a better count.", color = Color.White)
                scene?.warnings?.forEach { Text(it, color = Color(0xFFFFD740), style = MaterialTheme.typography.bodySmall) }
                Text("Estimated count — check the marked items.", color = Color.White)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = matchColor, enabled = !busy, onClick = {
                        matchColor = !matchColor; selectedGroup = null; clearCorrections()
                    }, label = { Text("Match color") })
                    FilterChip(selected = correctMode, enabled = !busy, onClick = { correctMode = !correctMode }, label = { Text("Correct count") })
                    if (excluded.isNotEmpty() || added.isNotEmpty()) TextButton(onClick = { clearCorrections() }) { Text("Reset edits") }
                }
                if (exampleId != null && scene?.appearanceAvailable == true) {
                    Text("Match strength: broader ← → closer", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    Slider(value = threshold, onValueChange = { threshold = it; clearCorrections() }, valueRange = 0.65f..0.95f)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = selectedGroup == null && exampleId == null, onClick = {
                        selectedGroup = null; exampleId = null; correctMode = false; clearCorrections(); message = null
                    }, label = { Text("All") })
                    groups.forEachIndexed { index, group ->
                        FilterChip(selected = selectedGroup == index && exampleId == null, onClick = {
                            selectedGroup = index; exampleId = null; clearCorrections(); message = null
                        }, label = { Text("Group ${index + 1}: ${group.size}") },
                            leadingIcon = { Canvas(Modifier.size(10.dp)) { drawCircle(palette[index % palette.size]) } })
                    }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { viewSize = it }) {
        if (initialPhoto == null) AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FIT_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                val view = this
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    if (active) {
                        try {
                            val cameraProvider = future.get()
                            val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                                    ResolutionStrategy(android.util.Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                                ).build()).build()
                            analysis.setAnalyzer(executor) { image ->
                                try {
                                    val plane = image.planes[0]
                                    val buffer = plane.buffer.duplicate()
                                    val origin = buffer.position()
                                    val bytes = ByteArray(image.width * image.height * 4)
                                    for (y in 0 until image.height) {
                                        buffer.position(origin + y * plane.rowStride)
                                        buffer.get(bytes, y * image.width * 4, image.width * 4)
                                    }
                                    val raw = Mat(image.height, image.width, CvType.CV_8UC4)
                                    val upright = Mat()
                                    try {
                                        raw.put(0, 0, bytes)
                                        when (image.imageInfo.rotationDegrees) {
                                            90 -> Core.rotate(raw, upright, Core.ROTATE_90_CLOCKWISE)
                                            180 -> Core.rotate(raw, upright, Core.ROTATE_180)
                                            270 -> Core.rotate(raw, upright, Core.ROTATE_90_COUNTERCLOCKWISE)
                                            else -> raw.copyTo(upright)
                                        }
                                        holder.update(upright)
                                        mainExecutor.execute { if (active) ready = true }
                                    } finally { raw.release(); upright.release() }
                                } catch (e: Exception) { Log.w("ObjectCounter", "Frame unavailable", e) }
                                finally { image.close() }
                            }
                            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                            provider = cameraProvider; previewCase = preview; analysisCase = analysis
                        } catch (e: Exception) {
                            Log.e("ObjectCounter", "Camera unavailable", e)
                            message = "Camera unavailable. Return and reopen the counter."
                        }
                    }
                }, mainExecutor)
            }
        })

        if (photo != null) {
            Image(photo.asImageBitmap(), contentDescription = "Captured image for counting",
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().background(Color.Black))
            val mapping = CounterImageMapping(viewSize.width, viewSize.height, photo.width, photo.height)
            Canvas(Modifier.fillMaxSize().testTag("counter-photo-overlay")
                .pointerInput(photo, viewSize, busy, scene, visibleIds, correctMode, excluded, added) {
                    if (!busy) detectTapGestures { point ->
                        val normalized = mapping.fromScreen(point)
                        val currentScene = scene
                        val roi = resultRoi
                        if (normalized != null && currentScene != null && roi != null) {
                            val x = normalized.x * photo.width - roi.x
                            val y = normalized.y * photo.height - roi.y
                            if (x >= 0 && y >= 0 && x < roi.width && y < roi.height) {
                                if (correctMode) {
                                    val manualHit = added.indexOfFirst { hypot(it.x - x, it.y - y) * mapping.scale < 24f }
                                    val hit = currentScene.hit(x, y, visibleIds.toSet())
                                    when {
                                        manualHit >= 0 -> added = added.filterIndexed { i, _ -> i != manualHit }
                                        hit != null -> excluded = if (hit in excluded) excluded - hit else excluded + hit
                                        else -> added = added + Offset(x, y)
                                    }
                                } else {
                                    val hit = currentScene.hit(x, y)
                                    if (hit != null) {
                                        exampleId = hit; selectedGroup = null; clearCorrections(); message = null
                                    } else message = "Tap inside a detected box to choose an example. Use Correct count to add a missed item."
                                }
                            }
                        }
                    }
                }.pointerInput(photo, viewSize, busy, correctMode) {
                if (!busy && !correctMode) {
                    var anchor: Offset? = null
                    detectDragGestures(
                        onDragStart = { point ->
                            anchor = mapping.fromScreen(point)
                            if (anchor != null) { selection = null; result = null; scene = null; exampleId = null; selectedGroup = null; clearCorrections() }
                        },
                        onDrag = { change, _ ->
                            anchor?.let { start ->
                                change.consume()
                                val end = mapping.fromScreen(change.position, clamp = true) ?: start
                                selection = Rect(min(start.x, end.x), min(start.y, end.y), max(start.x, end.x), max(start.y, end.y))
                            }
                        },
                        onDragEnd = {
                            selection?.let { if (it.width * photo.width < 16 || it.height * photo.height < 16) selection = null }
                            anchor = null
                        },
                        onDragCancel = { anchor = null; selection = null }
                    )
                }
            }) {
                selection?.let { rect ->
                    val topLeft = mapping.toScreen(rect.left * photo.width, rect.top * photo.height)
                    drawRect(Color.White, topLeft, Size(rect.width * photo.width * mapping.scale, rect.height * photo.height * mapping.scale), style = Stroke(3f))
                }
                val roi = resultRoi
                if (roi != null) {
                    scene?.items?.filter { it.id in visibleIds }?.forEach { visual ->
                        val item = visual.item
                        val groupIndex = groups.indexOfFirst { indices -> indices.any { scene!!.items[it].id == visual.id } }.coerceAtLeast(0)
                        val color = if (visual.id in excluded) Color.Gray else palette[groupIndex % palette.size]
                        drawRect(color, mapping.toScreen(roi.x + item.left, roi.y + item.top),
                            Size(item.width * mapping.scale, item.height * mapping.scale), style = Stroke(if (visual.id == exampleId) 7f else 3f))
                        if (visual.id !in excluded) drawCircle(color, 5f, mapping.toScreen(roi.x + item.x, roi.y + item.y))
                    }
                    added.forEach { point ->
                        drawCircle(Color.White, 10f, mapping.toScreen(roi.x + point.x, roi.y + point.y), style = Stroke(3f))
                    }
                }
            }
        }

        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            if (photo == null) {
                Button(enabled = ready && !busy, onClick = {
                    val frame = holder.snapshot()
                    if (frame == null) message = "Waiting for a camera frame…"
                    else {
                        captured = frame; selection = null
                        count(frame)
                    }
                }) { Text("Capture & count") }
            } else {
                OutlinedButton(enabled = !busy, onClick = {
                    captured = null; selection = null; result = null; resultRoi = null; scene = null
                    exampleId = null; correctMode = false; clearCorrections(); selectedGroup = null; message = null
                }) { Text("Retake", color = Color.White) }
                Button(enabled = !busy, onClick = { count(photo) }) { Text(if (busy) "Counting…" else if (selection == null) "Recount" else "Count area") }
                if (selection != null) TextButton(enabled = !busy, onClick = { selection = null; count(photo) }) { Text("Full image") }
            }
        }
    }
}

/** Immutable captured bitmap is distinct from the continually updated preview buffer. */
private class CounterFrameHolder {
    private var frame: Mat? = null
    private var closed = false
    @Synchronized fun update(mat: Mat) {
        if (closed) return
        frame?.release()
        frame = mat.clone()
    }
    @Synchronized fun snapshot(): Bitmap? = frame?.let {
        Bitmap.createBitmap(it.cols(), it.rows(), Bitmap.Config.ARGB_8888).also { bitmap -> Utils.matToBitmap(it, bitmap) }
    }
    @Synchronized fun close() { closed = true; frame?.release(); frame = null }
}

internal class CounterImageMapping(viewW: Int, viewH: Int, private val imageW: Int, private val imageH: Int) {
    val scale = min(viewW.toFloat() / imageW, viewH.toFloat() / imageH).coerceAtLeast(0.0001f)
    private val dx = (viewW - imageW * scale) / 2f
    private val dy = (viewH - imageH * scale) / 2f
    fun toScreen(x: Float, y: Float) = Offset(dx + x * scale, dy + y * scale)
    fun fromScreen(point: Offset, clamp: Boolean = false): Offset? {
        val x = (point.x - dx) / (imageW * scale)
        val y = (point.y - dy) / (imageH * scale)
        if (!clamp && (x !in 0f..1f || y !in 0f..1f)) return null
        return Offset(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
    }
}
