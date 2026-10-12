package com.satnamapps.shapecenterfinder

import kotlin.math.*

/** Matching scores are heuristics, not calibrated probabilities. */
internal object VisualSimilarity {
    fun cosine(a: FloatArray, b: FloatArray): Double {
        if (a.isEmpty() || a.size != b.size) return 0.0
        var dot = 0.0; var aa = 0.0; var bb = 0.0
        for (i in a.indices) {
            dot += a[i].toDouble() * b[i]; aa += a[i].toDouble() * a[i]; bb += b[i].toDouble() * b[i]
        }
        return if (aa <= 0.0 || bb <= 0.0) 0.0 else (dot / sqrt(aa * bb)).coerceIn(-1.0, 1.0)
    }

    fun matches(a: VisualItem, b: VisualItem, matchColor: Boolean, threshold: Double = 0.78): Boolean {
        if (a.id == b.id) return true
        val af = a.item.features ?: return false
        val bf = b.item.features ?: return false
        // Prevent texture alone matching an object of a radically different shape or scale.
        if (ShapeMath.ratio(af.area, bf.area) < 0.25 ||
            ShapeMath.ratio(af.aspect, bf.aspect) < 0.55) return false
        val learned = a.embedding != null && b.embedding != null
        if (ObjectGrouping.distance(af, bf) > if (learned) 2.5 else 1.3) return false
        if (learned && cosine(a.embedding!!, b.embedding!!) < threshold) return false
        if (matchColor && a.color.indices.sumOf { abs(a.color[it] - b.color[it]).toDouble() } > 0.65) return false
        return true
    }

    fun groups(items: List<VisualItem>, matchColor: Boolean): List<List<Int>> {
        val groups = mutableListOf<MutableList<Int>>()
        for (index in items.indices.sortedByDescending { items[it].item.features?.area ?: 0.0 }) {
            val target = groups.firstOrNull { group -> group.all {
                matches(items[index], items[it], matchColor) &&
                    ObjectGrouping.distance(items[index].item.features!!, items[it].item.features!!) <= 1.3
            } }
            if (target == null) groups.add(mutableListOf(index)) else target.add(index)
        }
        return groups.sortedByDescending { it.size }
    }
}

internal data class VisualItem(
    val id: Int,
    val item: ClusterCounter.Item,
    val embedding: FloatArray?,
    val color: FloatArray
)

internal data class VisualScene(
    val items: List<VisualItem>,
    val warnings: List<String>,
    val appearanceAvailable: Boolean,
    val elapsedMs: Long
) {
    fun matching(exampleId: Int, matchColor: Boolean, threshold: Double): List<Int> {
        val example = items.firstOrNull { it.id == exampleId } ?: return emptyList()
        return items.filter { VisualSimilarity.matches(example, it, matchColor, threshold) }.map { it.id }
    }

    /** Overlapping boxes choose the smallest containing item, never a nearby unrelated item. */
    fun hit(x: Float, y: Float, ids: Set<Int> = items.map { it.id }.toSet()): Int? =
        items.filter { it.id in ids && x >= it.item.left && y >= it.item.top &&
            x <= it.item.left + it.item.width && y <= it.item.top + it.item.height }
            .minByOrNull { it.item.width * it.item.height }?.id
}
