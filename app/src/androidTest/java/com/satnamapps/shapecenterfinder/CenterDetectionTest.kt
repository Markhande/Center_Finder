package com.satnamapps.shapecenterfinder

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.satnamapps.shapecenterfinder.model.ShapeAnalyzer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

@RunWith(AndroidJUnit4::class)
class CenterDetectionTest {
    @Before fun loadOpenCv() { assertTrue(OpenCVLoader.initLocal()) }

    @Test fun blankFrameHasNoCenter() {
        val frame = Mat(480, 640, CvType.CV_8UC1, Scalar(160.0))
        try { assertNull(ShapeAnalyzer {}.detectLargestShape(frame)) }
        finally { frame.release() }
    }

    @Test fun circleOnPaperKeepsItsCenter() {
        checkCircle(false)
    }

    @Test fun texturedObjectBesideClippedBackgroundKeepsItsCenter() {
        checkCircle(true)
    }

    private fun checkCircle(clutter: Boolean) {
        val frame = Mat(480, 640, CvType.CV_8UC1, Scalar(220.0))
        try {
            if (clutter) {
                Imgproc.rectangle(frame, Point(0.0, 20.0), Point(100.0, 460.0), Scalar(30.0), -1)
            }
            Imgproc.circle(frame, Point(320.0, 240.0), 90, Scalar(65.0), -1)
            if (clutter) {
                for (y in 195..285 step 15) {
                    Imgproc.line(frame, Point(275.0, y.toDouble()), Point(365.0, y.toDouble()), Scalar(115.0), 2)
                }
            }
            val result = ShapeAnalyzer {}.detectLargestShape(frame)
            assertNotNull(result)
            assertEquals(0.5, result!!.centroid.first.toDouble(), 0.025)
            assertEquals(0.5, result.centroid.second.toDouble(), 0.025)
            val xs = result.contourPoints.map { it.first }
            assertTrue("Must select the outer object, not its texture", xs.max() - xs.min() > 0.25f)
        } finally { frame.release() }
    }
}
