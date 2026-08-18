package com.example.centerfinder.vision

import androidx.compose.ui.geometry.Offset

enum class ShapeKind(val label: String) { Square("Square"), Rectangle("Rectangle"), Circle("Circle"), Oval("Oval"), Hexagon("Hexagon"), Honeycomb("Honeycomb"), Object("Object") }

data class CellOverlay(val polygon: List<Offset>, val center: Offset)

data class DetectionResult(
    val kind: ShapeKind,
    val outline: List<Offset>,
    val center: Offset,
    val confidence: Float,
    val frameWidth: Int,
    val frameHeight: Int,
    val cells: List<CellOverlay> = emptyList(),
    val timestampMs: Long = System.currentTimeMillis(),
)
