package com.example.centerfinder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.centerfinder.vision.CenterFinderAnalyzer
import com.example.centerfinder.vision.DetectionResult
import com.example.centerfinder.vision.ShapeKind
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { setContent { CenterFinderApp(it) } }
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) setContent { CenterFinderApp(true) } else permission.launch(Manifest.permission.CAMERA)
    }
}

@Composable
private fun CenterFinderApp(cameraGranted: Boolean) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        if (cameraGranted) CameraScreen() else PermissionScreen()
    }
}

@Composable
private fun PermissionScreen() = Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
    Text("Camera permission is needed to find object centers.", color = Color.White)
}

@Composable
private fun CameraScreen() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var detection by remember { mutableStateOf<DetectionResult?>(null) }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(surfaceProvider) }
                    val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).setOutputImageRotationEnabled(true).build().also {
                        it.setAnalyzer(Executors.newSingleThreadExecutor(), CenterFinderAnalyzer { result -> post { detection = result } })
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }, ContextCompat.getMainExecutor(ctx))
            }
        }, modifier = Modifier.fillMaxSize())
        Overlay(detection)
        Text("Point the camera at an object", color = Color.White, modifier = Modifier.align(Alignment.TopCenter).padding(top = 34.dp).background(Color.Black.copy(alpha=.35f), RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 8.dp))
        InfoCard(detection, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun Overlay(result: DetectionResult?) = Canvas(Modifier.fillMaxSize()) {
    val sx: Float; val sy: Float; val dx: Float; val dy: Float
    if (result != null) {
        val scale = maxOf(size.width / result.frameWidth, size.height / result.frameHeight)
        sx = scale; sy = scale; dx = (size.width - result.frameWidth * scale) / 2f; dy = (size.height - result.frameHeight * scale) / 2f
        fun map(p: Offset) = Offset(p.x * sx + dx, p.y * sy + dy)
        val outlineColor = when (result.kind) { ShapeKind.Square, ShapeKind.Rectangle -> Color.Green; ShapeKind.Circle, ShapeKind.Oval -> Color.Cyan; ShapeKind.Hexagon, ShapeKind.Honeycomb -> Color(0xffffb300); ShapeKind.Object -> Color(0xff76ff03) }
        fun drawPoly(points: List<Offset>, color: Color, width: Float) { if (points.size > 1) { val path = Path().apply { moveTo(map(points[0]).x, map(points[0]).y); points.drop(1).forEach { lineTo(map(it).x, map(it).y) }; close() }; drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round)) } }
        drawPoly(result.outline, outlineColor, 6f)
        result.cells.forEach { drawPoly(it.polygon, Color(0xffffc107), 3f); drawCircle(Color.Red, 4f, map(it.center)) }
        val c = map(result.center); drawLine(Color.Red, Offset(c.x - 42, c.y), Offset(c.x + 42, c.y), 5f); drawLine(Color.Red, Offset(c.x, c.y - 42), Offset(c.x, c.y + 42), 5f); drawCircle(Color.Red, 8f, c)
    }
    val r = Offset(size.width/2, size.height/2); drawLine(Color.White.copy(alpha=.65f), Offset(r.x-28,r.y), Offset(r.x+28,r.y), 2f); drawLine(Color.White.copy(alpha=.65f), Offset(r.x,r.y-28), Offset(r.x,r.y+28), 2f)
}

@Composable
private fun InfoCard(result: DetectionResult?, modifier: Modifier) = Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = .68f))) {
    Column(Modifier.padding(16.dp)) {
        Text(result?.kind?.label ?: "Looking for a shape…", color = Color.White, style = MaterialTheme.typography.titleLarge)
        result?.let { Text("Confidence ${(it.confidence * 100).toInt()}% • Center ${it.center.x.toInt()}, ${it.center.y.toInt()}" + if (it.kind == ShapeKind.Honeycomb) " • ${it.cells.size} cells" else "", color = Color.White.copy(alpha=.85f)) }
    }
}
