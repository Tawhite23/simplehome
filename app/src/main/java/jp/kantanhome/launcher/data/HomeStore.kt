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
    private const val KEY_LAYOUT = "layout_v2"
    private const val KEY_SETTINGS = "settings_v1"
    private const val KEY_STYLES = "styles_v1"

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

    /** アイコン・名前の変更（アイテムの key ごと） */
    var styles: Map<String, ItemStyle> by mutableStateOf(emptyMap())
        private set

    fun styleOf(key: String): ItemStyle? = styles[key]

    /** そのアイテムの形をそろえるか（アイテムごとの指定 → 全体の設定） */
    fun shapeOf(key: String): Boolean = styles[key]?.shape ?: settings.shapeIcons

    fun setStyle(key: String, style: ItemStyle?) {
        val old = styles[key]
        // 使わなくなった写真アイコンは消す
        if (old?.iconFile != null && old.iconFile != style?.iconFile) {
            runCatching { java.io.File(appContext.filesDir, old.iconFile).delete() }
        }
        styles = if (style == null || style.isEmpty) styles - key else styles + (key to style)
        val o = JSONObject()
        styles.forEach { (k, v) -> o.put(k, v.toJson()) }
        prefs.edit().putString(KEY_STYLES, o.toString()).apply()
    }

    private fun loadStyles(): Map<String, ItemStyle> {
        val raw = prefs.getString(KEY_STYLES, null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { styleFromJson(o.getJSONObject(it)) }
        }.getOrDefault(emptyMap())
    }

    /** 左右にめくるページ（ドック以外） */
    val pagerPages: List<Page> get() = pages.filter { it.kind != PageKind.DOCK }
    val dock: Page? get() = pages.firstOrNull { it.kind == PageKind.DOCK }

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        launcherApps = appContext.getSystemService(LauncherApps::class.java)
        widgetHost = AppWidgetHost(appContext, WIDGET_HOST_ID)
        settings = loadSettings()
        styles = loadStyles()
        pages =loadPages() ?: defaultPages().also { savePages(it) }
        launcherApps.registerCallback(packageCallback)
    }

    // ------------------------------------------------------------------
    // 追加・削除・入れ替え
    // ------------------------------------------------------------------

    fun contains(pageId: Long, key: String): Boolean =
        pages.find { it.id == pageId }?.items?.any { it?.key == key } == true

    /**
     * GRID / DOCK は最初の空きマスに入れる（GRID は空きがなければ末尾）。
     * ドックが4枠ともうまっているときは入れられない（false）。
     */
    fun addItem(pageId: Long, item: HomeItem): Boolean {
        val page = pages.find { it.id == pageId } ?: return false
        if (!page.kind.accepts(item) || contains(pageId, item.key)) return false
        val placed = placeIn(page, item) ?: return false
        update(pages.map { if (it.id == pageId) it.copy(items = placed) else it })
        return true
    }

    fun addToMainGrid(item: HomeItem): Boolean {
        val id = pages.firstOrNull { it.kind == PageKind.GRID }?.id ?: return false
        return addItem(id, item)
    }

    /** GRID / DOCK では空きマスにするだけで、ほかのアイコンは動かさない。 */
    fun removeItem(pageId: Long, item: HomeItem) {
        update(pages.map { if (it.id == pageId) it.copy(items = takeOut(it, item.key)) else it })
        cleanup(item)
    }

    /**
     * マスの入れ替え。from のアイコンを to へ、to にあったもの（空きなら空き）を from へ。
     * ホームの別ページ同士、ホームとドックの間でも入れ替えられる。周りのアイコンは動かない。
     */
    fun swapSlots(fromPageId: Long, fromIndex: Int, toPageId: Long, toIndex: Int): Boolean {
        val from = pages.find { it.id == fromPageId } ?: return false
        val to = pages.find { it.id == toPageId } ?: return false
        if (!from.kind.isSlots || !to.kind.isSlots) return false
        if (toIndex < 0 || toIndex >= capacity(to)) return false
        if (fromPageId == toPageId && fromIndex == toIndex) return false
        val moving = from.items.getOrNull(fromIndex) ?: return false

        if (fromPageId == toPageId) {
            val list = from.items.padTo(maxOf(fromIndex, toIndex) + 1)
            list[fromIndex] = list[toIndex]
            list[toIndex] = moving
            update(pages.map { if (it.id == fromPageId) it.copy(items = list.trimEnd()) else it })
            return true
        }

        val target = to.items.padTo(toIndex + 1)
        val other = target[toIndex]
        // 同じページに同じものが2つにならないようにする
        if (target.withIndex().any { (i, x) -> i != toIndex && x?.key == moving.key }) return false
        if (other != null && from.items.withIndex().any { (i, x) -> i != fromIndex && x?.key == other.key }) return false
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
        return true
    }

    /** ウィジェットページの並べ替え用：前後に1つずらす */
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

    /** 2枚目以降のホームページで、中身が空のときだけ消せる */
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
            .put("wallpaper", s.wallpaper)
            .put("shapeIcons", s.shapeIcons)
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

    private fun capacity(p: Page): Int = if (p.kind == PageKind.DOCK) DOCK_SIZE else Int.MAX_VALUE

    private fun placeIn(page: Page, item: HomeItem): List<HomeItem?>? {
        if (!page.kind.isSlots) return page.filled + item
        val list = page.items.padTo(if (page.kind == PageKind.DOCK) DOCK_SIZE else 0)
        val empty = list.indexOfFirst { it == null }
        when {
            empty >= 0 -> list[empty] = item
            page.kind == PageKind.DOCK -> return null
            else -> list.add(item)
        }
        return list.trimEnd()
    }

    private fun takeOut(page: Page, key: String): List<HomeItem?> =
        if (page.kind.isSlots) page.items.map { if (it?.key == key) null else it }.trimEnd()
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
        val loaded = runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val kind = runCatching { PageKind.valueOf(o.getString("kind")) }.getOrNull() ?: return@mapNotNull null
                val itemsArr = o.optJSONArray("items") ?: JSONArray()
                val items = (0 until itemsArr.length()).map { j ->
                    if (itemsArr.isNull(j)) null else itemFromJson(itemsArr.getJSONObject(j))
                }
                Page(
                    id = o.getLong("id"),
                    kind = kind,
                    title = o.getString("title"),
                    items = if (kind.isSlots) items.trimEnd() else items.filterNotNull(),
                )
            }
        }.getOrNull() ?: return null
        if (loaded.none { it.kind == PageKind.GRID }) return null
        if (loaded.any { it.kind == PageKind.DOCK }) return loaded
        val nextId = (loaded.maxOfOrNull { it.id } ?: 0L) + 1
        return loaded + Page(nextId, PageKind.DOCK, "よく連絡する人")
    }

    private fun loadSettings(): HomeSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return HomeSettings()
        return runCatching {
            val o = JSONObject(raw)
            HomeSettings(
                fontScale = o.optDouble("fontScale", 1.15).toFloat(),
                columns = o.optInt("columns", 3).coerceIn(2, 4),
                autoAddNewApps = o.optBoolean("autoAdd", true),
                wallpaper = o.optInt("wallpaper", 0),
                shapeIcons = o.optBoolean("shapeIcons", true),
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
            Page(1, PageKind.WIDGETS, "ウィジェット"),
            Page(2, PageKind.GRID, "ホーム", items),
            Page(3, PageKind.ALL_APPS, "アプリ"),
            Page(4, PageKind.DOCK, "よく連絡する人"),
        )
    }

    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            refreshCaches()
            if (user != Process.myUserHandle()) return
            // 消えたアプリのマスは空きにするだけ（詰めない）
            update(pages.map { p ->
                if (p.kind.isSlots)
                    p.copy(items = p.items.map { if (it?.packageName == packageName) null else it }.trimEnd())
                else p
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
