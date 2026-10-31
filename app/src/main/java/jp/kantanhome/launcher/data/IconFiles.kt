package jp.kantanhome.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.min

/** 写真を真ん中で正方形に切り抜いてアイコン用に保存する。戻り値は filesDir からの相対パス。 */
suspend fun savePhotoIcon(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val scale = 640f / min(w, h)
            if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
        }
        val side = min(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(
            decoded,
            (decoded.width - side) / 2,
            (decoded.height - side) / 2,
            side,
            side,
        )
        val icon = Bitmap.createScaledBitmap(square, 384, 384, true)
        val dir = File(context.filesDir, "icons").apply { mkdirs() }
        val name = "icons/${UUID.randomUUID()}.png"
        File(context.filesDir, name).outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
        name
    }.getOrNull()
}
