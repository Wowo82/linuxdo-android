package org.linuxdo.android.ui.profile

import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.linuxdo.android.AppContainer
import org.linuxdo.android.data.model.User
import org.linuxdo.android.data.remote.parseCookieHeader
import kotlin.coroutines.resume

/**
 * 网页登录：覆盖站点自带的 OAuth / SSO / Passkey / 人机验证等原生难接的方式。
 *
 * 让用户在 WebView 里完成登录，轮询 CookieManager 拿到 `_t` 后同步进应用的
 * cookie jar，再用 `/session/current.json` 验证并复用现有登录态。
 * 只读取当前站点 host 的 cookie，成功后会清掉 WebView 里的副本。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebLoginDialog(
    baseUrl: String,
    startPath: String,
    container: AppContainer,
    onDismiss: () -> Unit,
    onLoggedIn: (User) -> Unit,
) {
    val context = LocalContext.current
    val host = remember(baseUrl) { baseUrl.toHttpUrlOrNull()?.host }
    val initialPath = remember(startPath) {
        if (startPath.startsWith("/")) startPath else "/$startPath"
    }
    var progress by remember { mutableIntStateOf(0) }
    var verifying by remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.mediaPlaybackRequiresUserGesture = true
            val view = this
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(view, true)
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    // 只放行 http(s)；intent:// / market:// 等交给系统会跳出登录流程
                    val scheme = request.url.scheme
                    return scheme != "http" && scheme != "https"
                }
            }
        }
    }

    DisposableEffect(webView) {
        onDispose {
            runCatching {
                (webView.parent as? android.view.ViewGroup)?.removeView(webView)
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.destroy()
            }
        }
    }

    LaunchedEffect(baseUrl, host, initialPath) {
        val siteHost = host ?: return@LaunchedEffect
        val cookieManager = CookieManager.getInstance()

        // 先清掉 WebView 里的旧会话，避免退出 App 账号后又被网页自动登录
        suspendCancellableCoroutine<Unit> { cont ->
            cookieManager.removeAllCookies { cont.resume(Unit) }
        }
        webView.loadUrl("$baseUrl$initialPath")

        var lastTriedHeader: String? = null
        while (true) {
            delay(700)
            val header = cookieManager.getCookie(baseUrl) ?: continue
            if ("_t=" !in header || header == lastTriedHeader) continue
            lastTriedHeader = header

            val cookies = parseCookieHeader(header, siteHost)
            if (cookies.isEmpty()) continue
            container.sessionStore.cookieJar.putAll(cookies)

            verifying = true
            val user = container.repository.completeWebLogin().getOrNull()
            verifying = false
            if (user != null) {
                // 会话已经落到加密存储，WebView 里这份明文副本可以删掉
                cookieManager.removeAllCookies(null)
                onLoggedIn(user)
                return@LaunchedEffect
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text("网页登录", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, "关闭")
                            }
                        },
                        actions = {
                            if (initialPath != "/login") {
                                TextButton(onClick = { webView.loadUrl("$baseUrl/login") }) {
                                    Text("完整登录页", fontSize = 12.sp)
                                }
                            }
                        },
                    )
                },
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (host == null) {
                            Text(
                                "服务器地址无效",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.align(Alignment.Center),
                            )
                        } else {
                            AndroidView(
                                factory = { webView },
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (progress in 1..99) {
                                LinearProgressIndicator(
                                    Modifier.fillMaxWidth().align(Alignment.TopCenter)
                                )
                            }
                            if (verifying) {
                                Row(
                                    Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 12.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(
                                        Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("正在完成登录…", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "支持站点自带的 OAuth / SSO / 通行密钥 / 人机验证等方式",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}
