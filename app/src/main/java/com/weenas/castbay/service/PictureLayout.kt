package com.weenas.castbay.service

/** Where a picture goes on screen for each [ReceiverSettings.pictureMode]. */
object PictureLayout {
    /**
     * The size to lay a [videoWidth]x[videoHeight] picture out at, centred in a [boxWidth]x[boxHeight]
     * screen. Fit shows it whole (black bars), Fill covers the screen and crops what sticks out
     * (the result is larger than the screen), Stretch covers it and distorts the picture.
     */
    fun size(mode: String, boxWidth: Float, boxHeight: Float, videoWidth: Int, videoHeight: Int): Pair<Float, Float> {
        if (mode == ReceiverSettings.PICTURE_STRETCH || videoWidth <= 0 || videoHeight <= 0) return boxWidth to boxHeight
        val scaleX = boxWidth / videoWidth
        val scaleY = boxHeight / videoHeight
        val scale = if (mode == ReceiverSettings.PICTURE_FILL) maxOf(scaleX, scaleY) else minOf(scaleX, scaleY)
        return videoWidth * scale to videoHeight * scale
    }
}
