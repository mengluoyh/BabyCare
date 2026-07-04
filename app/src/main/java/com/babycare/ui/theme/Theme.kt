package com.babycare.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BabyBlue = Color(0xFFAECBEB)
val WarmYellow = Color(0xFFFFE082)
val MintGreen = Color(0xFFA5D6A7)

private val LightColorScheme = lightColorScheme(
    primary = BabyBlue,
    secondary = WarmYellow,
    tertiary = MintGreen,
    background = Color(0xFFF9FAFB),
    surface = Color.White
)

@Composable
fun BabyAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColorScheme, content = content)
}