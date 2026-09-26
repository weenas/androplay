package com.weenas.castbay.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The music controls' icons, drawn here rather than taken from material-icons-extended (several
 * MB for five shapes). Solid, rounded-corner-free glyphs in the SF Symbols spirit, on a 24 grid.
 */
object MediaIcons {
    val Play: ImageVector = icon("Play") {
        moveTo(7f, 4.5f); lineTo(19.5f, 12f); lineTo(7f, 19.5f); close()
    }

    val Pause: ImageVector = icon("Pause") {
        rect(6f, 4.5f, 4f, 15f)
        rect(14f, 4.5f, 4f, 15f)
    }

    val Next: ImageVector = icon("Next") {
        moveTo(5f, 5f); lineTo(15f, 12f); lineTo(5f, 19f); close()
        rect(16f, 5f, 3f, 14f)
    }

    val Previous: ImageVector = icon("Previous") {
        moveTo(19f, 5f); lineTo(9f, 12f); lineTo(19f, 19f); close()
        rect(5f, 5f, 3f, 14f)
    }

    val FastForward: ImageVector = icon("FastForward") {
        moveTo(3f, 6f); lineTo(12f, 12f); lineTo(3f, 18f); close()
        moveTo(12f, 6f); lineTo(21f, 12f); lineTo(12f, 18f); close()
    }

    val Rewind: ImageVector = icon("Rewind") {
        moveTo(21f, 6f); lineTo(12f, 12f); lineTo(21f, 18f); close()
        moveTo(12f, 6f); lineTo(3f, 12f); lineTo(12f, 18f); close()
    }

    private fun PathBuilder.rect(x: Float, y: Float, width: Float, height: Float) {
        moveTo(x, y); lineTo(x + width, y); lineTo(x + width, y + height); lineTo(x, y + height); close()
    }

    private fun icon(name: String, shape: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .path(fill = SolidColor(Color.White), pathBuilder = shape)
            .build()
}
