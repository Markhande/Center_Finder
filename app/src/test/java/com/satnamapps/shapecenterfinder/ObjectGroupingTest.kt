package com.satnamapps.shapecenterfinder

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class ObjectGroupingTest {
    @Test fun tenSimilarBallsStayTogether() {
        val balls = (0 until 10).map { ObjectFeatures(1000.0 + it * 20, 1.03, 0.90, 0.78) }
        assertEquals(listOf(10), ObjectGrouping.group(balls).map { it.size })
    }
    @Test fun ballsSquaresAndLongItemsHaveSeparateCounts() {
        val objects = List(10) { ObjectFeatures(1000.0, 1.0, 0.91, 0.78) } +
            List(3) { ObjectFeatures(1100.0, 1.0, 0.76, 0.99) } +
            List(4) { ObjectFeatures(1000.0, 5.0, 0.39, 0.98) }
        assertEquals(listOf(10, 4, 3), ObjectGrouping.group(objects).map { it.size })
    }
    @Test fun emptySceneHasNoGroups() { assertTrue(ObjectGrouping.group(emptyList()).isEmpty()) }
    @Test fun letterboxTouchesDoNotSelectImage() {
        val mapping = CounterImageMapping(1000, 1000, 1000, 500)
        assertNull(mapping.fromScreen(Offset(500f, 100f)))
        assertEquals(Offset(0.5f, 0.5f), mapping.fromScreen(Offset(500f, 500f)))
        assertEquals(Offset(0f, 250f), mapping.toScreen(0f, 0f))
        assertEquals(Offset(1f, 1f), mapping.fromScreen(Offset(1200f, 1100f), clamp = true))
    }
}
