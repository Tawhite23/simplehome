package jp.kantanhome.launcher.data

import android.appwidget.AppWidgetHost
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** ホームの配置と設定を持つ。変更は即 SharedPreferences に保存。 */
object HomeStore {
    const val WIDGET_HOST_ID = 2026
    private const val PREFS = "kantan_home"
    private const val KEY_LAYOUT = "layout_v1"
    private const val KEY_SETTINGS = "settings_v1"

    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences

    lateinit var launcherApps: LauncherApps
        private set
    lateinit var widgetHost: AppWidgetHost
        private set

    var pages: List<Page> by mutableStateOf(emptyList())
        private set
    var settings: HomeSettings by mutableStateOf(HomeSettings())
        private set

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        launcherApps = appContext.getSystemService(LauncherApps::class.java)
        widgetHost = AppWidgetHost(appContext, WIDGET_HOST_ID)
        settings = loadSettings()
        pages = loadPages() ?: defaultPages().also { savePages(it) }
        launcherApps.registerCallback(packageCallback)
    }

    // ------------------------------------------------------------------
    // 追加・削除・移動
    // ------------------------------------------------------------------

    fun contains(pageId: Long, key: String): Boolean =
        pages.find { it.id == pageId }?.items?.any { it?.key == key } == true

    /** GRID は最初の空きマスに入れる（なければ末尾）。それ以外は末尾に足す。 */
    fun addItem(pageId: Long, item: HomeItem): Boolean {
        val page = pages.find { it.id == pageId } ?: return false
        if (!page.kind.accepts(item) || contains(pageId, item.key)) return false
        update(pages.map { if (it.id == pageId) it.copy(items = placeIn(it, item)) else it })
        return true
    }

    fun addToMainGrid(item: HomeItem): Boolean {
        val id = pages.firstOrNull { it.kind == PageKind.GRID }?.id ?: return false
        return addItem(id, item)
    }

    /** GRID では空きマスにするだけで、ほかのアイコンは動かさない。 */
    fun removeItem(pageId: Long, item: HomeItem) {
        update(pages.map { if (it.id == pageId) it.copy(items = takeOut(it, item.key)) else it })
        cleanup(item)
    }

    /**
     * マスの入れ替え。from のアイコンを to へ、to にあったもの（空きなら空き）を from へ。
     * 別の GRID ページ同士でも入れ替えられる。周りのアイコンは動かない。
     */
    fun swapSlots(fromPageId: Long, fromIndex: Int, toPageId: Long, toIndex: Int) {
        val from = pages.find { it.id == fromPageId } ?: return
        val to = pages.find { it.id == toPageId } ?: return
        if (from.kind != PageKind.GRID || to.kind != PageKind.GRID) return
        if (fromPageId == toPageId && fromIndex == toIndex) return
        val moving = from.items.getOrNull(fromIndex) ?: return

        if (fromPageId == toPageId) {
            val list = from.items.padTo(maxOf(fromIndex, toIndex) + 1)
            val other = list[toIndex]
            list[toIndex] = moving
            list[fromIndex] = other
            update(pages.map { if (it.id == fromPageId) it.copy(items = list.trimEnd()) else it })
        } else {
            val target = to.items.padTo(toIndex + 1)
            val other = target[toIndex]
            // 別ページに同じものが既にあるなら重複させない
            if (target.any { it?.key == moving.key }) return
            if (other != null && from.items.any { it?.key == other.key && it !== moving }) return
            target[toIndex] = moving
            val source = from.items.toMutableList()
            source[fromIndex] = other
            update(pages.map {
                when (it.id) {
                    fromPageId -> it.copy(items = source.trimEnd())
                    toPageId -> it.copy(items = target.trimEnd())
                    else -> it
                }
            })
        }
    }

    /** リスト型ページ（でんわ・べんり）の並べ替え用：前後に1つずらす */
    fun moveInList(pageId: Long, key: String, delta: Int) {
        update(pages.map { p ->
            if (p.id != pageId) return@map p
            val list = p.items.toMutableList()
            val i = list.indexOfFirst { it?.key == key }
            val j = i + delta
            if (i < 0 || j !in list.indices) p
            else {
                val x = list.removeAt(i)
                list.add(j, x)
                p.copy(items = list)
            }
        })
    }

    /** 種類の違うページへ移す（例：連絡先を「でんわ」から「ホーム」へ） */
    fun moveToPage(fromPageId: Long, item: HomeItem, toPageId: Long): Boolean {
        val target = pages.find { it.id == toPageId } ?: return false
        if (!target.kind.accepts(item) || contains(toPageId, item.key)) return false
        update(pages.map {
            when (it.id) {
                fromPageId -> it.copy(items = takeOut(it, item.key))
                toPageId -> it.copy(items = placeIn(it, item))
                else -> it
            }
        })
        return true
    }

    fun resizeWidget(pageId: Long, widgetId: Int, deltaDp: Int) {
        update(pages.map { p ->
            if (p.id != pageId) p
            else p.copy(items = p.items.map { x ->
                if (x is HomeItem.Widget && x.widgetId == widgetId)
                    x.copy(heightDp = (x.heightDp + deltaDp).coerceIn(80, 600))
                else x
            })
        })
    }

    fun addGridPage() {
        val nextId = (pages.maxOfOrNull { it.id } ?: 0L) + 1
        val n = pages.count { it.kind == PageKind.GRID } + 1
        val insertAt = pages.indexOfLast { it.kind == PageKind.GRID } + 1
        update(pages.toMutableList().apply { add(insertAt, Page(nextId, PageKind.GRID, "ホーム$n")) })
    }

    /** 2枚目以降の GRID ページで、中身が空のときだけ消せる */
    fun removePage(pageId: Long): Boolean {
        val p = pages.find { it.id == pageId } ?: return false
        val firstGrid = pages.firstOrNull { it.kind == PageKind.GRID }?.id
        if (p.kind != PageKind.GRID || p.id == firstGrid || p.filled.isNotEmpty()) return false
        update(pages.filterNot { it.id == pageId })
        return true
    }

    fun updateSettings(s: HomeSettings) {
        settings = s
        prefs.edit().putString(KEY_SETTINGS, JSONObject()
            .put("fontScale", s.fontScale.toDouble())
            .put("columns", s.columns)
            .put("autoAdd", s.autoAddNewApps)
            .toString()).apply()
    }

    fun appItemFor(pkg: String): HomeItem.App? {
        val act = runCatching { launcherApps.getActivityList(pkg, Process.myUserHandle()) }
            .getOrNull()?.firstOrNull() ?: return null
        return HomeItem.App(pkg, act.componentName.className, act.label.toString())
    }

    // ------------------------------------------------------------------
    // 内部処理
    // ------------------------------------------------------------------

    private fun placeIn(page: Page, item: HomeItem): List<HomeItem?> {
        if (page.kind != PageKind.GRID) return page.filled + item
        val list = page.items.toMutableList()
        val empty = list.indexOfFirst { it == null }
        if (empty >= 0) list[empty] = item else list.add(item)
        return list
    }

    private fun takeOut(page: Page, key: String): List<HomeItem?> =
        if (page.kind == PageKind.GRID) page.items.map { if (it?.key == key) null else it }.trimEnd()
        else page.items.filter { it != null && it.key != key }

    private fun cleanup(item: HomeItem) {
        when (item) {
            is HomeItem.Widget -> runCatching { widgetHost.deleteAppWidgetId(item.widgetId) }
            is HomeItem.Shortcut -> syncPinnedShortcuts(item.pkg)
            else -> Unit
        }
    }

    /** ホームに残っているショートカットだけをピン留め状態にする */
    private fun syncPinnedShortcuts(pkg: String) {
        val ids = pages.flatMap { it.filled }
            .filterIsInstance<HomeItem.Shortcut>()
            .filter { it.pkg == pkg }
            .map { it.id }
            .distinct()
        runCatching {
            if (launcherApps.hasShortcutHostPermission()) {
                launcherApps.pinShortcuts(pkg, ids, Process.myUserHandle())
            }
        }
    }

    private fun update(newPages: List<Page>) {
        pages = newPages
        savePages(newPages)
    }

    private fun savePages(list: List<Page>) {
        val arr = JSONArray()
        list.forEach { p ->
            val items = JSONArray()
            p.items.forEach { items.put(it?.toJson() ?: JSONObject.NULL) }
            arr.put(JSONObject()
                .put("id", p.id)
                .put("kind", p.kind.name)
                .put("title", p.title)
                .put("items", items))
        }
        prefs.edit().putString(KEY_LAYOUT, arr.toString()).apply()
    }

    private fun loadPages(): List<Page>? {
        val raw = prefs.getString(KEY_LAYOUT, null) ?: return null
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val kind = PageKind.valueOf(o.getString("kind"))
                val itemsArr = o.optJSONArray("items") ?: JSONArray()
                val items = (0 until itemsArr.length()).map { j ->
                    if (itemsArr.isNull(j)) null else itemFromJson(itemsArr.getJSONObject(j))
                }
                Page(
                    id = o.getLong("id"),
                    kind = kind,
                    title = o.getString("title"),
                    items = if (kind == PageKind.GRID) items.trimEnd() else items.filterNotNull(),
                )
            }
        }.getOrNull()?.takeIf { list -> list.any { it.kind == PageKind.GRID } }
    }

    private fun loadSettings(): HomeSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return HomeSettings()
        return runCatching {
            val o = JSONObject(raw)
            HomeSettings(
                fontScale = o.optDouble("fontScale", 1.15).toFloat(),
                columns = o.optInt("columns", 3).coerceIn(2, 4),
                autoAddNewApps = o.optBoolean("autoAdd", true),
            )
        }.getOrDefault(HomeSettings())
    }

    /** 初回だけ：よく使いそうなアプリを、入っていれば並べておく */
    private fun defaultPages(): List<Page> {
        val pm = appContext.packageManager
        val candidates = mutableListOf<String?>()
        candidates += Intent(Intent.ACTION_DIAL).resolveActivity(pm)?.packageName
        candidates += Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(pm)?.packageName
        candidates += listOf(
            "com.google.android.apps.photos",
            "jp.naver.line.android",
            "com.android.chrome",
            "com.google.android.apps.maps",
            "com.google.android.youtube",
        )
        val items = candidates.filterNotNull().distinct().mapNotNull { appItemFor(it) }
        return listOf(
            Page(1, PageKind.CONTACTS, "でんわ"),
            Page(2, PageKind.GRID, "ホーム", items),
            Page(3, PageKind.WIDGETS, "べんり"),
            Page(4, PageKind.ALL_APPS, "アプリ"),
        )
    }

    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            refreshCaches()
            if (user != Process.myUserHandle()) return
            // 消えたアプリのマスは空きにするだけ（詰めない）
            update(pages.map { p ->
                if (p.kind == PageKind.GRID)
                    p.copy(items = p.items.map { if (it?.packageName == packageName) null else it }.trimEnd())
                else p.copy(items = p.items.filter { it?.packageName != packageName })
            })
        }

        override fun onPackageAdded(packageName: String, user: UserHandle) {
            refreshCaches()
            if (user != Process.myUserHandle() || !settings.autoAddNewApps) return
            if (packageName == appContext.packageName) return
            val item = appItemFor(packageName) ?: return
            if (pages.any { p -> p.items.any { it?.key == item.key } }) return
            val target = pages.lastOrNull { it.kind == PageKind.GRID } ?: return
            addItem(target.id, item)
        }

        override fun onPackageChanged(packageName: String, user: UserHandle) = refreshCaches()

        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            refreshCaches()

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            refreshCaches()
    }

    private fun refreshCaches() {
        IconCache.clear()
        AppCatalog.refresh(appContext)
    }
}

private fun List<HomeItem?>.padTo(size: Int): MutableList<HomeItem?> {
    val list = toMutableList()
    while (list.size < size) list.add(null)
    return list
}

private fun List<HomeItem?>.trimEnd(): List<HomeItem?> {
    var end = size
    while (end > 0 && this[end - 1] == null) end--
    return subList(0, end).toList()
}
