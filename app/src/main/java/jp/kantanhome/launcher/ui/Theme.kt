package jp.kantanhome.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density

val Blue = Color(0xFF0A84FF)
val Green = Color(0xFF30C15B)
val Red = Color(0xFFFF453A)
val SubText = Color(0xFF6E6E73)
val Ink = Color(0xFF1C1C1E)

/** 壁紙の上に置く白文字の影（明るい壁紙でも読めるように） */
val OnWallpaper = TextStyle(
    color = Color.White,
    shadow = Shadow(Color(0x99000000), Offset(0f, 2f), 8f),
)

/** iPhone っぽいやわらかいグラデーションの壁紙 */
class Wallpaper(val name: String, val colors: List<Color>)

val Wallpapers = listOf(
    Wallpaper("あお", listOf(Color(0xFF1E3A6E), Color(0xFF3A6FD8), Color(0xFF8FB8F5))),
    Wallpaper("ゆうやけ", listOf(Color(0xFF3B2667), Color(0xFFBC4E9C), Color(0xFFF8A07A))),
    Wallpaper("みどり", listOf(Color(0xFF0F3D3E), Color(0xFF2E8B57), Color(0xFFA8E6A1))),
    Wallpaper("さくら", listOf(Color(0xFF6B3A5B), Color(0xFFD07AA0), Color(0xFFF6D1DD))),
)

fun wallpaperBrush(index: Int): Brush =
    Brush.linearGradient(Wallpapers[index.coerceIn(0, Wallpapers.lastIndex)].colors)

private val Scheme = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    background = Color(0xFFF2F2F7),
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Ink,
    error = Red,
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
