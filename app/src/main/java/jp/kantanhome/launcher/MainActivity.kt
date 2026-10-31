package jp.kantanhome.launcher

import android.Manifest
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import jp.kantanhome.launcher.data.AppCatalog
import jp.kantanhome.launcher.data.HomeItem
import jp.kantanhome.launcher.data.HomeStore
import jp.kantanhome.launcher.data.PageKind
import jp.kantanhome.launcher.ui.HomeActions
import jp.kantanhome.launcher.ui.HomeScreen
import jp.kantanhome.launcher.ui.KantanTheme

class MainActivity : ComponentActivity() {

    private data class PendingWidget(val id: Int, val info: AppWidgetProviderInfo, val pageId: Long)

    /** ホームボタンが押されるたびに増える。画面側で「ホーム」ページへ戻す合図にする。 */
    private val homeSignal = mutableIntStateOf(0)
    private var pendingWidget: PendingWidget? = null
    private var pendingContactPageId: Long? = null

    private val bindWidget =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val p = pendingWidget ?: return@registerForActivityResult
            if (res.resultCode == RESULT_OK) configureOrAdd(p) else cancelWidget(p)
        }

    private val pickContactLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val uri = res.data?.data
            if (res.resultCode == RESULT_OK && uri != null) addContactFrom(uri)
        }

    private val callPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val actions by lazy {
        HomeActions(
            launch = this::launch,
            call = this::call,
            pickContact = this::pickContact,
            addWidget = this::startAddWidget,
            openHomeSettings = { openSettings(Settings.ACTION_HOME_SETTINGS) },
            openSystemSettings = { openSettings(Settings.ACTION_SETTINGS) },
            openChrome = this::openChrome,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        HomeStore.init(this)
        AppCatalog.refresh(this)
        setContent {
            KantanTheme(fontScale = HomeStore.settings.fontScale) {
                HomeScreen(actions = actions, homeSignal = homeSignal.intValue)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching { HomeStore.widgetHost.startListening() }
    }

    override fun onStop() {
        super.onStop()
        runCatching { HomeStore.widgetHost.stopListening() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) homeSignal.intValue++
    }

    // ------------------------------------------------------------------
    // 起動・電話
    // ------------------------------------------------------------------

    private fun launch(item: HomeItem) {
        val la = HomeStore.launcherApps
        val user = Process.myUserHandle()
        try {
            when (item) {
                is HomeItem.App -> la.startMainActivity(ComponentName(item.pkg, item.cls), user, null, null)
                is HomeItem.Shortcut -> la.startShortcut(item.pkg, item.id, null, null, user)
                else -> Unit
            }
        } catch (e: Exception) {
            // アプリ更新でクラス名が変わった場合などは、パッケージの起動画面で再挑戦
            val fallback = (item as? HomeItem.App)?.let { packageManager.getLaunchIntentForPackage(it.pkg) }
            if (fallback != null) runCatching { startActivity(fallback) }.onFailure { toast("ひらけませんでした") }
            else toast("ひらけませんでした")
        }
    }

    private fun call(c: HomeItem.Contact) {
        val uri = Uri.fromParts("tel", c.number, null)
        val granted = checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val action = if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL
        runCatching { startActivity(Intent(action, uri)) }.onFailure { toast("電話をかけられませんでした") }
    }

    // ------------------------------------------------------------------
    // 連絡先の追加
    // ------------------------------------------------------------------

    private fun pickContact(pageId: Long) {
        pendingContactPageId = pageId
        runCatching {
            pickContactLauncher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        }.onFailure { toast("電話帳をひらけませんでした") }
    }

    private fun addContactFrom(uri: Uri) {
        val pageId = pendingContactPageId ?: HomeStore.dock?.id ?: return
        pendingContactPageId = null
        val isDock = HomeStore.pages.find { it.id == pageId }?.kind == PageKind.DOCK
        // 電話番号データの URI は一時的に読める権限付きで返ってくるので、READ_CONTACTS は不要
        runCatching {
            contentResolver.query(uri, arrayOf("display_name", "data1"), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val number = c.getString(1)?.trim().orEmpty()
                    val name = c.getString(0)?.trim().orEmpty().ifBlank { number }
                    if (number.isNotEmpty()) {
                        val item = HomeItem.Contact(name, number)
                        when {
                            HomeStore.addItem(pageId, item) -> toast("「$name」をついかしました")
                            HomeStore.contains(pageId, item.key) -> toast("もう入っています")
                            isDock -> toast("下の4つがいっぱいです。どれかを外してください")
                            else -> toast("ついかできませんでした")
                        }
                    }
                }
            }
        }.onFailure { toast("れんらく先を読みこめませんでした") }

        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            callPermission.launch(Manifest.permission.CALL_PHONE)
        }
    }

    // ------------------------------------------------------------------
    // ウィジェットの追加
    // ------------------------------------------------------------------

    private fun startAddWidget(pageId: Long, info: AppWidgetProviderInfo) {
        val id = HomeStore.widgetHost.allocateAppWidgetId()
        val p = PendingWidget(id, info, pageId)
        pendingWidget = p
        val awm = AppWidgetManager.getInstance(this)
        val bound = runCatching { awm.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) }
            .getOrDefault(false)
        if (bound) {
            configureOrAdd(p)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            runCatching { bindWidget.launch(intent) }.onFailure {
                cancelWidget(p)
                toast("ウィジェットを置けませんでした")
            }
        }
    }

    private fun configureOrAdd(p: PendingWidget) {
        val optional = (p.info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0
        if (p.info.configure != null && !optional) {
            try {
                HomeStore.widgetHost.startAppWidgetConfigureActivityForResult(this, p.id, 0, REQ_CONFIGURE, null)
                return
            } catch (_: Exception) {
                // 設定画面を開けないウィジェットはそのまま置く
            }
        }
        finishWidget(p)
    }

    private fun finishWidget(p: PendingWidget) {
        val minDp = (p.info.minHeight / resources.displayMetrics.density).toInt()
        val height = (minDp + 24).coerceIn(120, 400)
        HomeStore.addItem(p.pageId, HomeItem.Widget(p.id, height))
        pendingWidget = null
    }

    private fun cancelWidget(p: PendingWidget) {
        runCatching { HomeStore.widgetHost.deleteAppWidgetId(p.id) }
        pendingWidget = null
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CONFIGURE) return
        val p = pendingWidget ?: return
        if (resultCode == RESULT_OK) finishWidget(p) else cancelWidget(p)
    }

    // ------------------------------------------------------------------

    private fun openChrome() {
        val intent = packageManager.getLaunchIntentForPackage("com.android.chrome")
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.co.jp/"))
        runCatching { startActivity(intent) }.onFailure { toast("Chromeをひらけませんでした") }
    }

    private fun openSettings(action: String) {
        runCatching { startActivity(Intent(action)) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val REQ_CONFIGURE = 7101
    }
}
