package com.masselis.tpmsadvanced.core.ui

import androidx.compose.runtime.staticCompositionLocalOf

public data class Readability(
    val highVisibility: Boolean = false,
    val scale: Float = 1f,
    val readingCards: Boolean = false,
)
public val LocalReadability: androidx.compose.runtime.ProvidableCompositionLocal<Readability> =
    staticCompositionLocalOf { Readability() }
