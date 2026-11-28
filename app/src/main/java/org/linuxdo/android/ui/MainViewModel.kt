package org.linuxdo.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.linuxdo.android.AppContainer
import org.linuxdo.android.data.model.User

data class MainState(
    val booting: Boolean = true,
    /** 当前站点地址：头像 / 图片绝对化都要用它，跟着「服务器地址」变 */
    val baseUrl: String = "",
    val siteTitle: String = "LINUX DO",
    val siteIcon: String? = null,
    val user: User? = null,
    val unreadNotifications: Int = 0,
    val bootError: String? = null,
)

/**
 * 根级状态：站点信息、当前用户、未读角标。
 */
class MainViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(MainState(baseUrl = container.sessionStore.baseUrl))
    val state: StateFlow<MainState> = _state.asStateFlow()

    private val repo get() = container.repository

    init {
        boot()
    }

    fun boot() {
        _state.update { it.copy(booting = true, bootError = null) }
        viewModelScope.launch {
            val info = runCatching { repo.siteBasicInfo() }.getOrNull()
            val user = repo.currentUserOrNull()
            // 已登录但本地没存 CSRF token（老版本升级上来 / 被清过）时补一次
            if (user != null && container.sessionStore.csrfTokens.current() == null) {
                repo.refreshCsrfToken()
            }
            val unread = runCatching {
                repo.refreshSession().allUnreadNotificationsCount
            }.getOrDefault(0)

            _state.update {
                it.copy(
                    booting = false,
                    baseUrl = container.sessionStore.baseUrl,
                    siteTitle = info?.title ?: "LINUX DO",
                    siteIcon = info?.logoSmallUrl ?: info?.appleTouchIconUrl ?: info?.faviconUrl,
                    user = user,
                    unreadNotifications = unread,
                    bootError = if (info == null) "无法连接站点，检查网络或服务器地址" else null,
                )
            }
        }
    }

    fun refreshUnread() {
        if (!container.sessionStore.isLoggedIn) return
        viewModelScope.launch {
            runCatching { repo.refreshSession() }.onSuccess { s ->
                _state.update { it.copy(unreadNotifications = s.allUnreadNotificationsCount) }
            }
        }
    }

    fun setUser(user: User?) = _state.update { it.copy(user = user) }

    fun logout() {
        viewModelScope.launch {
            repo.logout()
            _state.update { it.copy(user = null, unreadNotifications = 0) }
        }
    }

    /** 切换服务器地址；返回是否可用 */
    fun changeServer(url: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            container.sessionStore.baseUrl = url.trimEnd('/')
            container.sessionStore.clearCookies()
            // 丢掉落盘 cookie 后必须重建 client：Origin/Referer 与 CSRF 都是按站点算的
            container.onSiteChanged()
            repo.invalidateLists()
            _state.update { it.copy(booting = true, user = null) }
            val ok = runCatching { repo.siteBasicInfo() }.isSuccess
            _state.update { it.copy(booting = false) }
            boot()
            onDone(ok)
        }
    }

    suspend fun loadProfile(username: String) =
        runCatching { repo.user(username) }.getOrNull()
}

class MainViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MainViewModel(container) as T
}
