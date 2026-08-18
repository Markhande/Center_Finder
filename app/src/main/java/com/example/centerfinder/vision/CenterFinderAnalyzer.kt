package com.example.centerfinder.vision

import android.graphics.ImageFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Offset
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.*

class CenterFinderAnalyzer(private val onResult: (DetectionResult?) -> Unit) : ImageAnalysis.Analyzer {
    private var last: DetectionResult? = null
    private var missCount = 0
    init { OpenCVLoader.initLocal() }

    override fun analyze(image: ImageProxy) {
        try {
            val mat = image.toGrayMat() ?: return
            val rotated = mat.rotateFor(image.imageInfo.rotationDegrees)
            val result = detect(rotated)
            if (result != null) { missCount = 0; last = smooth(last, result); onResult(last) }
            else { missCount++; onResult(if (missCount < 8) last else null) }
            mat.release(); if (rotated !== mat) rotated.release()
        } finally { image.close() }
    }

    private fun detect(gray: Mat): DetectionResult? {
        val w = gray.cols(); val h = gray.rows(); val centerFrame = Point(w / 2.0, h / 2.0)
        val normalized = Mat(); Imgproc.GaussianBlur(gray, normalized, Size(5.0, 5.0), 0.0)
        Imgproc.createCLAHE(2.0, Size(8.0, 8.0)).apply(normalized, normalized)
        val masks = mutableListOf<Mat>()
        val edges = Mat(); Imgproc.Canny(normalized, edges, 45.0, 135.0); masks += edges
        for (type in listOf(Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)) {
            val m = Mat(); Imgproc.threshold(normalized, m, 0.0, 255.0, type); masks += m
        }
        val adaptive = Mat(); Imgproc.adaptiveThreshold(normalized, adaptive, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 41, 4.0); masks += adaptive
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(7.0, 7.0))
        val candidates = mutableListOf<Candidate>()
        masks.forEach { mask ->
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
            val contours = mutableListOf<MatOfPoint>(); Imgproc.findContours(mask, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            contours.mapNotNullTo(candidates) { it.toCandidate(w, h, centerFrame) }
        }
        masks.forEach { it.release() }; normalized.release(); kernel.release()
        val chosen = candidates.maxByOrNull { it.score } ?: return null
        val cells = detectHoneycomb(candidates, chosen, w, h)
        return if (cells.size >= 3) honeycombResult(cells, chosen, w, h) else chosen.toResult(w, h)
    }

    private fun MatOfPoint.toCandidate(w: Int, h: Int, reticle: Point): Candidate? {
        val area = abs(Imgproc.contourArea(this)); if (area < w * h * 0.006 || area > w * h * 0.82) return null
        val rect = Imgproc.boundingRect(this); val edgeTouches = listOf(rect.x <= 2, rect.y <= 2, rect.x + rect.width >= w - 2, rect.y + rect.height >= h - 2).count { it }
        if (edgeTouches >= 3) return null
        val m = Imgproc.moments(this); if (m.m00 == 0.0) return null
        val centroid = Point(m.m10 / m.m00, m.m01 / m.m00)
        val peri = Imgproc.arcLength(MatOfPoint2f(*toArray()), true)
        val approx = MatOfPoint2f(); Imgproc.approxPolyDP(MatOfPoint2f(*toArray()), approx, 0.025 * peri, true)
        val pts = approx.toArray().map { Offset(it.x.toFloat(), it.y.toFloat()) }
        val dist = hypot(centroid.x - reticle.x, centroid.y - reticle.y) / hypot(w.toDouble(), h.toDouble())
        val circularity = (4.0 * Math.PI * area / (peri * peri)).coerceIn(0.0, 1.0)
        val ellipse = if (rows() >= 5) Imgproc.fitEllipse(MatOfPoint2f(*toArray())) else null
        val kind = classify(pts, rect, circularity, ellipse)
        val score = (area / (w * h) * 1.2 + (1.0 - dist) * 2.0 + if (rect.contains(reticle)) 1.0 else 0.0).toFloat()
        return Candidate(kind, pts.ifEmpty { listOf(Offset(rect.x.toFloat(), rect.y.toFloat()), Offset((rect.x+rect.width).toFloat(), rect.y.toFloat()), Offset((rect.x+rect.width).toFloat(), (rect.y+rect.height).toFloat()), Offset(rect.x.toFloat(), (rect.y+rect.height).toFloat())) }, Offset(centroid.x.toFloat(), centroid.y.toFloat()), circularity.toFloat(), score, area)
    }

    private fun classify(pts: List<Offset>, rect: Rect, circularity: Double, ellipse: RotatedRect?): ShapeKind {
        if (pts.size == 6) return ShapeKind.Hexagon
        if (pts.size == 4) { val ratio = max(rect.width, rect.height).toDouble() / max(1, min(rect.width, rect.height)); return if (ratio < 1.16) ShapeKind.Square else ShapeKind.Rectangle }
        if (ellipse != null && circularity > 0.70) { val ratio = max(ellipse.size.width, ellipse.size.height) / max(1.0, min(ellipse.size.width, ellipse.size.height)); return if (ratio < 1.18) ShapeKind.Circle else ShapeKind.Oval }
        return ShapeKind.Object
    }

    private fun detectHoneycomb(cands: List<Candidate>, chosen: Candidate, w: Int, h: Int) = cands.filter { it.kind == ShapeKind.Hexagon && it.area > w*h*0.002 && hypot((it.center.x-chosen.center.x).toDouble(), (it.center.y-chosen.center.y).toDouble()) < min(w,h)*0.45 }.distinctBy { (it.center.x/12).roundToInt() to (it.center.y/12).roundToInt() }
    private fun honeycombResult(cells: List<Candidate>, chosen: Candidate, w: Int, h: Int): DetectionResult { val c = Offset(cells.map { it.center.x }.average().toFloat(), cells.map { it.center.y }.average().toFloat()); return DetectionResult(ShapeKind.Honeycomb, chosen.outline, c, .86f, w, h, cells.map { CellOverlay(it.outline, it.center) }) }
    private fun Candidate.toResult(w: Int, h: Int) = DetectionResult(kind, outline, center, confidence, w, h)
    private fun smooth(old: DetectionResult?, new: DetectionResult): DetectionResult { if (old == null || old.frameWidth != new.frameWidth || old.frameHeight != new.frameHeight) return new; fun mix(a: Offset,b: Offset)=Offset(a.x*.65f+b.x*.35f,a.y*.65f+b.y*.35f); return new.copy(center = mix(old.center,new.center), outline = new.outline.mapIndexed { i,p -> old.outline.getOrNull(i)?.let { mix(it,p) } ?: p }) }

    private data class Candidate(val kind: ShapeKind, val outline: List<Offset>, val center: Offset, val confidence: Float, val score: Float, val area: Double)
}

private fun ImageProxy.toGrayMat(): Mat? {
    if (format != ImageFormat.YUV_420_888) return null
    val y = planes[0].buffer; val bytes = ByteArray(y.remaining()); y.get(bytes)
    return Mat(height, width, CvType.CV_8UC1).apply { put(0, 0, bytes) }
}
private fun Mat.rotateFor(degrees: Int): Mat = when (degrees) { 90 -> Mat().also { Core.rotate(this, it, Core.ROTATE_90_CLOCKWISE) }; 180 -> Mat().also { Core.rotate(this, it, Core.ROTATE_180) }; 270 -> Mat().also { Core.rotate(this, it, Core.ROTATE_90_COUNTERCLOCKWISE) }; else -> this }
