package jp.kantanhome.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.kantanhome.launcher.data.HomeItem
import jp.kantanhome.launcher.data.Page

fun iconSizeFor(columns: Int): Dp = when (columns) {
    2 -> 84.dp
    3 -> 62.dp
    else -> 50.dp
}

/**
 * アプリ・Webショートカット・連絡先を同じマス目に混ぜて並べるページ。
 *
 * 並べ替えは「入れ替え」方式：
 *  長押し →「うごかす」→ 動かしたい先のマスを押す
 *  → 先にあったアイコンが元の場所に入る。空きマスならそこへ移るだけ。ほかは動かない。
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
        val filledRows = (base + columns - 1) / columns
        val total = maxOf(filledRows + 1, 1) * columns
        page.items + List(total - base) { null }
    } else page.items

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showClock) {
            item(key = "clock", span = { GridItemSpan(maxLineSpan) }) { ClockHeader() }
        }
        items(count = slots.size, key = { i -> slots[i]?.key ?: "empty-$i" }) { i ->
            val item = slots[i]
            if (item == null) {
                EmptySlot(iconSize = iconSize, active = isMoving) { onSlotTap(i) }
            } else {
                Tile(
                    label = labelFor(item),
                    highlight = movingIndex == i,
                    onClick = { if (isMoving) onSlotTap(i) else onClick(item) },
                    onLongClick = { if (isMoving) onSlotTap(i) else onLongClick(item) },
                ) { ItemIcon(item, iconSize) }
            }
        }
        if (!isMoving) {
            item(key = "add") {
                Tile(label = "ついか", onClick = onAdd, onLongClick = onAdd) { AddIcon(iconSize) }
            }
        }
    }
}
