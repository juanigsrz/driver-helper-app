package com.juanigsrz.driverhelper

import android.graphics.Bitmap
import android.media.Image

/** Copies an RGBA_8888 [Image] from `ImageReader` into an ARGB_8888 [Bitmap]. */
fun Image.toBitmap(): Bitmap {
    val plane = planes[0]
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    val rowPadding = rowStride - pixelStride * width
    val padded = Bitmap.createBitmap(
        width + rowPadding / pixelStride,
        height,
        Bitmap.Config.ARGB_8888,
    )
    padded.copyPixelsFromBuffer(plane.buffer)
    return if (rowPadding == 0) {
        padded
    } else {
        Bitmap.createBitmap(padded, 0, 0, width, height).also { padded.recycle() }
    }
}

/** Returns a new Bitmap covering the bottom [frac] of `this` (0.4f = bottom 40%). */
fun Bitmap.cropBottomFraction(frac: Float): Bitmap {
    require(frac in 0f..1f) { "frac out of range: $frac" }
    val cropH = (height * frac).toInt().coerceAtLeast(1)
    val top = height - cropH
    return Bitmap.createBitmap(this, 0, top, width, cropH)
}
