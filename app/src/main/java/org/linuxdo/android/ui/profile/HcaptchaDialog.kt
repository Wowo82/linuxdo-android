package org.linuxdo.android.ui.profile

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * linux.do 登录页使用的公开 hCaptcha sitekey（与开源项目 fluxdo 保持一致）。
 * hCaptcha 会校验页面域名，所以只在 linux.do 域名下使用。
 */
private const val LINUXDO_HCAPTCHA_SITE_KEY = "a776b4ac-8c4c-441e-986a-c6ee9ed8cf08"

/** 当前站点是否有可用的内置人机验证配置 */
internal fun hcaptchaSiteKeyFor(baseUrl: String): String? {
    val host = baseUrl.trim().toHttpUrlOrNull()?.host ?: return null
    return if (host == "linux.do" || host.endsWith(".linux.do")) LINUXDO_HCAPTCHA_SITE_KEY else null
}

/**
 * 独立的人机验证弹窗。
 *
 * 参考 fluxdo：不加载整页 Discourse 登录页，而是在一个 baseUrl 为站点域名
 * 的轻量页面里单独渲染 hCaptcha。Discourse 登录弹窗的样式/CSP/布局嵌套
 * 会让嵌入式 WebView 里的 hCaptcha 挑战区渲染成空白，独立页面更稳定。
 *
 * 验证通过后只回传 token，由仓库层提交给站点，再复用原生密码登录，
 * 这样 2FA、错误提示都走现有逻辑。
 */
@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HcaptchaDialog(
    baseUrl: String,
    siteKey: String,
    onDismiss: () -> Unit,
    onSolved: (String) -> Unit,
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val solvedState = rememberUpdatedState(onSolved)
    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val errorMessage = remember(reloadKey) { mutableStateOf<String?>(null) }

    val webView = remember(reloadKey) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            // 去掉 WebView UA 标记，避免 hCaptcha 把嵌入式浏览器判为不支持
            val defaultUa = settings.userAgentString
            if (defaultUa != null && defaultUa.contains("; wv")) {
                settings.userAgentString = defaultUa
                    .replace("; wv", "")
                    .replace("Version/4.0 ", "")
            }
            val view = this
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(view, true)
            }
            addJavascriptInterface(
                object {
                    private var handled = false

                    @JavascriptInterface
                    fun onSolved(token: String) {
                        if (handled || token.isBlank()) return
                        handled = true
                        mainHandler.post { solvedState.value(token) }
                    }

                    @JavascriptInterface
                    fun onError(message: String) {
                        mainHandler.post {
                            errorMessage.value = message.ifBlank { "人机验证加载失败" }
                        }
                    }
                },
                "HcaptchaBridge",
            )
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    loading = false
                }
            }
            webChromeClient = WebChromeClient()
            loadDataWithBaseURL(
                baseUrl.trimEnd('/') + "/",
                hcaptchaHtml(siteKey),
                "text/html",
                "utf-8",
                null,
            )
        }
    }

    DisposableEffect(webView) {
        onDispose {
            runCatching {
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.removeJavascriptInterface("HcaptchaBridge")
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.destroy()
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
                            Text(
                                "完成人机验证",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, "关闭")
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    loading = true
                                    reloadKey++
                                },
                            ) {
                                Icon(Icons.Filled.Refresh, "重新加载")
                            }
                        },
                    )
                },
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        AndroidView(
                            factory = { webView },
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (loading) {
                            CircularProgressIndicator(Modifier.align(Alignment.Center))
                        }
                    }
                    errorMessage.value?.let { message ->
                        Text(
                            message,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Text(
                        "验证码加载失败时，可改用邮箱验证码或网页登录",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                }
            }
        }
    }
}

/**
 * 轻量 hCaptcha 页面：origin 由 loadDataWithBaseURL 的 baseUrl 决定，
 * 不引入 Discourse 前端资源。
 */
private fun hcaptchaHtml(siteKey: String): String {
    val safeKey = siteKey.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
    return """
        <!DOCTYPE html>
        <html lang="zh-CN">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
          <style>
            html, body { margin: 0; padding: 0; min-height: 100%; background: #fff;
              font-family: -apple-system, system-ui, sans-serif; }
            .wrap { min-height: 100vh; box-sizing: border-box; display: flex;
              flex-direction: column; align-items: center; justify-content: center;
              gap: 18px; padding: 24px; text-align: center; }
            .tip { font-size: 14px; line-height: 1.6; color: #5f6368; margin: 0; }
            #cap { min-height: 78px; display: flex; align-items: center; justify-content: center; }
            .err { font-size: 12px; color: #c62828; margin: 0; max-width: 280px; }
          </style>
        </head>
        <body>
          <div class="wrap">
            <p class="tip">请完成下方的人机验证，通过后会自动继续登录</p>
            <div id="cap"></div>
            <p class="err" id="err"></p>
          </div>
          <script>
            (function () {
              function fail(msg) {
                var text = msg || '人机验证加载失败';
                document.getElementById('err').textContent = text;
                try { HcaptchaBridge.onError(text); } catch (e) {}
              }
              function boot() {
                if (!window.hcaptcha) { fail('验证码脚本加载失败，请重试'); return; }
                try {
                  hcaptcha.render('cap', {
                    sitekey: '$safeKey',
                    callback: function (token) {
                      try { HcaptchaBridge.onSolved(token); } catch (e) {}
                    },
                    'error-callback': function (e) { fail('验证失败：' + (e || '请重试')); },
                    'expired-callback': function () {
                      document.getElementById('err').textContent = '验证已过期，请重新勾选';
                    }
                  });
                } catch (e) { fail(String(e)); }
              }
              var s = document.createElement('script');
              s.src = 'https://js.hcaptcha.com/1/api.js?render=explicit&hl=zh';
              s.async = true;
              s.onload = boot;
              s.onerror = function () { fail('验证码脚本加载失败，请检查网络后重试'); };
              document.head.appendChild(s);
            })();
          </script>
        </body>
        </html>
    """.trimIndent()
}
