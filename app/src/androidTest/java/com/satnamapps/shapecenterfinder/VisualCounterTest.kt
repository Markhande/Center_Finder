package com.satnamapps.shapecenterfinder

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

@RunWith(AndroidJUnit4::class)
class VisualCounterTest {
    @Before fun load() { assertTrue(OpenCVLoader.initLocal()) }

    @Test fun bundledModelRunsOfflineAndMatchesRepeatedObjects() = runBlocking {
        val frame = Mat(480, 640, CvType.CV_8UC4, Scalar(220.0, 220.0, 220.0, 255.0))
        val photo = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        try {
            for (i in 0 until 3) Imgproc.circle(frame, Point(100.0 + i * 200, 240.0), 35, Scalar(30.0, 30.0, 30.0, 255.0), -1)
            Utils.matToBitmap(frame, photo)
            val roi = Rect(0, 0, 640, 480)
            val detection = ClusterCounter.count(frame, roi)
            val scene = VisualCounter.analyze(InstrumentationRegistry.getInstrumentation().targetContext, photo, roi, detection)
            assertTrue("Bundled model must actually run, not silently fall back", scene.appearanceAvailable)
            assertEquals(3, scene.items.size)
            assertTrue(scene.items.all { it.embedding != null && it.embedding!!.isNotEmpty() })
            assertEquals(3, scene.matching(0, false, 0.78).size)
            assertEquals(1, VisualSimilarity.groups(scene.items, false).size)
        } finally { frame.release(); photo.recycle() }
    }

    @Test fun darkFeaturelessPhotoReportsQualityIssue() {
        val photo = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        try {
            photo.eraseColor(android.graphics.Color.rgb(10, 10, 10))
            val warnings = VisualCounter.qualityWarnings(photo, Rect(0, 0, 200, 200))
            assertTrue(warnings.any { it.contains("dark") })
            assertTrue(warnings.any { it.contains("sharp") })
        } finally { photo.recycle() }
    }
}
