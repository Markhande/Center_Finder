package com.satnamapps.shapecenterfinder

import kotlin.math.abs
import kotlin.math.ln

/** Rotation-independent outline measurements; color is intentionally not an identity rule. */
data class ObjectFeatures(
    val area: Double,
    val aspect: Double,
    val circularity: Double,
    val extent: Double
)

internal object ObjectGrouping {
    fun distance(a: ObjectFeatures, b: ObjectFeatures): Double =
        abs(ln(a.area / b.area)) / ln(3.0) +
            abs(ln(a.aspect / b.aspect)) / ln(1.65) +
            abs(a.circularity - b.circularity) / 0.20 +
            abs(a.extent - b.extent) / 0.22

    /** Complete-link matching avoids a chain of weak matches merging unrelated objects. */
    fun group(features: List<ObjectFeatures>): List<List<Int>> {
        val groups = mutableListOf<MutableList<Int>>()
        for (index in features.indices.sortedByDescending { features[it].area }) {
            val match = groups.filter { group -> group.all { distance(features[index], features[it]) <= 1.3 } }
                .minByOrNull { group -> group.map { distance(features[index], features[it]) }.average() }
            if (match == null) groups.add(mutableListOf(index)) else match.add(index)
        }
        return groups.sortedByDescending { it.size }
    }
}
