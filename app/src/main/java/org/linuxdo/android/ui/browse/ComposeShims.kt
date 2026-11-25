package org.linuxdo.android.ui.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** StateFlow 转 Compose State（collectAsState 接收 Flow 接收者） */
@Composable
fun <T> StateFlow<T>.collectState(): State<T> {
    val flow: Flow<T> = this
    return flow.collectAsState(this.value)
}

/** snapshotFlow 简写 */
fun <T> snapshotOf(block: () -> T): Flow<T> = snapshotFlow(block)
