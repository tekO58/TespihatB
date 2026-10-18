package com.tekin.tesbih

import android.graphics.Color

/**
 * Standalone Tesbih theme helper.
 * Keep the original dark palette as the default; theme switching can be added
 * independently without importing WorkPlanner-specific app infrastructure.
 */
object ThemeMode {
    @Volatile
    var light: Boolean = false
        private set

    fun pick(dark: String, light: String): Int =
        Color.parseColor(if (this.light) light else dark)
}
