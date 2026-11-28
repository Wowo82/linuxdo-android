package org.linuxdo.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import org.linuxdo.android.ui.MainScreen
import org.linuxdo.android.ui.MainViewModel
import org.linuxdo.android.ui.MainViewModelFactory
import org.linuxdo.android.ui.common.LocalSiteBaseUrl
import org.linuxdo.android.ui.theme.LinuxDoTheme
import org.linuxdo.android.util.TopicLink

class MainActivity : ComponentActivity() {

    /**
     * 外部链接进来的话题 id。
     * Manifest 里声明了 `/t/` 的 VIEW intent-filter，没这个字段的话
     * 点链接只会拉起 App 停在首页，参数被丢掉。
     */
    private var deepLinkTopicId by mutableStateOf<Long?>(null)
    private var deepLinkPostNumber by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as LinuxDoApp).container
        consumeDeepLink(intent)

        setContent {
            LinuxDoTheme {
                val vm: MainViewModel = viewModel(factory = MainViewModelFactory(container))
                val state by vm.state.collectAsState()
                // 站点地址从根状态下发；切服务器后 state.baseUrl 变化会自动触发重组
                CompositionLocalProvider(
                    LocalSiteBaseUrl provides state.baseUrl.ifBlank { container.sessionStore.baseUrl },
                ) {
                    MainScreen(
                        vm = vm,
                        state = state,
                        container = container,
                        deepLinkTopicId = deepLinkTopicId,
                        deepLinkPostNumber = deepLinkPostNumber,
                        onDeepLinkConsumed = {
                            deepLinkTopicId = null
                            deepLinkPostNumber = null
                        },
                    )
                }
            }
        }
    }

    /** 从浏览器点 https://linux.do/t/... 过来（singleTop，复用同一实例） */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeDeepLink(intent)
    }

    private fun consumeDeepLink(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return
        val target = TopicLink.parse(intent.dataString) ?: return
        deepLinkTopicId = target.topicId
        deepLinkPostNumber = target.postNumber
        // 防止旋转 / 重建时重复处理同一个 intent
        intent.data = null
    }
}
