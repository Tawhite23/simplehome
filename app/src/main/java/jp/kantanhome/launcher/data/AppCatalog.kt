package jp.kantanhome.launcher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.Collator
import java.util.Locale
import kotlin.concurrent.thread

/** 起動できるアプリの一覧（あいうえお順） */
object AppCatalog {
    var apps: List<AppEntry> by mutableStateOf(emptyList())
        private set

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var loading = false
    @Volatile private var again = false

    fun refresh(context: Context) {
        if (loading) {
            again = true
            return
        }
        loading = true
        val app = context.applicationContext
        thread(name = "app-catalog") {
            val list = runCatching {
                val la = app.getSystemService(LauncherApps::class.java)
                val collator = Collator.getInstance(Locale.JAPANESE)
                la.getActivityList(null, Process.myUserHandle())
                    .filter { it.componentName.packageName != app.packageName }
                    .map { AppEntry(it.componentName.packageName, it.componentName.className, it.label.toString()) }
                    .distinctBy { it.key }
                    .sortedWith { a, b -> collator.compare(a.label, b.label) }
            }.getOrDefault(apps)
            main.post {
                apps = list
                loading = false
                if (again) {
                    again = false
                    refresh(app)
                }
            }
        }
    }
}
