package jp.kantanhome.launcher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object IconCache {
    private val cache = LruCache<String, ImageBitmap>(256)

    fun peek(key: String): ImageBitmap? = cache.get(key)

    fun clear() = cache.evictAll()

    suspend fun load(context: Context, item: HomeItem, sizePx: Int): ImageBitmap? =
        withContext(Dispatchers.IO) {
            cache.get(item.key) ?: runCatching { drawableFor(context, item) }.getOrNull()
                ?.toBitmap(sizePx, sizePx)
                ?.asImageBitmap()
                ?.also { cache.put(item.key, it) }
        }

    private fun drawableFor(context: Context, item: HomeItem): Drawable? {
        val la = context.getSystemService(LauncherApps::class.java)
        val pm = context.packageManager
        return when (item) {
            is HomeItem.App ->
                la.getActivityList(item.pkg, Process.myUserHandle())
                    .firstOrNull { it.componentName.className == item.cls }
                    ?.getBadgedIcon(0)
                    ?: pm.getApplicationIcon(item.pkg)

            is HomeItem.Shortcut ->
                shortcutDrawable(context, la, item) ?: pm.getApplicationIcon(item.pkg)

            else -> null
        }
    }

    private fun shortcutDrawable(context: Context, la: LauncherApps, item: HomeItem.Shortcut): Drawable? {
        if (!la.hasShortcutHostPermission()) return null
        val query = LauncherApps.ShortcutQuery()
            .setPackage(item.pkg)
            .setShortcutIds(listOf(item.id))
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        val info = la.getShortcuts(query, Process.myUserHandle())?.firstOrNull() ?: return null
        return la.getShortcutIconDrawable(info, context.resources.displayMetrics.densityDpi)
    }
}
