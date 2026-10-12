package com.satnamapps.shapecenterfinder

import org.opencv.core.*
import org.opencv.geometry.Geometry
import org.opencv.imgproc.Imgproc
import kotlin.math.*

/** Counts visible silhouettes against the dominant border color, then groups similar outlines. */
object ClusterCounter {
    data class Item(val x: Float, val y: Float, val left: Float, val top: Float,
                    val width: Float, val height: Float, val features: ObjectFeatures? = null)
    data class Group(val id: Int, val items: List<Item>)
    data class CountResult(val groups: List<Group>, val roiWidth: Int, val roiHeight: Int,
                           val excludedAtBorder: Int, val possibleMergedObjects: Int) {
        val count get() = groups.sumOf { it.items.size }
    }
    private data class Candidate(val item: Item, val features: ObjectFeatures)

    fun count(source: Mat, roi: Rect): CountResult {
        require(!source.empty() && source.channels() == 4)
        val x = roi.x.coerceIn(0, source.cols() - 1)
        val y = roi.y.coerceIn(0, source.rows() - 1)
        val safe = Rect(x, y, roi.width.coerceIn(1, source.cols() - x), roi.height.coerceIn(1, source.rows() - y))
        val owned = mutableListOf<Mat>()
        fun mat() = Mat().also { owned.add(it) }
        val contours = mutableListOf<MatOfPoint>()
        try {
            val cropped = Mat(source, safe).also { owned.add(it) }
            val rgb = mat()
            Imgproc.cvtColor(cropped, rgb, Imgproc.COLOR_RGBA2RGB)
            val smooth = mat()
            Imgproc.GaussianBlur(rgb, smooth, Size(5.0, 5.0), 0.0)
            val lab = mat()
            Imgproc.cvtColor(smooth, lab, Imgproc.COLOR_RGB2Lab)
            val mask = foregroundMask(lab).also { owned.add(it) }
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0)).also { owned.add(it) }
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel)
            Imgproc.findContours(mask, contours, mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            val candidates = mutableListOf<Candidate>()
            var excluded = 0
            var merged = 0
            val minArea = max(30.0, safe.area() * 0.00008)
            for (contour in contours) {
                val area = Geometry.contourArea(contour)
                if (area < minArea) continue
                val bounds = Geometry.boundingRect(contour)
                if (bounds.x <= 1 || bounds.y <= 1 || bounds.x + bounds.width >= safe.width - 1 ||
                    bounds.y + bounds.height >= safe.height - 1) { excluded++; continue }
                val candidate = describe(contour) ?: continue
                // Split only silhouettes supported by multiple round lobes. Long items are one item.
                val split = if (candidate.features.circularity < 0.78 && candidate.features.aspect < 3.5)
                    splitRoundObjects(contour, bounds, area) else emptyList()
                if (split.size > 1) candidates.addAll(split)
                else {
                    candidates.add(candidate)
                    if (candidate.features.extent < 0.60) merged++
                }
            }
            val groups = ObjectGrouping.group(candidates.map { it.features }).mapIndexed { index, indices ->
                Group(index + 1, indices.map { candidates[it].item.copy(features = candidates[it].features) })
            }
            return CountResult(groups, safe.width, safe.height, excluded, merged)
        } finally {
            contours.forEach { it.release() }
            owned.forEach { it.release() }
        }
    }

    private fun foregroundMask(lab: Mat): Mat {
        val w = lab.cols(); val h = lab.rows()
        val pixels = ByteArray(w * h * 3)
        lab.get(0, 0, pixels)
        fun channel(index: Int, c: Int) = pixels[index * 3 + c].toInt() and 255
        fun bin(i: Int) = (channel(i, 0) / 24) * 121 + (channel(i, 1) / 24) * 11 + channel(i, 2) / 24
        val border = mutableListOf<Int>()
        val band = max(1, min(w, h) / 30)
        for (y in 0 until h step 2) for (x in 0 until w step 2) {
            if (x < band || y < band || x >= w - band || y >= h - band) border.add(y * w + x)
        }
        val dominant = border.groupingBy { bin(it) }.eachCount().maxByOrNull { it.value }!!.key
        val samples = border.filter { bin(it) == dominant }
        val background = DoubleArray(3) { c -> samples.map { channel(it, c) }.average() }
        val distances = ByteArray(w * h) { i ->
            sqrt((0..2).sumOf { c -> (channel(i, c) - background[c]).pow(2) }).toInt().coerceIn(0, 255).toByte()
        }
        val difference = Mat(h, w, CvType.CV_8UC1)
        val binary = Mat()
        try {
            difference.put(0, 0, distances)
            val threshold = Imgproc.threshold(difference, binary, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
            // Avoid treating sensor noise in an empty scene as objects.
            Imgproc.threshold(difference, binary, max(18.0, threshold), 255.0, Imgproc.THRESH_BINARY)
            return binary
        } catch (e: Exception) { binary.release(); throw e }
        finally { difference.release() }
    }

    private fun describe(contour: MatOfPoint): Candidate? {
        val points = MatOfPoint2f(*contour.toArray())
        try {
            val area = Geometry.contourArea(contour)
            val m = Geometry.moments(contour)
            if (m.m00 <= 0.0) return null
            val box = Geometry.minAreaRect(points)
            val bounds = Geometry.boundingRect(contour)
            val features = ObjectFeatures(area, ShapeMath.aspect(box.size.width, box.size.height),
                ShapeMath.circularity(area, Geometry.arcLength(points, true)),
                area / max(1.0, box.size.area()))
            return Candidate(Item((m.m10 / m.m00).toFloat(), (m.m01 / m.m00).toFloat(),
                bounds.x.toFloat(), bounds.y.toFloat(), bounds.width.toFloat(), bounds.height.toFloat()), features)
        } finally { points.release() }
    }

    private fun splitRoundObjects(contour: MatOfPoint, bounds: Rect, area: Double): List<Candidate> {
        val mask = Mat.zeros(bounds.height + 4, bounds.width + 4, CvType.CV_8UC1)
        val shifted = MatOfPoint(*contour.toArray().map { Point(it.x - bounds.x + 2, it.y - bounds.y + 2) }.toTypedArray())
        val distance = Mat(); val peaks = Mat(); val peaks8 = Mat(); val labels = Mat(); val stats = Mat(); val centers = Mat()
        try {
            Imgproc.drawContours(mask, listOf(shifted), -1, Scalar(255.0), -1)
            Imgproc.distanceTransform(mask, distance, Geometry.DIST_L2, 5)
            val radius = Core.minMaxLoc(distance).maxVal
            if (radius < 5.0) return emptyList()
            Imgproc.threshold(distance, peaks, radius * 0.72, 255.0, Imgproc.THRESH_BINARY)
            peaks.convertTo(peaks8, CvType.CV_8U)
            val n = Imgproc.connectedComponentsWithStats(peaks8, labels, stats, centers)
            val lobes = (1 until n).filter { stats.get(it, Imgproc.CC_STAT_AREA)[0] >= 5.0 }.map { label ->
                Point(centers.get(label, 0)[0], centers.get(label, 1)[0])
            }
            if (lobes.size < 2 || lobes.size > 100) return emptyList()
            // Require circle coverage and separation evidence rather than counting every peak.
            val coverage = area / (lobes.size * Math.PI * radius * radius)
            if (coverage !in 0.75..1.25) return emptyList()
            if (lobes.indices.any { i -> (i + 1 until lobes.size).any { j ->
                    hypot(lobes[i].x - lobes[j].x, lobes[i].y - lobes[j].y) < radius * 1.35
                } }) return emptyList()
            return lobes.map { p ->
                val cx = (p.x + bounds.x - 2).toFloat(); val cy = (p.y + bounds.y - 2).toFloat()
                val r = radius.toFloat()
                Candidate(Item(cx, cy, cx - r, cy - r, r * 2, r * 2),
                    ObjectFeatures(Math.PI * radius * radius, 1.0, 0.90, Math.PI / 4))
            }
        } finally { listOf(mask, shifted, distance, peaks, peaks8, labels, stats, centers).forEach { it.release() } }
    }
}
