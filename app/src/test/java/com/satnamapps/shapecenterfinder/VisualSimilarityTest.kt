package com.satnamapps.shapecenterfinder

import org.junit.Assert.*
import org.junit.Test

class VisualSimilarityTest {
    private fun item(id: Int, embedding: FloatArray?, color: FloatArray = floatArrayOf(1f, 0f), area: Double = 1000.0) =
        VisualItem(id, ClusterCounter.Item(10f, 10f, 0f, 0f, 20f, 20f,
            ObjectFeatures(area, 1.0, 0.9, 0.78)), embedding, color)

    @Test fun sameShapeWithDifferentAppearanceDoesNotMatch() {
        assertFalse(VisualSimilarity.matches(item(0, floatArrayOf(1f, 0f)), item(1, floatArrayOf(0f, 1f)), false))
    }
    @Test fun colorIsOptional() {
        val first = item(0, floatArrayOf(1f, 0f))
        val second = item(1, floatArrayOf(1f, 0f), floatArrayOf(0f, 1f))
        assertTrue(VisualSimilarity.matches(first, second, false))
        assertFalse(VisualSimilarity.matches(first, second, true))
    }
    @Test fun shapeFallbackStillWorks() {
        assertTrue(VisualSimilarity.matches(item(0, null), item(1, null), false))
        assertFalse(VisualSimilarity.matches(item(0, null), item(1, null, area = 20000.0), false))
    }
    @Test fun selectedExampleAlwaysIncludedAndOthersFiltered() {
        val scene = VisualScene(listOf(item(0, floatArrayOf(1f, 0f)), item(1, floatArrayOf(1f, 0f)),
            item(2, floatArrayOf(0f, 1f))), emptyList(), true, 0)
        assertEquals(listOf(0, 1), scene.matching(0, false, 0.9))
        assertTrue(scene.matching(9, false, 0.9).isEmpty())
        assertNull(scene.hit(50f, 50f))
    }
    @Test fun degenerateEmbeddingCannotClaimSimilarity() {
        assertEquals(0.0, VisualSimilarity.cosine(floatArrayOf(0f), floatArrayOf(0f)), 0.0)
        assertEquals(0.0, VisualSimilarity.cosine(floatArrayOf(1f), floatArrayOf(1f, 0f)), 0.0)
    }
}
