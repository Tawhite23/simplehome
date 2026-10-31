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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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

private val CardShape = RoundedCornerShape(18.dp)

@Composable
fun Modifier.card(): Modifier = this
    .clip(CardShape)
    .background(MaterialTheme.colorScheme.surface)
    .border(1.dp, Color(0x22000000), CardShape)

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
            .heightIn(min = 60.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (color == Color.Unspecified) scheme.primary else color,
            contentColor = if (contentColor == Color.Unspecified) scheme.onPrimary else contentColor,
        ),
    ) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
fun SubButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    BigButton(
        text = text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = onClick,
    )

@Composable
fun SmallChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text, color = MaterialTheme.colorScheme.onPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

private val AvatarColors = listOf(
    Color(0xFF2E7D32), Color(0xFF1565C0), Color(0xFFAD1457),
    Color(0xFFEF6C00), Color(0xFF6A1B9A), Color(0xFF00838F),
)

@Composable
fun Avatar(name: String, size: Dp) {
    val color = AvatarColors[(name.hashCode() and 0x7fffffff) % AvatarColors.size]
    val textSize = with(LocalDensity.current) { (size * 0.45f).toSp() }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1), color = Color.White, fontSize = textSize, fontWeight = FontWeight.Bold)
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
    val b = bitmap
    if (b != null) {
        Image(bitmap = b, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(size / 4))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

@Composable
fun AddIcon(size: Dp) {
    val textSize = with(LocalDensity.current) { (size * 0.6f).toSp() }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text("＋", color = Color.White, fontSize = textSize, fontWeight = FontWeight.Bold)
    }
}

/** ホームの1マス。ラベルは常に2行分の高さを取るので、マスの高さがそろう。 */
@Composable
fun Tile(
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    icon: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(if (highlight) Color(0xFFFFF3C4) else scheme.surface)
            .border(if (highlight) 3.dp else 1.dp, if (highlight) Color(0xFFE0A100) else Color(0x22000000), CardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            fontSize = 17.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = scheme.onSurface,
        )
    }
}

/**
 * 空きマス。ふだんは見えない（場所だけ取る）。
 * 移動中は「ここへ」と表示して、押すとそこへ動かせる。
 */
@Composable
fun EmptySlot(iconSize: Dp, active: Boolean, onClick: () -> Unit) {
    val shape = CardShape
    val base = Modifier
        .fillMaxWidth()
        .clip(shape)
    val mod = if (active) {
        base
            .background(Color(0xFFEFF4FB))
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick)
    } else base
    Box(mod.padding(vertical = 14.dp, horizontal = 6.dp), contentAlignment = Alignment.Center) {
        // Tile と同じ高さにするための見えない中身
        Column(Modifier.alpha(0f), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.size(iconSize))
            Spacer(Modifier.height(8.dp))
            Text(" ", fontSize = 17.sp, lineHeight = 21.sp, minLines = 2, maxLines = 2)
        }
        if (active) {
            Text("ここへ", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ClockHeader() {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val n = LocalDateTime.now()
            now = n
            delay((60 - n.second) * 1000L)
        }
    }
    val time = remember(now) { now.format(DateTimeFormatter.ofPattern("H:mm")) }
    val date = remember(now) { now.format(DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPANESE)) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(time, fontSize = 60.sp, lineHeight = 64.sp, fontWeight = FontWeight.Bold)
        Text(date, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}
