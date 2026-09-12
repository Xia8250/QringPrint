package com.thisko.qringprint

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.thisko.qringprint.bluetooth.BtPermission
import com.thisko.qringprint.bluetooth.PrinterConnection
import com.thisko.qringprint.bluetooth.ShareBus
import com.thisko.qringprint.ui.SafePage
import com.thisko.qringprint.ui.glass.GlassBottomBar
import com.thisko.qringprint.ui.glass.GlassBottomItem
import com.thisko.qringprint.ui.motion.pageEnter
import com.thisko.qringprint.ui.pages.AdBannerPrintPage
import com.thisko.qringprint.ui.pages.BatchPrintPage
import com.thisko.qringprint.ui.pages.CodePrintPage
import com.thisko.qringprint.ui.pages.CustomPrintPage
import com.thisko.qringprint.ui.pages.DiagnosticPage
import com.thisko.qringprint.ui.pages.HistoryPage
import com.thisko.qringprint.ui.pages.HomePage
import com.thisko.qringprint.ui.pages.ImagePrintPage
import com.thisko.qringprint.ui.pages.MinePage
import com.thisko.qringprint.ui.pages.OcrPrintPage
import com.thisko.qringprint.ui.pages.ReceiptPrintPage
import com.thisko.qringprint.ui.pages.StructuredKind
import com.thisko.qringprint.ui.pages.StructuredPrintPage
import com.thisko.qringprint.ui.pages.TemplatePage
import com.thisko.qringprint.ui.pages.TextPrintPage
import com.thisko.qringprint.ui.theme.QringPrintTheme
import com.thisko.qringprint.ui.theme.isAppDark
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    private val sharedItems = mutableStateOf<SharedPayload?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // edge-to-edge: 不再 hide statusBars, 让 App 背景延伸到状态栏后面
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        // 跟随 App 当前深色: 浅色 App 用深色导航栏文字,深色 App 用浅色文字
        val isAppDark = try {
            com.thisko.qringprint.model.ThemeState.darkOverride.value ?: false
        } catch (_: Throwable) { false }
        insetsController.isAppearanceLightNavigationBars = !isAppDark
        insetsController.isAppearanceLightStatusBars = !isAppDark
        requestPermissionsIfNeeded()
        lifecycleScope.launch { PrinterConnection.autoReconnect(this@MainActivity) }
        // 启动后 2 秒静默检查更新
        lifecycleScope.launch { kotlinx.coroutines.delay(2000L); com.thisko.qringprint.update.UpdateManager.check(this@MainActivity, manual = false) }
        handleShareIntent(intent)
        setContent {
            QringPrintTheme {
                var splashVisible by remember { mutableStateOf(true) }
                val dark = isAppDark()
                AnimatedContent(
                    targetState = splashVisible,
                    transitionSpec = { fadeIn(tween(420)) togetherWith fadeOut(tween(280)) },
                    label = "splash",
                ) { showSplash ->
                    if (showSplash) {
                        com.thisko.qringprint.ui.pages.SplashWelcomePage(onFinished = { splashVisible = false })
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(
                                    Brush.radialGradient(
                                        colors = if (dark) {
                                            listOf(
                                                Color(0xFF45474B),
                                                Color(0xFF34373C),
                                                Color(0xFF3F4146),
                                                Color(0xFF24262A),
                                            )
                                        } else {
                                            listOf(
                                                Color(0xFFB79CFF),
                                                Color(0xFFA8C8FF),
                                                Color(0xFFF7B7D2),
                                                Color(0xFFF2F3F5),
                                            )
                                        },
                                        center = Offset(540f, 320f),
                                        radius = 1300f,
                                    ),
                                ),
                            )
                            Box(
                                modifier = Modifier.fillMaxSize().background(
                                    Brush.radialGradient(
                                        colors = if (dark) {
                                            listOf(Color(0xFF746B5D).copy(alpha = 0.34f), Color.Transparent)
                                        } else {
                                            listOf(Color(0xFFFBE0B2).copy(alpha = 0.55f), Color.Transparent)
                                        },
                                        center = Offset(180f, 1900f),
                                        radius = 900f,
                                    ),
                                ),
                            )
                            AppRoot(sharedItems = sharedItems)
                        }
                        com.thisko.qringprint.update.UpdatePromptHost()
                        }
                    }
                }
            }
        }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val type = intent.type ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (type.startsWith("image/")) {
                    val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    }
                    if (uri != null) {
                        ShareBus.setImages(listOf(uri))
                        sharedItems.value = SharedPayload(1, false)
                    }
                } else if (type == "text/plain") {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                    val body = if (!text.isNullOrEmpty()) "标题: $subject\n\n$text" else null
                    if (body != null) {
                        ShareBus.setText(body)
                        sharedItems.value = SharedPayload(0, true)
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (type.startsWith("image/")) {
                    val uris: ArrayList<Uri>? = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                    }
                    if (!uris.isNullOrEmpty()) {
                        ShareBus.setImages(uris)
                        sharedItems.value = SharedPayload(uris.size, false)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        PrinterConnection.onForeground()
    }

    override fun onPause() {
        super.onPause()
        PrinterConnection.onBackground()
    }

    /** Re-create this Activity to apply display-mode changes. */
    fun recreateSelf() {
        runCatching { recreate() }
    }

    private fun requestPermissionsIfNeeded() {
        if (!BtPermission.hasAll(this)) {
            requestPermissionLauncher.launch(BtPermission.requiredPermissions())
        }
    }
}

data class SharedPayload(val imageCount: Int, val hasText: Boolean)

private enum class Tab(val key: String, val label: String, val icon: ImageVector) {
    Home("home", "首页", Icons.Filled.Home),
    Template("template", "模板", Icons.Filled.GridView),
    History("history", "历史", Icons.Filled.History),
    Mine("mine", "我的", Icons.Filled.Person),
}

private sealed class Route {
    data object Tabs : Route()
    data class Print(val kind: String) : Route()
    data class Custom(val id: String? = null) : Route()
    data object Diagnostic : Route()
    data object Batch : Route()
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun AppRoot(sharedItems: MutableState<SharedPayload?>) {
    var currentTab by remember { mutableStateOf(Tab.Home) }
    var stack by remember { mutableStateOf<List<Route>>(listOf(Route.Tabs)) }

    val onPrint: (String, String) -> Unit = { kind, payload ->
        when (kind) {
            "text" -> com.thisko.qringprint.model.HistoryBus.setText(payload)
            "code" -> com.thisko.qringprint.model.HistoryBus.setCode(payload)
            "image" -> com.thisko.qringprint.model.HistoryBus.setImage(payload)
        }
        stack = stack + Route.Print(kind)
    }
    val onCustom: (String?) -> Unit = { id -> stack = stack + Route.Custom(id) }
    val current = stack.last()

    LaunchedEffect(sharedItems.value) {
        val shared = sharedItems.value ?: return@LaunchedEffect
        when {
            shared.imageCount > 0 -> {
                sharedItems.value = null
                currentTab = Tab.Home
                stack = listOf(Route.Tabs, Route.Print("image"))
            }
            shared.hasText -> {
                sharedItems.value = null
                currentTab = Tab.Home
                stack = listOf(Route.Tabs, Route.Print("text"))
            }
        }
    }

    val dark = isAppDark()

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = current to currentTab,
            modifier = Modifier.fillMaxSize().pageEnter(),
            transitionSpec = {
                (fadeIn() + slideInHorizontally { it / 8 }) togetherWith
                    (fadeOut() + slideOutHorizontally { -it / 12 })
            },
            label = "route-transition",
        ) { target ->
            val route = target.first
            val tab = target.second
            when (route) {
                is Route.Tabs -> when (tab) {
                    Tab.Home -> HomePage(
                        modifier = Modifier.fillMaxSize().padding(bottom = 84.dp),
                        onPrint = { kind -> onPrint(kind, "") },
                        onCustom = { onCustom(null) },
                        onBatch = { stack = stack + Route.Batch },
                        onDiagnostic = { stack = stack + Route.Diagnostic },
                    )
                    Tab.Template -> TemplatePage(
                        modifier = Modifier.fillMaxSize().padding(bottom = 84.dp),
                        onEdit = { id -> onCustom(id) },
                    )
                    Tab.History -> HistoryPage(
                        modifier = Modifier.fillMaxSize().padding(bottom = 84.dp),
                        onReprint = onPrint,
                    )
                    Tab.Mine -> MinePage(
                        modifier = Modifier.fillMaxSize().padding(bottom = 84.dp),
                        onDiagnostic = { stack = stack + Route.Diagnostic },
                    )
                }
                is Route.Print -> {
                    val backStack = stack.dropLast(1)
                    when (route.kind) {
                        "text" -> SafePage("文字打印", onBack = { stack = backStack }) { TextPrintPage(onBack = { stack = backStack }) }
                        "image" -> SafePage("图片打印", onBack = { stack = backStack }) { ImagePrintPage(onBack = { stack = backStack }) }
                        "code" -> SafePage("条码打印", onBack = { stack = backStack }) { CodePrintPage(onBack = { stack = backStack }) }
                        "adbanner" -> SafePage("广告横带", onBack = { stack = backStack }) { AdBannerPrintPage(onBack = { stack = backStack }) }
                        "custom" -> SafePage("自定义打印", onBack = { stack = backStack }) { CustomPrintPage(templateId = null, onBack = { stack = backStack }) }
                        "table" -> SafePage("表格打印", onBack = { stack = backStack }) { StructuredPrintPage(StructuredKind.TABLE, onBack = { stack = backStack }) }
                        "receipt" -> SafePage("小票打印", onBack = { stack = backStack }) { ReceiptPrintPage(onBack = { stack = backStack }) }
                        "ocr" -> SafePage("拍照转文字", onBack = { stack = backStack }) { OcrPrintPage(onBack = { stack = backStack }) }
                        "time" -> SafePage("时间打印", onBack = { stack = backStack }) { StructuredPrintPage(StructuredKind.TIME, onBack = { stack = backStack }) }
                        "schedule" -> SafePage("课程表", onBack = { stack = backStack }) { StructuredPrintPage(StructuredKind.SCHEDULE, onBack = { stack = backStack }) }
                        "label" -> SafePage("标签纸", onBack = { stack = backStack }) { StructuredPrintPage(StructuredKind.LABEL, onBack = { stack = backStack }) }
                        else -> HomePage(
                            modifier = Modifier.fillMaxSize().padding(bottom = 84.dp),
                            onPrint = { kind -> onPrint(kind, "") },
                            onCustom = { onCustom(null) },
                            onBatch = { stack = stack + Route.Batch },
                            onDiagnostic = { stack = stack + Route.Diagnostic },
                        )
                    }
                }
                is Route.Custom -> {
                    val backStack = stack.dropLast(1)
                    SafePage("自定义打印", onBack = { stack = backStack }) {
                        CustomPrintPage(templateId = route.id, onBack = { stack = backStack })
                    }
                }
                is Route.Diagnostic -> {
                    val backStack = stack.dropLast(1)
                    SafePage("诊断页", onBack = { stack = backStack }) {
                        DiagnosticPage(onBack = { stack = backStack })
                    }
                }
                is Route.Batch -> {
                    val backStack = stack.dropLast(1)
                    SafePage("批量打印", onBack = { stack = backStack }) {
                        BatchPrintPage(onBack = { stack = backStack })
                    }
                }
            }
        }

        if (current is Route.Tabs) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                GlassBottomBar(
                    items = Tab.values().map { tabItem ->
                        GlassBottomItem(tabItem.label, tabItem.icon) { currentTab = tabItem }
                    },
                    selectedIndex = Tab.values().indexOf(currentTab).coerceAtLeast(0),
                    dark = dark,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}
