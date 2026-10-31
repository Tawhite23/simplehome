package jp.kantanhome.launcher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object IconCache {
    private val cache = LruCache<String, ImageBitmap>(256)

    /** 見た目に関わる設定もキーに入れる（変えたら別物として読み直す） */
    fun cacheKey(item: HomeItem, style: ItemStyle?, shape: Boolean): String =
        "${item.key}|${style?.iconFile}|$shape"

    fun peek(key: String): ImageBitmap? = cache.get(key)

    fun clear() = cache.evictAll()

    /**
     * @param shape true なら角丸の枠いっぱいに描けるよう形を整える
     *  - アダプティブアイコン：背景と前景をそのまま全面に描く（端末ごとの丸や雫の型を使わない）
     *  - 丸いアイコンしかない古いアプリ：中身を枠いっぱいに広げ、角はふちの色で塗る
     */
    suspend fun load(context: Context, item: HomeItem, style: ItemStyle?, shape: Boolean, sizePx: Int): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val key = cacheKey(item, style, shape)
            cache.get(key) ?: runCatching {
                val custom = style?.iconFile
                if (custom != null) {
                    BitmapFactory.decodeFile(File(context.filesDir, custom).path)
                        ?.let { Bitmap.createScaledBitmap(it, sizePx, sizePx, true) }
                } else {
                    val d = drawableFor(context, item) ?: return@runCatching null
                    if (shape) fillShape(d, sizePx) else d.toBitmap(sizePx, sizePx)
                }
            }.getOrNull()?.asImageBitmap()?.also { cache.put(key, it) }
        }

    private fun drawableFor(context: Context, item: HomeItem): Drawable? {
        val la = context.getSystemService(LauncherApps::class.java)
        val pm = context.packageManager
        return when (item) {
            is HomeItem.App ->
                la.getActivityList(item.pkg, Process.myUserHandle())
                    .firstOrNull { it.componentName.className == item.cls }
                    ?.getIcon(0)
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

    // ------------------------------------------------------------------
    // 形をそろえる
    // ------------------------------------------------------------------

    private fun fillShape(d: Drawable, s: Int): Bitmap {
        if (d is AdaptiveIconDrawable) {
            // 各レイヤーは 108dp で、見せる範囲は中央の 72dp。72dp が s になるよう 1.5 倍で描く。
            val out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val inset = s / 4
            listOfNotNull(d.background, d.foreground).forEach {
                it.setBounds(-inset, -inset, s + inset, s + inset)
                it.draw(c)
            }
            return out
        }
        return fillLegacy(d.toBitmap(s * 2, s * 2), s)
    }

    /** 古い形式のアイコン（丸だけのもの等）：中身を四角いっぱいに広げ、すき間はふちの色で塗る */
    private fun fillLegacy(src: Bitmap, s: Int): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        var left = w; var top = h; var right = -1; var bottom = -1
        for (y in 0 until h) for (x in 0 until w) {
            if (Color.alpha(px[y * w + x]) > 40) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (right < left || bottom < top) return Bitmap.createScaledBitmap(src, s, s, true)

        // 中身の内側に沿って円周上の色を拾い、平均を背景色にする
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val r = min(right - left, bottom - top) / 2f * 0.9f
        var sr = 0L; var sg = 0L; var sb = 0L; var n = 0
        for (i in 0 until 72) {
            val a = i * Math.PI * 2 / 72
            val x = (cx + r * cos(a)).toInt().coerceIn(0, w - 1)
            val y = (cy + r * sin(a)).toInt().coerceIn(0, h - 1)
            val p = px[y * w + x]
            if (Color.alpha(p) > 200) {
                sr += Color.red(p); sg += Color.green(p); sb += Color.blue(p); n++
            }
        }
        val bg = if (n > 0) Color.rgb((sr / n).toInt(), (sg / n).toInt(), (sb / n).toInt()) else Color.WHITE

        val out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(bg)
        // 正方形に近づけてから枠いっぱいに描く（縦横比は変えない）
        val side = maxOf(right - left, bottom - top) + 1
        val sx = (cx - side / 2f).toInt()
        val sy = (cy - side / 2f).toInt()
        c.drawBitmap(
            src,
            Rect(sx, sy, sx + side, sy + side),
            RectF(0f, 0f, s.toFloat(), s.toFloat()),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
        return out
    }
}
