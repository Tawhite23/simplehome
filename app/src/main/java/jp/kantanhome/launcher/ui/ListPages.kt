@file:OptIn(ExperimentalFoundationApi::class)

package jp.kantanhome.launcher.ui

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import jp.kantanhome.launcher.data.AppEntry
import jp.kantanhome.launcher.data.HomeItem
import jp.kantanhome.launcher.data.HomeStore
import jp.kantanhome.launcher.data.Page

// ----------------------------------------------------------------------
// ウィジェットページ（いちばん左。iPhone の「今日の表示」）
// ----------------------------------------------------------------------

fun widgetLabel(context: Context, widgetId: Int): String =
    runCatching {
        AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId)?.loadLabel(context.packageManager)
    }.getOrNull() ?: "ウィジェット"

@Composable
fun WidgetsPage(
    page: Page,
    onMenu: (HomeItem.Widget) -> Unit,
    onAdd: () -> Unit,
) {
    val context = LocalContext.current
    val widgets = page.filled.filterIsInstance<HomeItem.Widget>()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Text(
                "ウィジェット",
                style = OnWallpaper,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp, top = 6.dp),
            )
        }
        if (widgets.isEmpty()) {
            item(key = "hint") {
                Text(
                    "時計・天気・カレンダーなどを置けます",
                    style = OnWallpaper,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
        items(widgets, key = { it.key }) { w ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(26.dp))
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val label = remember(w.widgetId) { widgetLabel(context, w.widgetId) }
                    Text(
                        label,
                        style = OnWallpaper,
                        modifier = Modifier.weight(1f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    GlassChip("へんしゅう") { onMenu(w) }
                }
                Spacer(Modifier.height(8.dp))
                WidgetView(w)
            }
        }
        item(key = "add") {
            Text(
                "＋ ウィジェットをついか",
                style = OnWallpaper,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(22.dp), 0.28f)
                    .clickable(onClick = onAdd)
                    .padding(vertical = 18.dp),
            )
        }
    }
}

@Composable
private fun WidgetView(item: HomeItem.Widget) {
    val context = LocalContext.current
    val info = remember(item.widgetId) {
        runCatching { AppWidgetManager.getInstance(context).getAppWidgetInfo(item.widgetId) }.getOrNull()
    }
    if (info == null) {
        Text(
            "表示できません。「へんしゅう」から外してください",
            style = OnWallpaper,
            fontSize = 17.sp,
            modifier = Modifier.padding(12.dp),
        )
        return
    }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(item.heightDp.dp)
            .clip(RoundedCornerShape(18.dp)),
    ) {
        val wDp = maxWidth.value
        val hDp = item.heightDp.toFloat()
        AndroidView(
            factory = { c -> HomeStore.widgetHost.createView(c, item.widgetId, info) },
            update = { v -> v.updateSize(wDp, hDp) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Suppress("DEPRECATION")
private fun AppWidgetHostView.updateSize(wDp: Float, hDp: Float) {
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            updateAppWidgetSize(Bundle(), listOf(SizeF(wDp, hDp)))
        } else {
            updateAppWidgetSize(null, wDp.toInt(), hDp.toInt(), wDp.toInt(), hDp.toInt())
        }
    }
}

// ----------------------------------------------------------------------
// アプリ一覧ページ（いちばん右。iPhone の「アプリライブラリ」）
// ----------------------------------------------------------------------

@Composable
fun AllAppsPage(
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onLongClick: (AppEntry) -> Unit,
    onSettings: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "header") {
            Column(Modifier.padding(start = 6.dp, top = 6.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "アプリ",
                        style = OnWallpaper,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    GlassChip("せってい", onClick = onSettings)
                }
                Text("長押しでホームに置けます", style = OnWallpaper, fontSize = 16.sp)
            }
        }
        items(apps, key = { it.key }) { a ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(20.dp), 0.18f)
                    .combinedClickable(onClick = { onLaunch(a) }, onLongClick = { onLongClick(a) })
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ItemIcon(a.toItem(), 50.dp)
                Spacer(Modifier.width(14.dp))
                Text(
                    a.label,
                    style = OnWallpaper,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
