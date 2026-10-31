@file:OptIn(ExperimentalFoundationApi::class)

package jp.kantanhome.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.kantanhome.launcher.data.HomeItem
import jp.kantanhome.launcher.data.IconCache
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** iPhone のアイコンに近い角の丸さ（一辺の約 22%） */
fun iconShape(size: Dp): Shape = RoundedCornerShape(size * 0.225f)

/** すりガラス風の面。背景ぼかしは使わず、半透明の白とふちの光で表現する。 */
fun Modifier.glass(shape: Shape, alpha: Float = 0.22f): Modifier = this
    .clip(shape)
    .background(Color.White.copy(alpha = alpha))
    .border(1.dp, Color.White.copy(alpha = 0.35f), shape)

@Composable
fun BigButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (color == Color.Unspecified) scheme.primary else color,
            contentColor = if (contentColor == Color.Unspecified) scheme.onPrimary else contentColor,
        ),
    ) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
fun SubButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    BigButton(
        text = text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = Blue,
        onClick = onClick,
    )

/** 壁紙の上に置く小さめのガラスボタン */
@Composable
fun GlassChip(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .glass(shape, 0.28f)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text, style = OnWallpaper, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

private val AvatarColors = listOf(
    listOf(Color(0xFF6FB1FC), Color(0xFF2F6FD6)),
    listOf(Color(0xFFFF9A8B), Color(0xFFE0457B)),
    listOf(Color(0xFF8BE3A0), Color(0xFF2E9E5B)),
    listOf(Color(0xFFFFC371), Color(0xFFF07B2B)),
    listOf(Color(0xFFC3A6FF), Color(0xFF7D4FD6)),
    listOf(Color(0xFF7FE0E6), Color(0xFF1F98A6)),
)

/** iPhone の連絡先のような丸いアイコン（名前の1文字目） */
@Composable
fun Avatar(name: String, size: Dp) {
    val colors = AvatarColors[(name.hashCode() and 0x7fffffff) % AvatarColors.size]
    val textSize = with(LocalDensity.current) { (size * 0.42f).toSp() }
    Box(
        Modifier
            .size(size)
            .shadow(4.dp, CircleShape)
            .background(Brush.verticalGradient(colors), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1), color = Color.White, fontSize = textSize, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ItemIcon(item: HomeItem, size: Dp) {
    if (item is HomeItem.Contact) {
        Avatar(item.name, size)
        return
    }
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap: ImageBitmap? by produceState(IconCache.peek(item.key), item.key) {
        if (value == null) value = IconCache.load(context, item, px)
    }
    val shape = iconShape(size)
    val b = bitmap
    Box(
        Modifier
            .size(size)
            .shadow(4.dp, shape)
            .clip(shape)
            .background(Color.White),
    ) {
        if (b != null) Image(bitmap = b, contentDescription = null, modifier = Modifier.size(size))
    }
}

/** ホームの1つ分：アイコン＋白い名前。カードの枠はなし（iPhone と同じ）。 */
@Composable
fun HomeIcon(
    label: String,
    iconSize: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    showLabel: Boolean = true,
    icon: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .then(if (selected) Modifier.glass(RoundedCornerShape(20.dp), 0.35f) else Modifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = OnWallpaper,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 点線のマス。空きマスの「ここへ」や、ドックの「＋」に使う。 */
@Composable
fun DashedSlot(
    iconSize: Dp,
    text: String,
    circle: Boolean,
    showLabel: Boolean = true,
    onClick: () -> Unit,
) {
    val radius = if (circle) iconSize / 2 else iconSize * 0.225f
    val textSize = with(LocalDensity.current) { (iconSize * 0.42f).toSp() }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(radius))
                .background(Color.White.copy(alpha = 0.14f))
                .drawBehind {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.85f),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                        ),
                        cornerRadius = CornerRadius(radius.toPx()),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("＋", style = OnWallpaper, fontSize = textSize, fontWeight = FontWeight.Light)
        }
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(text, style = OnWallpaper, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** 空きマス。ふだんは見えない。入れ替え中だけ「ここへ」と出る。 */
@Composable
fun EmptySlot(iconSize: Dp, active: Boolean, circle: Boolean = false, onClick: () -> Unit) {
    if (active) {
        DashedSlot(iconSize, "ここへ", circle, onClick = onClick)
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.size(iconSize))
            Spacer(Modifier.height(6.dp))
            Text(" ", fontSize = 15.sp, lineHeight = 19.sp)
        }
    }
}

/** iPhone の時計ウィジェット風。大きめの時刻と日付。 */
@Composable
fun ClockWidget() {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val n = LocalDateTime.now()
            now = n
            delay((60 - n.second) * 1000L)
        }
    }
    val time = remember(now) { now.format(DateTimeFormatter.ofPattern("H:mm")) }
    val date = remember(now) { now.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.JAPANESE)) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .glass(RoundedCornerShape(28.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(time, style = OnWallpaper, fontSize = 64.sp, lineHeight = 68.sp, fontWeight = FontWeight.SemiBold)
        Text(date, style = OnWallpaper, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}
