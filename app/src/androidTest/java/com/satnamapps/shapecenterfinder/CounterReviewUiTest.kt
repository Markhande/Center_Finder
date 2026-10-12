package com.satnamapps.shapecenterfinder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.opencv.android.OpenCVLoader
import kotlin.math.min

class CounterReviewUiTest {
    @get:Rule val compose = createComposeRule()
    @Before fun load() { check(OpenCVLoader.initLocal()) }

    @Test fun exampleSelectionAndManualCorrectionsUpdateVisibleCount() {
        val photo = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        Canvas(photo).apply {
            drawColor(Color.LTGRAY)
            val paint = Paint().apply { color = Color.BLACK }
            drawCircle(200f, 240f, 35f, paint)
            drawCircle(440f, 240f, 35f, paint)
        }
        compose.setContent { MaterialTheme { ObjectCounterScreen(initialPhoto = photo) } }
        compose.waitUntil(30000) { compose.onAllNodesWithText("2 items • 1 group").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("counter-photo-overlay").performTouchInput {
            val scale = min(width / 640f, height / 480f)
            click(Offset(width / 2f - 120f * scale, height / 2f))
        }
        compose.onNodeWithText("2 similar items").assertExists()
        compose.onNodeWithText("Correct count").performClick()
        compose.onNodeWithTag("counter-photo-overlay").performTouchInput {
            val scale = min(width / 640f, height / 480f)
            click(Offset(width / 2f - 120f * scale, height / 2f))
        }
        compose.onNodeWithText("Reviewed count: 1").assertExists()
        compose.onNodeWithTag("counter-photo-overlay").performTouchInput { click(center) }
        compose.onNodeWithText("Reviewed count: 2").assertExists()
        compose.onNodeWithText("Reset edits").performClick()
        compose.onNodeWithText("2 similar items").assertExists()
    }
}
