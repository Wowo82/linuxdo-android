package org.linuxdo.android.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.linuxdo.android.data.UserRepository
import org.linuxdo.android.data.model.Category

data class CategoriesState(
    val categories: List<Category> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * 版块页状态。
 *
 * 分类树很少变，`UserRepository` 里还有一层缓存；这里的 ViewModel 只是为了
 * 让旋转屏幕 / 切 tab 回来时不重拉、不丢已加载的列表。
 */
class CategoriesViewModel(private val repo: UserRepository) : ViewModel() {

    private val _state = MutableStateFlow(CategoriesState())
    val state: StateFlow<CategoriesState> = _state.asStateFlow()

    private var loadedOnce = false

    fun ensureLoaded() {
        if (loadedOnce && _state.value.categories.isNotEmpty()) return
        load()
    }

    fun reload() = load(forceReload = true)

    private fun load(forceReload: Boolean = false) {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.categories(forceReload) }
                .onSuccess { list ->
                    loadedOnce = true
                    _state.update {
                        it.copy(categories = list.categories, loading = false, error = null)
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, error = e.message ?: "版块加载失败")
                    }
                }
        }
    }
}
