package jp.kantanhome.launcher.ui

import android.appwidget.AppWidgetProviderInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

/** 入れ替え中のアイコン：どのページ（ドック含む）の何番目のマスか */
private data class Moving(val pageId: Long, val index: Int)

@Composable
fun HomeScreen(actions: HomeActions, homeSignal: Int) {
    val context = LocalContext.current
    val allPages = HomeStore.pages
    val pages = HomeStore.pagerPages
    val dock = HomeStore.dock
    val settings = HomeStore.settings
    val apps = AppCatalog.apps

    val homeIndex = pages.indexOfFirst { it.kind == PageKind.GRID }.coerceAtLeast(0)
    val mainGridId = pages.firstOrNull { it.kind == PageKind.GRID }?.id
    val pagerState = rememberPagerState(initialPage = homeIndex) { HomeStore.pagerPages.size }
    val scope = rememberCoroutineScope()
    val currentKind = pages.getOrNull(pagerState.currentPage)?.kind

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

    // ホームボタン：入れ替えをやめて「ホーム」へ
    LaunchedEffect(homeSignal) {
        if (homeSignal > 0) {
            moving = null
            pagerState.animateScrollToPage(homeIndex)
        }
    }
    // 戻るボタン：入れ替え中ならやめる。そうでなければ「ホーム」へ
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
    val onSlotTap: (Long, Int) -> Unit = { pageId, target ->
        val m = moving
        if (m != null) {
            if (!(m.pageId == pageId && m.index == target)) {
                if (!HomeStore.swapSlots(m.pageId, m.index, pageId, target)) {
                    Toast.makeText(context, "そこには置けません（同じものがあります）", Toast.LENGTH_SHORT).show()
                }
            }
            moving = null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(wallpaperBrush(settings.wallpaper)),
    ) {
        // 上下を少しだけ暗くして、白い文字を読みやすくする
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color(0x33000000), Color.Transparent, Color(0x40000000)),
                    ),
                ),
        )
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            moving?.let { m ->
                val name = allPages.find { it.id == m.pageId }?.items?.getOrNull(m.index)?.let(labelFor) ?: ""
                MovingBanner(name) { moving = null }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                key = { HomeStore.pagerPages.getOrNull(it)?.id ?: it },
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
                        onSlotTap = { onSlotTap(page.id, it) },
                        onAdd = { addChooserFor = page.id },
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

                    PageKind.DOCK -> Unit
                }
            }

            PageDots(pages.size, pagerState.currentPage) { i ->
                scope.launch { pagerState.animateScrollToPage(i) }
            }

            if (dock != null && currentKind == PageKind.GRID) {
                Dock(
                    page = dock,
                    labelFor = labelFor,
                    movingIndex = moving?.takeIf { it.pageId == dock.id }?.index,
                    isMoving = moving != null,
                    onClick = onItemClick,
                    onLongClick = { menuFor = dock.id to it },
                    onSlotTap = { onSlotTap(dock.id, it) },
                    onAdd = { addChooserFor = dock.id },
                )
            }
        }
    }

    // ---------------- ダイアログ ----------------

    menuFor?.let { (pageId, item) ->
        val page = allPages.find { it.id == pageId }
        if (page != null) {
            ItemMenuDialog(
                page = page,
                item = item,
                label = labelFor(item),
                onStartMove = { index -> moving = Moving(page.id, index) },
                onDismiss = { menuFor = null },
            )
        }
    }

    addChooserFor?.let { pageId ->
        val isDock = pageId == dock?.id
        AddChooserDialog(
            showWeb = !isDock,
            onApp = { addChooserFor = null; appPickerFor = pageId },
            onContact = { addChooserFor = null; actions.pickContact(pageId) },
            onWeb = { addChooserFor = null; showWebHelp = true },
            onDismiss = { addChooserFor = null },
        )
    }

    appPickerFor?.let { pageId ->
        val existing = allPages.find { it.id == pageId }?.filled.orEmpty().map { it.key }.toSet()
        AppPickerDialog(
            apps = apps,
            existing = existing,
            onPick = {
                if (!HomeStore.addItem(pageId, it.toItem())) {
                    Toast.makeText(context, "いっぱいです。どれかを外してください", Toast.LENGTH_SHORT).show()
                    appPickerFor = null
                }
            },
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
        val targets = allPages.filter { it.kind == PageKind.GRID } + listOfNotNull(dock)
        AddToHomeDialog(entry, targets) { addToHome = null }
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
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .glass(RoundedCornerShape(22.dp), 0.32f)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "「$name」を入れかえます。\n動かしたい先を押してください",
            style = OnWallpaper,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 23.sp,
            modifier = Modifier.weight(1f),
        )
        GlassChip("やめる", onClick = onCancel)
    }
}

/** iPhone のページの点。点を押してもそのページへ移れる（押しやすいよう当たり判定は大きめ）。 */
@Composable
private fun PageDots(count: Int, current: Int, onSelect: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .padding(vertical = 4.dp)
                .glass(RoundedCornerShape(50), 0.18f)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(count) { i ->
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(if (i == current) 11.dp else 9.dp)
                            .background(Color.White.copy(alpha = if (i == current) 1f else 0.45f), CircleShape),
                    )
                }
            }
        }
    }
}
