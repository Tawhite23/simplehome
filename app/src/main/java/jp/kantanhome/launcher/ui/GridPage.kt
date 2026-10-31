package jp.kantanhome.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.kantanhome.launcher.data.DOCK_SIZE
import jp.kantanhome.launcher.data.HomeItem
import jp.kantanhome.launcher.data.Page

fun iconSizeFor(columns: Int): Dp = when (columns) {
    2 -> 96.dp
    3 -> 74.dp
    else -> 60.dp
}

/**
 * アプリ・Webショートカット・連絡先を同じマス目に混ぜて並べるページ。
 *
 * 並べ替えは「入れ替え」方式：
 *  長押し →「うごかす」→ 動かしたい先のマスを押す
 *  → 先にあったアイコンが元の場所に入る（空きマスならそこへ移るだけ）。ほかは動かない。
 */
@Composable
fun GridPage(
    page: Page,
    showClock: Boolean,
    columns: Int,
    labelFor: (HomeItem) -> String,
    movingIndex: Int?,          // このページで移動中のマス（なければ null）
    isMoving: Boolean,          // どこかで移動中か
    onClick: (HomeItem) -> Unit,
    onLongClick: (HomeItem) -> Unit,
    onSlotTap: (Int) -> Unit,   // 移動中に押されたマス
    onAdd: () -> Unit,
) {
    val iconSize = iconSizeFor(columns)
    // 移動中は、今の行の残り＋もう1行ぶん空きマスを見せて、そこへも置けるようにする
    val slots: List<HomeItem?> = if (isMoving) {
        val base = page.items.size
        val rows = (base + columns - 1) / columns
        val total = (rows + 1) * columns
        page.items + List(total - base) { null }
    } else page.items

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showClock) {
            item(key = "clock", span = { GridItemSpan(maxLineSpan) }) { ClockWidget() }
        } else {
            item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    page.title,
                    style = OnWallpaper,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 4.dp),
                )
            }
        }
        items(count = slots.size, key = { i -> slots[i]?.key ?: "empty-$i" }) { i ->
            val item = slots[i]
            if (item == null) {
                EmptySlot(iconSize = iconSize, active = isMoving) { onSlotTap(i) }
            } else {
                HomeIcon(
                    label = labelFor(item),
                    iconSize = iconSize,
                    selected = movingIndex == i,
                    onClick = { if (isMoving) onSlotTap(i) else onClick(item) },
                    onLongClick = { if (isMoving) onSlotTap(i) else onLongClick(item) },
                ) { ItemIcon(item, iconSize) }
            }
        }
        if (!isMoving) {
            item(key = "add") { DashedSlot(iconSize, "ついか", circle = false, onClick = onAdd) }
        }
    }
}

/**
 * 画面下の「よく連絡する人」。らくらくホームの下段の連絡先と同じ役目。
 * 4枠固定で、ホームのマスとも入れ替えられる（アプリを置いてもよい）。
 */
@Composable
fun Dock(
    page: Page,
    labelFor: (HomeItem) -> String,
    movingIndex: Int?,
    isMoving: Boolean,
    onClick: (HomeItem) -> Unit,
    onLongClick: (HomeItem) -> Unit,
    onSlotTap: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    val size: Dp = 62.dp
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .glass(RoundedCornerShape(34.dp), 0.26f)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in 0 until DOCK_SIZE) {
                Column(Modifier.weight(1f)) {
                    val item = page.items.getOrNull(i)
                    when {
                        item != null -> HomeIcon(
                            label = labelFor(item),
                            iconSize = size,
                            selected = movingIndex == i,
                            onClick = { if (isMoving) onSlotTap(i) else onClick(item) },
                            onLongClick = { if (isMoving) onSlotTap(i) else onLongClick(item) },
                        ) { ItemIcon(item, size) }

                        isMoving -> DashedSlot(size, "ここへ", circle = true) { onSlotTap(i) }
                        else -> DashedSlot(size, "ついか", circle = true, onClick = onAdd)
                    }
                }
            }
        }
    }
}
