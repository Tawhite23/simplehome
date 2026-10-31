@file:OptIn(ExperimentalFoundationApi::class)

package jp.kantanhome.launcher.ui

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import jp.kantanhome.launcher.data.AppEntry
import jp.kantanhome.launcher.data.HomeItem
import jp.kantanhome.launcher.data.HomeStore
import jp.kantanhome.launcher.data.Page

// ----------------------------------------------------------------------
// でんわ（連絡先）ページ
// ----------------------------------------------------------------------

@Composable
fun ContactsPage(
    page: Page,
    onClick: (HomeItem.Contact) -> Unit,
    onLongClick: (HomeItem.Contact) -> Unit,
    onAdd: () -> Unit,
) {
    val contacts = page.filled.filterIsInstance<HomeItem.Contact>()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "hint") {
            Text(
                "名前を押すと電話をかけます",
                fontSize = 18.sp,
                color = SubText,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        items(contacts, key = { it.key }) { c ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .card()
                    .combinedClickable(onClick = { onClick(c) }, onLongClick = { onLongClick(c) })
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(c.name, 64.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(c.number, fontSize = 17.sp, color = SubText)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "でんわ",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Green)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        item(key = "add") { BigButton("＋ れんらく先をついか", onClick = onAdd) }
    }
}

// ----------------------------------------------------------------------
// べんり（ウィジェット）ページ
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
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (widgets.isEmpty()) {
            item(key = "hint") {
                Text(
                    "時計・天気・カレンダーなどを置けます",
                    fontSize = 18.sp,
                    color = SubText,
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
        items(widgets, key = { it.key }) { w ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .card()
                    .padding(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val label = remember(w.widgetId) { widgetLabel(context, w.widgetId) }
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    SmallChip("へんしゅう") { onMenu(w) }
                }
                Spacer(Modifier.height(6.dp))
                WidgetView(w)
            }
        }
        item(key = "add") { BigButton("＋ ウィジェットをついか", onClick = onAdd) }
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
            fontSize = 17.sp,
            modifier = Modifier.padding(12.dp),
        )
        return
    }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(item.heightDp.dp),
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
// アプリ一覧ページ
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
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "header") {
            Column(Modifier.padding(bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("アプリ一覧", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    SmallChip("⚙ せってい", onClick = onSettings)
                }
                Text("長押しでホームに置けます", fontSize = 16.sp, color = SubText)
            }
        }
        items(apps, key = { it.key }) { a ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .card()
                    .combinedClickable(onClick = { onLaunch(a) }, onLongClick = { onLongClick(a) })
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ItemIcon(a.toItem(), 48.dp)
                Spacer(Modifier.width(14.dp))
                Text(a.label, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
