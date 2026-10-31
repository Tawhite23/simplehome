package jp.kantanhome.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

val Green = Color(0xFF2E7D32)
val SubText = Color(0xFF555555)

private val Scheme = lightColorScheme(
    primary = Color(0xFF1B5E9E),
    onPrimary = Color.White,
    background = Color(0xFFF5F3EE),
    onBackground = Color(0xFF1A1A1A),
    surface = Color.White,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFE6E1D6),
    onSurfaceVariant = Color(0xFF1A1A1A),
    error = Color(0xFFC62828),
    onError = Color.White,
)

/** 端末の文字サイズ設定に、ホームの「文字の大きさ」をさらに掛ける */
@Composable
fun KantanTheme(fontScale: Float, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * fontScale)) {
        MaterialTheme(colorScheme = Scheme, content = content)
    }
}
