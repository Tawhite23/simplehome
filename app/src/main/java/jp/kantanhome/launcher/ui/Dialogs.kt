package jp.kantanhome.launcher.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Process
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import jp.kantanhome.launcher.data.AppEntry
import jp.kantanhome.launcher.data.HomeItem
import jp.kantanhome.launcher.data.HomeStore
import jp.kantanhome.launcher.data.Page
import jp.kantanhome.launcher.data.PageKind
import java.text.Collator
import java.util.Locale
import kotlin.math.abs

private val RowShape = RoundedCornerShape(16.dp)

private fun Modifier.listRow(): Modifier = this
    .clip(RowShape)
    .background(Color.White)
    .border(1.dp, Color(0x14000000), RowShape)

// ----------------------------------------------------------------------
// 共通の枠
// ----------------------------------------------------------------------

@Composable
fun BigDialog(
    title: String,
    onDismiss: () -> Unit,
    dismissText: String = "とじる",
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(30.dp),
            color = Color(0xFFF2F2F7),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp, color = Ink)
                content()
                SubButton(dismissText, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun FullDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFF2F2F7)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
                content()
                SubButton("とじる", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun ChoiceButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) BigButton(text, modifier, onClick = onClick)
    else SubButton(text, modifier, onClick = onClick)
}

// ----------------------------------------------------------------------
// 長押しメニュー
// ----------------------------------------------------------------------

@Composable
fun ItemMenuDialog(
    page: Page,
    item: HomeItem,
    label: String,
    onStartMove: (Int) -> Unit,
    onEditIcon: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val title = if (item is HomeItem.Widget) widgetLabel(context, item.widgetId) else label

    BigDialog(title, onDismiss) {
        if (item !is HomeItem.Widget) {
            BigButton("アイコン・名前をかえる", onClick = onEditIcon)
        }
        if (page.kind.let { it == PageKind.GRID || it == PageKind.DOCK }) {
            BigButton("うごかす（入れかえ）") {
                val index = page.items.indexOfFirst { it?.key == item.key }
                if (index >= 0) onStartMove(index)
                onDismiss()
            }
            Text(
                "押したあと、動かしたい先のマスを押します。そこにあったアイコンと場所が入れかわります。" +
                    "ほかのホームページや、下の「よく連絡する人」の枠とも入れかえられます。",
                fontSize = 16.sp,
                lineHeight = 23.sp,
                color = SubText,
            )
        } else if (item is HomeItem.Widget) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SubButton("▲ 上へ", Modifier.weight(1f)) { HomeStore.moveInList(page.id, item.key, -1) }
                SubButton("▼ 下へ", Modifier.weight(1f)) { HomeStore.moveInList(page.id, item.key, 1) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SubButton("大きく", Modifier.weight(1f)) { HomeStore.resizeWidget(page.id, item.widgetId, 60) }
                SubButton("小さく", Modifier.weight(1f)) { HomeStore.resizeWidget(page.id, item.widgetId, -60) }
            }
        }
        BigButton("ホームから外す", color = Red) {
            HomeStore.removeItem(page.id, item)
            onDismiss()
        }
        if (item is HomeItem.App || item is HomeItem.Shortcut) {
            Text("※ 外してもアプリは消えません。右はしの「アプリ」にあります。", fontSize = 15.sp, color = SubText)
        }
    }
}

// ----------------------------------------------------------------------
// 追加まわり
// ----------------------------------------------------------------------

@Composable
fun AddChooserDialog(
    showWeb: Boolean,
    onApp: () -> Unit,
    onContact: () -> Unit,
    onWeb: () -> Unit,
    onDismiss: () -> Unit,
) {
    BigDialog("何をおきますか？", onDismiss) {
        BigButton("れんらく先（電話）", onClick = onContact)
        BigButton("アプリ", onClick = onApp)
        if (showWeb) BigButton("ウェブサイト（Chromeのお気に入り）", onClick = onWeb)
    }
}

@Composable
fun AppPickerDialog(apps: List<AppEntry>, existing: Set<String>, onPick: (AppEntry) -> Unit, onDismiss: () -> Unit) {
    FullDialog("おくアプリをえらぶ", onDismiss) {
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(apps, key = { it.key }) { a ->
                val added = a.key in existing
                Row(
                    Modifier
                        .fillMaxWidth()
                        .listRow()
                        .clickable(enabled = !added) { onPick(a) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ItemIcon(a.toItem(), 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        a.label,
                        modifier = Modifier.weight(1f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (added) Text("置いてあります", fontSize = 15.sp, color = Green, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private class WidgetChoice(val info: AppWidgetProviderInfo, val label: String, val app: String)

@Composable
fun WidgetPickerDialog(onPick: (AppWidgetProviderInfo) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val choices = remember {
        val pm = context.packageManager
        val collator = Collator.getInstance(Locale.JAPANESE)
        runCatching {
            AppWidgetManager.getInstance(context)
                .getInstalledProvidersForProfile(Process.myUserHandle())
                .map { info ->
                    val pkg = info.provider.packageName
                    @Suppress("DEPRECATION")
                    val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                        .getOrDefault(pkg)
                    WidgetChoice(info, info.loadLabel(pm).orEmpty(), app)
                }
                .sortedWith { a, b -> collator.compare(a.app + " " + a.label, b.app + " " + b.label) }
        }.getOrDefault(emptyList())
    }
    FullDialog("おくウィジェットをえらぶ", onDismiss) {
        if (choices.isEmpty()) Text("置けるウィジェットがありません", fontSize = 18.sp, color = Ink)
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(choices, key = { it.info.provider.flattenToString() }) { c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .listRow()
                        .clickable { onPick(c.info) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ItemIcon(HomeItem.App(c.info.provider.packageName, "", c.app), 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.label, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(c.app, fontSize = 15.sp, color = SubText)
                    }
                }
            }
        }
    }
}

@Composable
fun WebHelpDialog(onOpenChrome: () -> Unit, onDismiss: () -> Unit) {
    BigDialog("よく見るサイトをホームに置く", onDismiss) {
        Text(
            "1. 下の「Chromeをひらく」を押す\n" +
                "2. 置きたいサイトを開く（お気に入りからでもOK）\n" +
                "3. 右上の「︙」→「ホーム画面に追加」→「追加」\n\n" +
                "ホームの空いているマスに入ります。アプリと同じように入れかえできます。",
            fontSize = 19.sp,
            lineHeight = 28.sp,
            color = Ink,
        )
        BigButton("Chromeをひらく", onClick = onOpenChrome)
    }
}

@Composable
fun AddToHomeDialog(entry: AppEntry, targets: List<Page>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    BigDialog("「${entry.label}」をどこに置きますか？", onDismiss) {
        targets.forEach { p ->
            SubButton(if (p.kind == PageKind.DOCK) "下の「よく連絡する人」の枠" else p.title) {
                val msg = when {
                    HomeStore.addItem(p.id, entry.toItem()) -> "置きました"
                    HomeStore.contains(p.id, entry.key) -> "もう置いてあります"
                    else -> "いっぱいです"
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        }
    }
}

@Composable
fun CallConfirmDialog(contact: HomeItem.Contact, onCall: () -> Unit, onDismiss: () -> Unit) {
    BigDialog("${contact.name} さんに\n電話をかけますか？", onDismiss, dismissText = "やめる") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Avatar(contact.name, 96.dp) }
        Text(
            contact.number,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = Ink,
            modifier = Modifier.fillMaxWidth(),
        )
        BigButton("電話をかける", color = Green, onClick = onCall)
    }
}

// ----------------------------------------------------------------------
// 設定
// ----------------------------------------------------------------------

@Composable
fun SettingsDialog(actions: HomeActions, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val s = HomeStore.settings
    val gridPages = HomeStore.pages.filter { it.kind == PageKind.GRID }

    BigDialog("ホームのせってい", onDismiss) {
        Text("かべがみ", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Wallpapers.forEachIndexed { i, w ->
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { HomeStore.updateSettings(s.copy(wallpaper = i)) }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(wallpaperBrush(i))
                            .border(if (s.wallpaper == i) 4.dp else 0.dp, Blue, CircleShape),
                    )
                    Text(w.name, fontSize = 15.sp, color = Ink)
                }
            }
        }

        Text("文字の大きさ", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ふつう" to 1.0f, "大きい" to 1.15f, "特大" to 1.3f).forEach { (name, v) ->
                ChoiceButton(name, abs(s.fontScale - v) < 0.01f, Modifier.weight(1f)) {
                    HomeStore.updateSettings(s.copy(fontScale = v))
                }
            }
        }

        Text("横にならべる数", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(2, 3, 4).forEach { n ->
                ChoiceButton("$n", s.columns == n, Modifier.weight(1f)) {
                    HomeStore.updateSettings(s.copy(columns = n))
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 56.dp)) {
            Text("新しく入れたアプリを自動でホームに置く", fontSize = 18.sp, color = Ink, modifier = Modifier.weight(1f))
            Switch(
                checked = s.autoAddNewApps,
                onCheckedChange = { HomeStore.updateSettings(s.copy(autoAddNewApps = it)) },
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 56.dp)) {
            Column(Modifier.weight(1f)) {
                Text("アイコンの形をそろえる", fontSize = 18.sp, color = Ink)
                Text("丸いアイコンも角丸いっぱいに表示します", fontSize = 14.sp, color = SubText)
            }
            Switch(
                checked = s.shapeIcons,
                onCheckedChange = { HomeStore.updateSettings(s.copy(shapeIcons = it)) },
            )
        }

        Text("ホームのページ", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        SubButton("＋ ホームのページを増やす") { HomeStore.addGridPage() }
        gridPages.drop(1).forEach { p ->
            SubButton("「${p.title}」を消す") {
                if (!HomeStore.removePage(p.id)) {
                    Toast.makeText(context, "中のアイコンを先に外してください", Toast.LENGTH_SHORT).show()
                }
            }
        }

        Text("その他", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        SubButton("元のホームにもどす") { actions.openHomeSettings() }
        SubButton("スマホの設定をひらく") { actions.openSystemSettings() }
        Text("かんたんホーム 1.2", fontSize = 14.sp, color = SubText)
    }
}
