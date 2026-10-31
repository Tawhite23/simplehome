@file:OptIn(ExperimentalFoundationApi::class)

package jp.kantanhome.launcher.ui

import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.kantanhome.launcher.data.AppCatalog
import jp.kantanhome.launcher.data.AppEntry
import jp.kantanhome.launcher.data.HomeItem
import jp.kantanhome.launcher.data.HomeStore
import jp.kantanhome.launcher.data.PageKind
import kotlinx.coroutines.launch

/** 画面から Activity に頼む操作 */
class HomeActions(
    val launch: (HomeItem) -> Unit,
    val call: (HomeItem.Contact) -> Unit,
    val pickContact: (Long) -> Unit,
    val addWidget: (Long, AppWidgetProviderInfo) -> Unit,
    val openHomeSettings: () -> Unit,
    val openSystemSettings: () -> Unit,
    val openChrome: () -> Unit,
)

/** 移動中のアイコン：どのページの何番目のマスか */
private data class Moving(val pageId: Long, val index: Int)

@Composable
fun HomeScreen(actions: HomeActions, homeSignal: Int) {
    val pages = HomeStore.pages
    val settings = HomeStore.settings
    val apps = AppCatalog.apps

    val homeIndex = pages.indexOfFirst { it.kind == PageKind.GRID }.coerceAtLeast(0)
    val mainGridId = pages.firstOrNull { it.kind == PageKind.GRID }?.id
    val pagerState = rememberPagerState(initialPage = homeIndex) { HomeStore.pages.size }
    val scope = rememberCoroutineScope()

    val labels = remember(apps) { apps.associate { it.key to it.label } }
    val labelFor: (HomeItem) -> String = { item ->
        when (item) {
            is HomeItem.App -> labels[item.key] ?: item.label
            is HomeItem.Shortcut -> item.label
            is HomeItem.Contact -> item.name
            is HomeItem.Widget -> "ウィジェット"
        }
    }

    var moving by remember { mutableStateOf<Moving?>(null) }
    var menuFor by remember { mutableStateOf<Pair<Long, HomeItem>?>(null) }
    var addChooserFor by remember { mutableStateOf<Long?>(null) }
    var appPickerFor by remember { mutableStateOf<Long?>(null) }
    var widgetPickerFor by remember { mutableStateOf<Long?>(null) }
    var showWebHelp by remember { mutableStateOf(false) }
    var addToHome by remember { mutableStateOf<AppEntry?>(null) }
    var callTarget by remember { mutableStateOf<HomeItem.Contact?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    // ホームボタン：移動をやめて「ホーム」ページへ
    LaunchedEffect(homeSignal) {
        if (homeSignal > 0) {
            moving = null
            pagerState.animateScrollToPage(homeIndex)
        }
    }
    // 戻るボタン：移動中ならやめる。そうでなければ「ホーム」ページへ
    BackHandler {
        if (moving != null) moving = null
        else scope.launch { pagerState.animateScrollToPage(homeIndex) }
    }

    val onItemClick: (HomeItem) -> Unit = { item ->
        when (item) {
            is HomeItem.Contact -> callTarget = item
            is HomeItem.Widget -> Unit
            else -> actions.launch(item)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        moving?.let { m ->
            val name = pages.find { it.id == m.pageId }?.items?.getOrNull(m.index)?.let(labelFor) ?: ""
            MovingBanner(name) { moving = null }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            key = { HomeStore.pages.getOrNull(it)?.id ?: it },
        ) { index ->
            val page = pages.getOrNull(index) ?: return@HorizontalPager
            when (page.kind) {
                PageKind.GRID -> GridPage(
                    page = page,
                    showClock = page.id == mainGridId,
                    columns = settings.columns,
                    labelFor = labelFor,
                    movingIndex = moving?.takeIf { it.pageId == page.id }?.index,
                    isMoving = moving != null,
                    onClick = onItemClick,
                    onLongClick = { menuFor = page.id to it },
                    onSlotTap = { target ->
                        val m = moving
                        if (m != null) {
                            if (!(m.pageId == page.id && m.index == target)) {
                                HomeStore.swapSlots(m.pageId, m.index, page.id, target)
                            }
                            moving = null
                        }
                    },
                    onAdd = { addChooserFor = page.id },
                )

                PageKind.CONTACTS -> ContactsPage(
                    page = page,
                    onClick = { callTarget = it },
                    onLongClick = { menuFor = page.id to it },
                    onAdd = { actions.pickContact(page.id) },
                )

                PageKind.WIDGETS -> WidgetsPage(
                    page = page,
                    onMenu = { menuFor = page.id to it },
                    onAdd = { widgetPickerFor = page.id },
                )

                PageKind.ALL_APPS -> AllAppsPage(
                    apps = apps,
                    onLaunch = { actions.launch(it.toItem()) },
                    onLongClick = { addToHome = it },
                    onSettings = { showSettings = true },
                )
            }
        }

        PageTabs(pages.map { it.title }, pagerState.currentPage) { i ->
            scope.launch { pagerState.animateScrollToPage(i) }
        }
    }

    // ---------------- ダイアログ ----------------

    menuFor?.let { (pageId, item) ->
        val page = pages.find { it.id == pageId }
        if (page != null) {
            ItemMenuDialog(
                page = page,
                item = item,
                label = labelFor(item),
                pages = pages,
                onStartMove = { index -> moving = Moving(page.id, index) },
                onDismiss = { menuFor = null },
            )
        }
    }

    addChooserFor?.let { pageId ->
        AddChooserDialog(
            onApp = { addChooserFor = null; appPickerFor = pageId },
            onContact = { addChooserFor = null; actions.pickContact(pageId) },
            onWeb = { addChooserFor = null; showWebHelp = true },
            onDismiss = { addChooserFor = null },
        )
    }

    appPickerFor?.let { pageId ->
        val existing = pages.find { it.id == pageId }?.filled.orEmpty().map { it.key }.toSet()
        AppPickerDialog(
            apps = apps,
            existing = existing,
            onPick = { HomeStore.addItem(pageId, it.toItem()) },
            onDismiss = { appPickerFor = null },
        )
    }

    widgetPickerFor?.let { pageId ->
        WidgetPickerDialog(
            onPick = { info -> widgetPickerFor = null; actions.addWidget(pageId, info) },
            onDismiss = { widgetPickerFor = null },
        )
    }

    if (showWebHelp) {
        WebHelpDialog(
            onOpenChrome = { showWebHelp = false; actions.openChrome() },
            onDismiss = { showWebHelp = false },
        )
    }

    addToHome?.let { entry ->
        AddToHomeDialog(entry, pages.filter { it.kind == PageKind.GRID }) { addToHome = null }
    }

    callTarget?.let { c ->
        CallConfirmDialog(c, onCall = { callTarget = null; actions.call(c) }, onDismiss = { callTarget = null })
    }

    if (showSettings) {
        SettingsDialog(actions) { showSettings = false }
    }
}

@Composable
private fun MovingBanner(name: String, onCancel: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF3C4))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "「$name」を動かします。\n動かしたい先のマスを押してください",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 23.sp,
            modifier = Modifier.weight(1f),
        )
        SmallChip("やめる", onClick = onCancel)
    }
}

/** 画面下のページ切り替え。スワイプが苦手でも押すだけで移れる。 */
@Composable
private fun PageTabs(titles: List<String>, current: Int, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val scroll = rememberScrollState()
    val fits = titles.size <= 4
    val rowModifier = Modifier
        .fillMaxWidth()
        .background(scheme.surface)
        .padding(6.dp)
    Row(
        if (fits) rowModifier else rowModifier.horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        titles.forEachIndexed { i, title ->
            val selected = i == current
            val size = if (fits) Modifier.weight(1f) else Modifier.widthIn(min = 96.dp)
            Box(
                size
                    .height(60.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) scheme.primary else scheme.surfaceVariant)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title,
                    color = if (selected) scheme.onPrimary else scheme.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}
