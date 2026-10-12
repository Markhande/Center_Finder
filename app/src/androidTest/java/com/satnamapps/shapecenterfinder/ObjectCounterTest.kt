package com.satnamapps.shapecenterfinder

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

@RunWith(AndroidJUnit4::class)
class ObjectCounterTest {
    @Before fun load() { assertTrue(OpenCVLoader.initLocal()) }
    private fun scene(background: Scalar = Scalar(220.0, 220.0, 220.0, 255.0), check: (Mat) -> Unit) {
        val frame = Mat(480, 640, CvType.CV_8UC4, background)
        try { check(frame) } finally { frame.release() }
    }
    private fun count(frame: Mat) = ClusterCounter.count(frame, Rect(0, 0, frame.cols(), frame.rows()))
    @Test fun emptyImageHasNoObjects() = scene { assertEquals(0, count(it).count) }
    @Test fun tenBallsAreOneGroupEvenWithDifferentColors() = scene { frame ->
        for (i in 0 until 10) {
            val color = if (i % 2 == 0) Scalar(180.0, 20.0, 30.0, 255.0) else Scalar(20.0, 40.0, 180.0, 255.0)
            Imgproc.circle(frame, Point(90.0 + (i % 5) * 110, 140.0 + (i / 5) * 170), 30, color, -1)
        }
        val result = count(frame)
        assertEquals(10, result.count)
        assertEquals(1, result.groups.size)
    }
    @Test fun lightObjectsOnDarkBackgroundAreCounted() = scene(Scalar(20.0, 20.0, 20.0, 255.0)) { frame ->
        for (i in 0 until 4) Imgproc.circle(frame, Point(100.0 + i * 130, 240.0), 30, Scalar(230.0, 230.0, 230.0, 255.0), -1)
        assertEquals(4, count(frame).count)
    }
    @Test fun circlesAndRectanglesFormDifferentGroups() = scene { frame ->
        for (i in 0 until 3) {
            Imgproc.circle(frame, Point(100.0 + i * 180, 130.0), 30, Scalar(30.0, 30.0, 30.0, 255.0), -1)
            Imgproc.rectangle(frame, Point(70.0 + i * 180, 280.0), Point(130.0 + i * 180, 340.0), Scalar(30.0, 30.0, 30.0, 255.0), -1)
        }
        val result = count(frame)
        assertEquals(6, result.count)
        assertEquals(listOf(3, 3), result.groups.map { it.items.size })
    }
    @Test fun touchingBallsSplitButLongRectangleDoesNot() = scene { frame ->
        Imgproc.circle(frame, Point(220.0, 150.0), 40, Scalar(30.0, 30.0, 30.0, 255.0), -1)
        Imgproc.circle(frame, Point(290.0, 150.0), 40, Scalar(30.0, 30.0, 30.0, 255.0), -1)
        Imgproc.rectangle(frame, Point(170.0, 300.0), Point(400.0, 340.0), Scalar(30.0, 30.0, 30.0, 255.0), -1)
        assertEquals(3, count(frame).count)
    }
    @Test fun clippedObjectIsExcluded() = scene { frame ->
        Imgproc.circle(frame, Point(0.0, 240.0), 50, Scalar(30.0, 30.0, 30.0, 255.0), -1)
        val result = count(frame)
        assertEquals(0, result.count)
        assertEquals(1, result.excludedAtBorder)
    }
}
