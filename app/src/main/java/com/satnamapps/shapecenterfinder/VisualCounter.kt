package com.satnamapps.shapecenterfinder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.*

internal object VisualCounter {
    /** Runs entirely on a worker. Runtime/model ownership ends even if the screen is cancelled. */
    suspend fun analyze(context: Context, photo: Bitmap, roi: Rect, detection: ClusterCounter.CountResult): VisualScene {
        val start = SystemClock.elapsedRealtime()
        val warnings = qualityWarnings(photo, roi).toMutableList()
        val objects = detection.groups.flatMap { it.items }
        // Bound inference cost on dense/noisy images; never silently omit detections from the count.
        var appearanceAvailable = objects.isNotEmpty() && objects.size <= 150
        var embedder: ImageEmbedder? = null
        if (appearanceAvailable) {
            try {
                embedder = ImageEmbedder.createFromOptions(context, ImageEmbedder.ImageEmbedderOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("mobilenet_v3_small.tflite").build())
                    .setL2Normalize(true).build())
            } catch (e: RuntimeException) {
                Log.w("VisualCounter", "Appearance model unavailable", e)
                appearanceAvailable = false
            } catch (e: LinkageError) {
                Log.w("VisualCounter", "Appearance runtime unavailable", e)
                appearanceAvailable = false
            }
        }
        try {
            val items = objects.mapIndexed { index, item ->
                currentCoroutineContext().ensureActive()
                val crop = objectCrop(photo, roi, item)
                try {
                    val color = colorHistogram(crop)
                    val grayscale = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
                    val embedding = try {
                        Canvas(grayscale).drawBitmap(crop, 0f, 0f, Paint().apply {
                            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                        })
                        if (appearanceAvailable) {
                            val image = BitmapImageBuilder(grayscale).build()
                            try { embedder!!.embed(image).embeddingResult().embeddings().first().floatEmbedding().clone() }
                            catch (e: RuntimeException) {
                                Log.w("VisualCounter", "Appearance inference failed", e)
                                appearanceAvailable = false
                                null
                            } finally { image.close() }
                        } else null
                    } finally { if (!grayscale.isRecycled) grayscale.recycle() }
                    VisualItem(index, item, embedding, color)
                } finally { crop.recycle() }
            }
            if (!appearanceAvailable && objects.isNotEmpty()) warnings.add(
                if (objects.size > 150) "Dense image: shape matching only. Select a smaller area for appearance matching."
                else "Appearance matching unavailable; using shape matching only."
            )
            // One fallback policy for the whole image; do not mix scores from two different methods.
            return VisualScene(if (appearanceAvailable) items else items.map { it.copy(embedding = null) },
                warnings, appearanceAvailable, SystemClock.elapsedRealtime() - start)
        } finally { embedder?.close() }
    }

    private fun objectCrop(photo: Bitmap, roi: Rect, item: ClusterCounter.Item): Bitmap {
        val padding = max(item.width, item.height) * 0.08f
        val left = floor(roi.x + item.left - padding).toInt().coerceIn(0, photo.width - 1)
        val top = floor(roi.y + item.top - padding).toInt().coerceIn(0, photo.height - 1)
        val right = ceil(roi.x + item.left + item.width + padding).toInt().coerceIn(left + 1, photo.width)
        val bottom = ceil(roi.y + item.top + item.height + padding).toInt().coerceIn(top + 1, photo.height)
        val output = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(android.graphics.Color.rgb(127, 127, 127))
        val scale = min(224f / (right - left), 224f / (bottom - top))
        val width = (right - left) * scale; val height = (bottom - top) * scale
        canvas.drawBitmap(photo, android.graphics.Rect(left, top, right, bottom),
            RectF((224 - width) / 2, (224 - height) / 2, (224 + width) / 2, (224 + height) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        return output
    }

    private fun colorHistogram(bitmap: Bitmap): FloatArray {
        val histogram = FloatArray(12)
        val hsv = FloatArray(3)
        var n = 0
        // Central pixels reduce the influence of the surrounding background.
        for (y in 64 until 160 step 4) for (x in 64 until 160 step 4) {
            android.graphics.Color.colorToHSV(bitmap.getPixel(x, y), hsv)
            val bin = when {
                hsv[2] < 0.18 -> 10
                hsv[1] < 0.15 -> 11
                else -> (hsv[0] / 36).toInt().coerceIn(0, 9)
            }
            histogram[bin]++; n++
        }
        return histogram.map { it / n }.toFloatArray()
    }

    internal fun qualityWarnings(photo: Bitmap, roi: Rect): List<String> {
        val rgba = Mat(); val gray = Mat(); val laplacian = Mat()
        val mean = MatOfDouble(); val std = MatOfDouble()
        try {
            Utils.bitmapToMat(photo, rgba)
            val region = Mat(rgba, roi)
            try { Imgproc.cvtColor(region, gray, Imgproc.COLOR_RGBA2GRAY) } finally { region.release() }
            val brightness = Core.mean(gray).`val`[0]
            Imgproc.Laplacian(gray, laplacian, CvType.CV_64F)
            Core.meanStdDev(laplacian, mean, std)
            val sharpness = std.toArray()[0].pow(2)
            return buildList {
                if (brightness < 40) add("Photo is dark. Add light and retake for a better count.")
                if (brightness > 245) add("Photo may be overexposed. Reduce glare and retake.")
                if (sharpness < 35) add("Few sharp details found. Check focus and hold the camera steady.")
            }
        } finally { listOf(rgba, gray, laplacian, mean, std).forEach { it.release() } }
    }
}
