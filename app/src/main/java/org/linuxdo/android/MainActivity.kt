package org.linuxdo.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import org.linuxdo.android.ui.MainScreen
import org.linuxdo.android.ui.MainViewModel
import org.linuxdo.android.ui.MainViewModelFactory
import org.linuxdo.android.ui.theme.LinuxDoTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as LinuxDoApp).container

        setContent {
            LinuxDoTheme {
                val vm: MainViewModel = viewModel(factory = MainViewModelFactory(container))
                val state by vm.state.collectAsState()
                MainScreen(vm = vm, state = state, container = container)
            }
        }
    }

    /** 从浏览器点 https://linux.do/t/... 过来 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
