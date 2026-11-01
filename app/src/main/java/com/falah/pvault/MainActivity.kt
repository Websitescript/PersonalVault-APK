package com.falah.pvault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF2DD4BF), onPrimary = Color.Black,
                    background = Color.Black, surface = Color(0xFF121212),
                    onSurface = Color.White, onBackground = Color.White,
                    secondaryContainer = Color(0xFF1F2A29)
                )
            ) { App() }
        }
    }
}

@Composable
fun App(vm: VaultVM = viewModel()) {
    BackHandler(vm.screen != Screen.Home) { vm.back() }
    when (val s = vm.screen) {
        Screen.Home -> HomeScreen(vm)
        is Screen.Detail -> DetailScreen(vm, s.id)
        is Screen.Form -> FormScreen(vm, s.id)
        is Screen.Viewer -> ViewerScreen(vm, s.id)
        Screen.Reorder -> ReorderScreen(vm)
    }
}
