package com.satnamapps.shapecenterfinder.model

/**
 * Result of analyzing a single camera frame.
 * All coordinates are normalized (0..1) relative to [frameWidth] x [frameHeight],
 * in the *display-oriented* frame (i.e. after correcting for sensor rotation).
 */
data class DetectionResult(
    val shapeName: String,
    val contourPoints: List<Pair<Float, Float>>,
    val centroid: Pair<Float, Float>,
    val frameWidth: Int,
    val frameHeight: Int
)