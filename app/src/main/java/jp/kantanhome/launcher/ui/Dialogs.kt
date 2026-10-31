package jp.kantanhome.launcher.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Process
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import jp.kantanhome.launcher.data.accepts
import java.text.Collator
import java.util.Locale
import kotlin.math.abs

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
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
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
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
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
    pages: List<Page>,
    onStartMove: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var choosingPage by remember { mutableStateOf(false) }
    val title = if (item is HomeItem.Widget) widgetLabel(context, item.widgetId) else label
    // GRID 同士の移動は「うごかす（入れかえ）」で行うので、ここでは種類の違うページだけ
    val targets = pages.filter {
        it.id != page.id && it.kind.accepts(item) &&
            !(page.kind == PageKind.GRID && it.kind == PageKind.GRID)
    }

    BigDialog(title, onDismiss) {
        if (!choosingPage) {
            when {
                page.kind == PageKind.GRID -> {
                    BigButton("うごかす（入れかえ）") {
                        val index = page.items.indexOfFirst { it?.key == item.key }
                        if (index >= 0) onStartMove(index)
                        onDismiss()
                    }
                    Text(
                        "押したあと、動かしたい先のマスを押します。先にあったアイコンと場所が入れかわります（別のホームページへも動かせます）。",
                        fontSize = 16.sp,
                        color = SubText,
                    )
                }

                item is HomeItem.Widget -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SubButton("▲ 上へ", Modifier.weight(1f)) { HomeStore.moveInList(page.id, item.key, -1) }
                        SubButton("▼ 下へ", Modifier.weight(1f)) { HomeStore.moveInList(page.id, item.key, 1) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SubButton("大きく", Modifier.weight(1f)) { HomeStore.resizeWidget(page.id, item.widgetId, 60) }
                        SubButton("小さく", Modifier.weight(1f)) { HomeStore.resizeWidget(page.id, item.widgetId, -60) }
                    }
                }

                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SubButton("▲ 上へ", Modifier.weight(1f)) { HomeStore.moveInList(page.id, item.key, -1) }
                        SubButton("▼ 下へ", Modifier.weight(1f)) { HomeStore.moveInList(page.id, item.key, 1) }
                    }
                }
            }
            if (targets.isNotEmpty()) {
                SubButton("ほかのページへ移す") { choosingPage = true }
            }
            BigButton("ホームから外す", color = MaterialTheme.colorScheme.error) {
                HomeStore.removeItem(page.id, item)
                onDismiss()
            }
            if (item is HomeItem.App || item is HomeItem.Shortcut) {
                Text("※ 外してもアプリは消えません。「アプリ」ページにあります。", fontSize = 15.sp, color = SubText)
            }
        } else {
            Text("どのページへ移しますか？", fontSize = 19.sp)
            targets.forEach { t ->
                SubButton(t.title) {
                    if (!HomeStore.moveToPage(page.id, item, t.id)) {
                        Toast.makeText(context, "そのページにはもう入っています", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }
            }
            SubButton("もどる") { choosingPage = false }
        }
    }
}

// ----------------------------------------------------------------------
// 追加まわり
// ----------------------------------------------------------------------

@Composable
fun AddChooserDialog(onApp: () -> Unit, onContact: () -> Unit, onWeb: () -> Unit, onDismiss: () -> Unit) {
    BigDialog("何をおきますか？", onDismiss) {
        BigButton("アプリ", onClick = onApp)
        BigButton("れんらく先（電話）", onClick = onContact)
        BigButton("ウェブサイト", onClick = onWeb)
    }
}

@Composable
fun AppPickerDialog(apps: List<AppEntry>, existing: Set<String>, onPick: (AppEntry) -> Unit, onDismiss: () -> Unit) {
    FullDialog("ホームに置くアプリをえらぶ", onDismiss) {
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
                        .card()
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
                        fontWeight = FontWeight.Bold,
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
    FullDialog("置くウィジェットをえらぶ", onDismiss) {
        if (choices.isEmpty()) Text("置けるウィジェットがありません", fontSize = 18.sp)
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
                        .card()
                        .clickable { onPick(c.info) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ItemIcon(HomeItem.App(c.info.provider.packageName, "", c.app), 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.label, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                "2. 置きたいサイトを開く\n" +
                "3. 右上の「︙」→「ホーム画面に追加」→「追加」\n\n" +
                "このホームの「ホーム」ページの空いているマスに入ります。",
            fontSize = 19.sp,
            lineHeight = 28.sp,
        )
        BigButton("Chromeをひらく", onClick = onOpenChrome)
    }
}

@Composable
fun AddToHomeDialog(entry: AppEntry, gridPages: List<Page>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    BigDialog("「${entry.label}」をどこに置きますか？", onDismiss) {
        gridPages.forEach { p ->
            SubButton(p.title) {
                val ok = HomeStore.addItem(p.id, entry.toItem())
                Toast.makeText(context, if (ok) "置きました" else "もう置いてあります", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        }
    }
}

@Composable
fun CallConfirmDialog(contact: HomeItem.Contact, onCall: () -> Unit, onDismiss: () -> Unit) {
    BigDialog("${contact.name} さんに\n電話をかけますか？", onDismiss, dismissText = "やめる") {
        Text(
            contact.number,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
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
        Text("文字の大きさ", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ふつう" to 1.0f, "大きい" to 1.15f, "特大" to 1.3f).forEach { (name, v) ->
                ChoiceButton(name, abs(s.fontScale - v) < 0.01f, Modifier.weight(1f)) {
                    HomeStore.updateSettings(s.copy(fontScale = v))
                }
            }
        }

        Text("横にならべる数", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(2, 3, 4).forEach { n ->
                ChoiceButton("$n", s.columns == n, Modifier.weight(1f)) {
                    HomeStore.updateSettings(s.copy(columns = n))
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 56.dp)) {
            Text("新しく入れたアプリを自動でホームに置く", fontSize = 18.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = s.autoAddNewApps,
                onCheckedChange = { HomeStore.updateSettings(s.copy(autoAddNewApps = it)) },
            )
        }

        Text("ホームのページ", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        SubButton("＋ ホームのページを増やす") { HomeStore.addGridPage() }
        gridPages.drop(1).forEach { p ->
            SubButton("「${p.title}」を消す") {
                if (!HomeStore.removePage(p.id)) {
                    Toast.makeText(context, "中のアイコンを先に外してください", Toast.LENGTH_SHORT).show()
                }
            }
        }

        Text("その他", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        SubButton("元のホームにもどす（ホームアプリの選択）") { actions.openHomeSettings() }
        SubButton("スマホの設定をひらく") { actions.openSystemSettings() }
        Spacer(Modifier.width(0.dp))
        Text("かんたんホーム 1.0", fontSize = 14.sp, color = Color.Gray)
    }
}
