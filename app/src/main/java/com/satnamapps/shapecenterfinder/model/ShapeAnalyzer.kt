package com.satnamapps.shapecenterfinder.model

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.geometry.Geometry
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.sqrt
import com.satnamapps.shapecenterfinder.ShapeMath

/**
 * CameraX analyzer that ranks complete object outlines near the viewfinder center,
 * classifies its rough shape, and reports its centroid.
 *
 * Runs on the Y-plane only (grayscale) - fast, and all we need for edge detection.
 */
class ShapeAnalyzer(
    private val onResult: (DetectionResult?) -> Unit
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        var gray: Mat? = null
        var rotated: Mat? = null
        try {
            gray = imageProxyToGrayMat(image)
            rotated = rotateMat(gray, image.imageInfo.rotationDegrees)
            onResult(detectLargestShape(rotated))
        } catch (e: Exception) {
            android.util.Log.w("ShapeAnalyzer", "Frame analysis failed", e)
            onResult(null)
        } finally {
            rotated?.release()
            gray?.release()
            image.close()
        }
    }

    private fun imageProxyToGrayMat(image: ImageProxy): Mat {
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate()
        val origin = buffer.position()
        val crop = image.cropRect
        val pixels = ByteArray(crop.width() * crop.height())
        // Read only actual pixels: the final row need not include stride padding.
        for (y in 0 until crop.height()) {
            for (x in 0 until crop.width()) {
                pixels[y * crop.width() + x] = buffer.get(
                    origin + (y + crop.top) * plane.rowStride + (x + crop.left) * plane.pixelStride
                )
            }
        }
        return Mat(crop.height(), crop.width(), CvType.CV_8UC1).also { it.put(0, 0, pixels) }
    }

    private fun rotateMat(src: Mat, rotationDegrees: Int): Mat {
        if (rotationDegrees == 0) return src.clone()
        val dst = Mat()
        when (rotationDegrees) {
            90 -> Core.rotate(src, dst, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(src, dst, Core.ROTATE_180)
            270 -> Core.rotate(src, dst, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> return src.clone()
        }
        return dst
    }

    internal fun detectLargestShape(gray: Mat): DetectionResult? {
        val blurred = Mat()
        val edges = Mat()
        val mask = Mat()
        val hierarchy = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
        val contours = ArrayList<MatOfPoint>()
        try {
            // Suppress fine surface texture while keeping object boundaries.
            Imgproc.bilateralFilter(gray, blurred, 7, 45.0, 45.0)
            val mean = org.opencv.core.MatOfDouble()
            val deviation = org.opencv.core.MatOfDouble()
            val high = try {
                Core.meanStdDev(blurred, mean, deviation)
                (deviation.toArray()[0] * 2.0).coerceIn(40.0, 140.0)
            } finally {
                mean.release()
                deviation.release()
            }
            Imgproc.Canny(blurred, edges, high * 0.4, high)
            Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            // Local contrast also supplies closed outlines under uneven illumination.
            Imgproc.adaptiveThreshold(blurred, mask, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV, 31, 5.0)
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
            val localContours = ArrayList<MatOfPoint>()
            Imgproc.findContours(mask, localContours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            contours.addAll(localContours)

            val frameArea = gray.rows().toDouble() * gray.cols()
            var best: MatOfPoint? = null
            var bestScore = 0.0
            for (c in contours) {
                val area = Geometry.contourArea(c)
                if (area < frameArea * 0.008 || area > frameArea * 0.85) continue
                val bounds = Geometry.boundingRect(c)
                // A clipped outline does not describe the whole object's center.
                if (bounds.x <= 2 || bounds.y <= 2 ||
                    bounds.x + bounds.width >= gray.cols() - 2 ||
                    bounds.y + bounds.height >= gray.rows() - 2) continue
                val extent = area / (bounds.width.toDouble() * bounds.height)
                if (extent < 0.2) continue
                val m = Geometry.moments(c)
                if (m.m00 <= 0.0) continue
                val points = MatOfPoint2f(*c.toArray())
                val perimeter = try { Geometry.arcLength(points, true) } finally { points.release() }
                val compactness = ShapeMath.circularity(area, perimeter)
                if (compactness < 0.035) continue
                val aim = ShapeMath.aimScore(m.m10 / m.m00, m.m01 / m.m00, gray.cols(), gray.rows())
                val score = sqrt(area / frameArea) * aim * aim * sqrt(compactness) * sqrt(extent)
                if (score > bestScore) {
                    bestScore = score
                    best = c
                }
            }
            val bestContour = best ?: return null
            val moments = Geometry.moments(bestContour)
            if (moments.m00 == 0.0) return null

            val contour2f = MatOfPoint2f(*bestContour.toArray())
            val approx = MatOfPoint2f()
            val peri = Geometry.arcLength(contour2f, true)
            Geometry.approxPolyDP(contour2f, approx, 0.02 * peri, true)
            val vertexCount = approx.toArray().size
            contour2f.release()
            approx.release()

            val shapeName = classifyShape(vertexCount, bestContour)

            val w = gray.cols().toFloat()
            val h = gray.rows().toFloat()
            val cx = (moments.m10 / moments.m00).toFloat()
            val cy = (moments.m01 / moments.m00).toFloat()

            val normalizedContour = bestContour.toArray().map { p ->
                Pair((p.x.toFloat() / w).coerceIn(0f, 1f), (p.y.toFloat() / h).coerceIn(0f, 1f))
            }

            return DetectionResult(
                shapeName = shapeName,
                contourPoints = normalizedContour,
                centroid = Pair((cx / w).coerceIn(0f, 1f), (cy / h).coerceIn(0f, 1f)),
                frameWidth = gray.cols(),
                frameHeight = gray.rows()
            )
        } finally {
            contours.forEach { it.release() }
            blurred.release()
            edges.release()
            mask.release()
            hierarchy.release()
            kernel.release()
        }
    }

    private fun classifyShape(vertexCount: Int, contour: MatOfPoint): String {
        val points = MatOfPoint2f(*contour.toArray())
        val rotatedBox = try { Geometry.minAreaRect(points) } finally { points.release() }
        return when (vertexCount) {
            3 -> "Triangle"
            4 -> {
                val ratio = (rotatedBox.size.width / rotatedBox.size.height).toFloat()
                if (abs(ratio - 1.0f) < 0.08f) "Square" else "Rectangle"
            }
            5 -> "Pentagon"
            6 -> "Hexagon"
            7 -> "Object"
            8 -> "Octagon"
            else -> {
                val area = Geometry.contourArea(contour)
                val contour2f = MatOfPoint2f(*contour.toArray())
                val peri = Geometry.arcLength(contour2f, true)
                contour2f.release()
                val circularity = if (peri > 0) (4 * Math.PI * area) / (peri * peri) else 0.0
                if (circularity > 0.75) "Circle" else "Object"
            }
        }
    }
}