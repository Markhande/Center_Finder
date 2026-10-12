package com.satnamapps.shapecenterfinder

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal data class Vec2(val x: Double, val y: Double) {
    fun toPair(): Pair<Float, Float> = x.toFloat() to y.toFloat()
}

/** Pure geometry used by the detector and unit-tested without OpenCV. */
internal object ShapeMath {

    fun circularity(area: Double, perimeter: Double): Double {
        if (perimeter <= 1e-6) return 0.0
        return (4.0 * PI * area) / (perimeter * perimeter)
    }

    fun aspect(width: Double, height: Double): Double {
        val a = max(width, height)
        val b = min(width, height)
        return if (b <= 1e-6) Double.POSITIVE_INFINITY else a / b
    }

    fun isSquareAspect(aspect: Double): Boolean = aspect in 1.0..1.14

    fun sideLengths(pts: List<Vec2>): DoubleArray {
        val n = pts.size
        return DoubleArray(n) { i ->
            val a = pts[i]
            val b = pts[(i + 1) % n]
            hypot(a.x - b.x, a.y - b.y)
        }
    }

    fun interiorAnglesDeg(pts: List<Vec2>): DoubleArray {
        val n = pts.size
        return DoubleArray(n) { i ->
            val prev = pts[(i - 1 + n) % n]
            val curr = pts[i]
            val next = pts[(i + 1) % n]
            angleAtDeg(prev, curr, next)
        }
    }

    fun angleAtDeg(prev: Vec2, curr: Vec2, next: Vec2): Double {
        val ux = prev.x - curr.x
        val uy = prev.y - curr.y
        val vx = next.x - curr.x
        val vy = next.y - curr.y
        val du = hypot(ux, uy)
        val dv = hypot(vx, vy)
        if (du < 1e-6 || dv < 1e-6) return 0.0
        val cos = ((ux * vx + uy * vy) / (du * dv)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cos))
    }

    fun coefficientOfVariation(values: DoubleArray): Double {
        if (values.isEmpty()) return 1.0
        val mean = values.average()
        if (abs(mean) < 1e-6) return 1.0
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance) / abs(mean)
    }

    fun mean(values: DoubleArray): Double = if (values.isEmpty()) 0.0 else values.average()

    fun centroid(pts: List<Vec2>): Vec2 {
        if (pts.isEmpty()) return Vec2(0.0, 0.0)
        return Vec2(pts.map { it.x }.average(), pts.map { it.y }.average())
    }

    /** How close every angle is to [target] degrees. 1 = perfect. */
    fun angleAgreement(angles: DoubleArray, target: Double, tolerance: Double): Double {
        if (angles.isEmpty()) return 0.0
        val scores = angles.map { ang ->
            val err = abs(ang - target)
            (1.0 - (err / tolerance).coerceIn(0.0, 1.0))
        }
        return scores.average()
    }

    /**
     * Quad quality in 0..1 using 90° corners and opposite-side agreement.
     * Real photographed rectangles score high even with a little perspective.
     */
    fun quadScore(pts: List<Vec2>): Double {
        if (pts.size != 4) return 0.0
        val sides = sideLengths(pts)
        val angles = interiorAnglesDeg(pts)
        val angleQ = angleAgreement(angles, 90.0, 28.0)
        val opp01 = ratio(sides[0], sides[2])
        val opp13 = ratio(sides[1], sides[3])
        val sideQ = (opp01 + opp13) / 2.0
        return (0.62 * angleQ + 0.38 * sideQ).coerceIn(0.0, 1.0)
    }

    /**
     * Regular-hexagon quality in 0..1: 120° interiors and similar sides.
     */
    fun hexagonScore(pts: List<Vec2>): Double {
        if (pts.size != 6) return 0.0
        val sides = sideLengths(pts)
        val angles = interiorAnglesDeg(pts)
        val angleQ = angleAgreement(angles, 120.0, 32.0)
        val sideQ = (1.0 - coefficientOfVariation(sides).coerceIn(0.0, 1.0))
        return (0.55 * angleQ + 0.45 * sideQ).coerceIn(0.0, 1.0)
    }

    fun ratio(a: Double, b: Double): Double {
        val hi = max(abs(a), abs(b))
        val lo = min(abs(a), abs(b))
        return if (hi < 1e-6) 0.0 else lo / hi
    }

    fun regularPolygon(center: Vec2, radius: Double, sides: Int, startAngleRad: Double): List<Vec2> {
        return (0 until sides).map { i ->
            val t = startAngleRad + i * 2.0 * PI / sides
            Vec2(center.x + radius * cos(t), center.y + radius * sin(t))
        }
    }

    fun startAngleRad(pts: List<Vec2>, center: Vec2): Double {
        if (pts.isEmpty()) return 0.0
        val angles = pts.map { atan2(it.y - center.y, it.x - center.x) }.sorted()
        return angles.average()
    }

    fun meanRadius(pts: List<Vec2>, center: Vec2): Double {
        if (pts.isEmpty()) return 0.0
        return pts.map { hypot(it.x - center.x, it.y - center.y) }.average()
    }

    fun ellipsePoints(center: Vec2, width: Double, height: Double, angleDeg: Double, samples: Int = 32): List<Vec2> {
        val rad = Math.toRadians(angleDeg)
        val cosA = cos(rad)
        val sinA = sin(rad)
        val rx = width / 2.0
        val ry = height / 2.0
        return (0 until samples).map { i ->
            val t = i * 2.0 * PI / samples
            val x = rx * cos(t)
            val y = ry * sin(t)
            Vec2(x * cosA - y * sinA + center.x, x * sinA + y * cosA + center.y)
        }
    }

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /**
     * Match [next] vertices to [prev] by nearest unused neighbor so the
     * outline does not rotate/jitter between frames.
     */
    fun matchVertices(
        prev: List<Pair<Float, Float>>,
        next: List<Pair<Float, Float>>
    ): List<Pair<Float, Float>> {
        if (prev.size != next.size || prev.isEmpty()) return next
        val used = BooleanArray(next.size)
        val ordered = ArrayList<Pair<Float, Float>>(prev.size)
        for (p in prev) {
            var best = -1
            var bestD = Double.POSITIVE_INFINITY
            for (i in next.indices) {
                if (used[i]) continue
                val d = hypot((next[i].first - p.first).toDouble(), (next[i].second - p.second).toDouble())
                if (d < bestD) {
                    bestD = d
                    best = i
                }
            }
            if (best >= 0) {
                used[best] = true
                ordered += next[best]
            }
        }
        return if (ordered.size == next.size) ordered else next
    }

    fun blendPoints(
        prev: List<Pair<Float, Float>>,
        next: List<Pair<Float, Float>>,
        alpha: Float
    ): List<Pair<Float, Float>> {
        val matched = matchVertices(prev, next)
        if (prev.size != matched.size) return next
        return prev.indices.map { i ->
            lerp(prev[i].first, matched[i].first, alpha) to lerp(prev[i].second, matched[i].second, alpha)
        }
    }

    /** Score an object higher when it sits near the viewfinder center. */
    fun aimScore(cx: Double, cy: Double, imgW: Int, imgH: Int): Double {
        val nx = (cx - imgW / 2.0) / imgW.toDouble()
        val ny = (cy - imgH / 2.0) / imgH.toDouble()
        return 1.0 / (1.0 + 7.0 * (nx * nx + ny * ny))
    }
}
