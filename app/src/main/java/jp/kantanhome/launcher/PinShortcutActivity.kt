package jp.kantanhome.launcher

import android.content.pm.LauncherApps
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import jp.kantanhome.launcher.data.HomeItem
import jp.kantanhome.launcher.data.HomeStore

/**
 * Chrome などの「ホーム画面に追加」を受け取り、メインの「ホーム」ページの空きマスに置く。
 * このアプリがデフォルトのホームになっているときだけ呼ばれる。
 */
class PinShortcutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HomeStore.init(this)
        val la = getSystemService(LauncherApps::class.java)
        val req = runCatching { la.getPinItemRequest(intent) }.getOrNull()
        if (req != null && req.isValid && req.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            val info = req.shortcutInfo
            if (info != null && req.accept()) {
                val label = (info.shortLabel ?: info.longLabel ?: "ショートカット").toString()
                val added = HomeStore.addToMainGrid(HomeItem.Shortcut(info.`package`, info.id, label))
                val msg = if (added) "「$label」をホームに置きました" else "「$label」はもうホームにあります"
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
        finish()
    }
}
