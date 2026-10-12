package com.satnamapps.shapecenterfinder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class ShapeMathTest {

    @Test
    fun squareCircularityIsNotACircle() {
        // Perfect square: circularity = π/4 ≈ 0.785. The old detector
        // treated anything above 0.78 as a circle, which mislabeled squares.
        val side = 100.0
        val area = side * side
        val perimeter = 4.0 * side
        val c = ShapeMath.circularity(area, perimeter)
        assertTrue(c in 0.78..0.79)
        assertTrue(c < 0.86)
    }

    @Test
    fun circleCircularityIsNearOne() {
        val r = 50.0
        val area = PI * r * r
        val perimeter = 2.0 * PI * r
        val c = ShapeMath.circularity(area, perimeter)
        assertTrue(c in 0.99..1.01)
    }

    @Test
    fun squareAspectUsesTightTolerance() {
        assertTrue(ShapeMath.isSquareAspect(1.0))
        assertTrue(ShapeMath.isSquareAspect(1.10))
        assertFalse(ShapeMath.isSquareAspect(1.25))
    }

    @Test
    fun quadScorePrefersRightAngles() {
        val square = listOf(
            Vec2(0.0, 0.0),
            Vec2(100.0, 0.0),
            Vec2(100.0, 100.0),
            Vec2(0.0, 100.0)
        )
        val skewed = listOf(
            Vec2(0.0, 0.0),
            Vec2(100.0, 10.0),
            Vec2(80.0, 90.0),
            Vec2(20.0, 70.0)
        )
        assertTrue(ShapeMath.quadScore(square) > 0.92)
        assertTrue(ShapeMath.quadScore(square) > ShapeMath.quadScore(skewed))
    }

    @Test
    fun hexagonScorePrefersRegularHex() {
        val hex = ShapeMath.regularPolygon(Vec2(0.0, 0.0), 40.0, 6, 0.0)
        val squashed = hex.mapIndexed { i, p ->
            if (i % 2 == 0) Vec2(p.x * 1.4, p.y * 0.6) else p
        }
        assertTrue(ShapeMath.hexagonScore(hex) > 0.9)
        assertTrue(ShapeMath.hexagonScore(hex) > ShapeMath.hexagonScore(squashed))
    }

    @Test
    fun vertexMatchingKeepsOutlineStable() {
        val prev = listOf(0f to 0f, 10f to 0f, 10f to 10f, 0f to 10f)
        val rotated = listOf(10f to 0f, 10f to 10f, 0f to 10f, 0f to 0f)
        val matched = ShapeMath.matchVertices(prev, rotated)
        assertEquals(prev, matched)
    }

    @Test
    fun aimScorePeaksAtViewfinderCenter() {
        val center = ShapeMath.aimScore(500.0, 500.0, 1000, 1000)
        val corner = ShapeMath.aimScore(50.0, 50.0, 1000, 1000)
        assertTrue(center > corner)
        assertTrue(abs(center - 1.0) < 0.01)
    }

    @Test
    fun nameSmootherShowsLiveName() {
        val s = DetectionSmoother(windowSize = 5, requiredAgreement = 3)
        assertEquals("Square", s.stabilizeName("Square"))
        assertEquals("Circle", s.stabilizeName("Circle"))
        assertEquals(null, s.stabilizeName(null))
    }
}