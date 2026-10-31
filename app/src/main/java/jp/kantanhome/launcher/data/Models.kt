package jp.kantanhome.launcher.data

import org.json.JSONObject

/** ページの種類。シンプルホームの「連絡先／メイン／アプリ／ウィジェット」の分け方をベースにしている。 */
enum class PageKind { CONTACTS, GRID, WIDGETS, ALL_APPS }

/** ホームに置けるもの。GRID ページにはアプリ・Webショートカット・連絡先を混ぜて置ける。 */
sealed interface HomeItem {
    val key: String

    data class App(val pkg: String, val cls: String, val label: String) : HomeItem {
        override val key: String get() = "app:$pkg/$cls"
    }

    /** Chrome の「ホーム画面に追加」などで作られるピン留めショートカット */
    data class Shortcut(val pkg: String, val id: String, val label: String) : HomeItem {
        override val key: String get() = "sc:$pkg/$id"
    }

    data class Contact(val name: String, val number: String) : HomeItem {
        override val key: String get() = "tel:$number"
    }

    data class Widget(val widgetId: Int, val heightDp: Int) : HomeItem {
        override val key: String get() = "w:$widgetId"
    }
}

val HomeItem.packageName: String?
    get() = when (this) {
        is HomeItem.App -> pkg
        is HomeItem.Shortcut -> pkg
        else -> null
    }

/** 移動先として受け入れられるか */
fun PageKind.accepts(item: HomeItem): Boolean = when (item) {
    is HomeItem.Widget -> this == PageKind.WIDGETS
    is HomeItem.Contact -> this == PageKind.GRID || this == PageKind.CONTACTS
    else -> this == PageKind.GRID
}

/**
 * items は「マス」の並び。GRID ページでは null = 空きマス。
 * 外したり入れ替えたりしても自動で詰めない（空きはそのまま残る）。
 * CONTACTS / WIDGETS ページは普通のリストで null は入らない。
 */
data class Page(
    val id: Long,
    val kind: PageKind,
    val title: String,
    val items: List<HomeItem?> = emptyList(),
) {
    val filled: List<HomeItem> get() = items.filterNotNull()
}

data class HomeSettings(
    val fontScale: Float = 1.15f,
    val columns: Int = 3,
    val autoAddNewApps: Boolean = true,
)

data class AppEntry(val pkg: String, val cls: String, val label: String) {
    val key: String get() = "app:$pkg/$cls"
    fun toItem(): HomeItem.App = HomeItem.App(pkg, cls, label)
}

// ---- JSON 保存用 ----

internal fun HomeItem.toJson(): JSONObject = when (this) {
    is HomeItem.App -> JSONObject().put("t", "app").put("pkg", pkg).put("cls", cls).put("label", label)
    is HomeItem.Shortcut -> JSONObject().put("t", "sc").put("pkg", pkg).put("id", id).put("label", label)
    is HomeItem.Contact -> JSONObject().put("t", "tel").put("name", name).put("number", number)
    is HomeItem.Widget -> JSONObject().put("t", "w").put("wid", widgetId).put("h", heightDp)
}

internal fun itemFromJson(o: JSONObject): HomeItem? = runCatching {
    when (o.optString("t")) {
        "app" -> HomeItem.App(o.getString("pkg"), o.getString("cls"), o.optString("label"))
        "sc" -> HomeItem.Shortcut(o.getString("pkg"), o.getString("id"), o.optString("label"))
        "tel" -> HomeItem.Contact(o.optString("name"), o.getString("number"))
        "w" -> HomeItem.Widget(o.getInt("wid"), o.optInt("h", 160))
        else -> null
    }
}.getOrNull()
