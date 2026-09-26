package com.androplay.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale

/** The launch artwork, decoded once in MainActivity and shared by the screens that show it. */
val LocalBackgroundImage = staticCompositionLocalOf<ImageBitmap?> { null }

/** The artwork's darkest tone, shown until (or instead of) the image. */
private val BACKGROUND_TONE = Color(0xFF0B0910)

/**
 * How much the artwork is darkened, the same on every screen so moving between them doesn't
 * flicker, and dark enough for text on top.
 */
private const val BACKGROUND_DIM = 0.7f

/**
 * The home and Settings backdrop: the launch artwork under a black veil, so text on top stays
 * readable. Playback screens keep a plain black background instead.
 */
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(BACKGROUND_TONE)) {
        LocalBackgroundImage.current?.let { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = BACKGROUND_DIM)))
        content()
    }
}
