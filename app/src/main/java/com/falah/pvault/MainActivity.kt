package com.falah.pvault

import android.content.Intent
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.core.content.IntentCompat

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF2DD4BF), onPrimary = Color.Black,
    background = Color.Black, onBackground = Color.White,
    surface = Color(0xFF121212), onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1E), onSurfaceVariant = Color(0xFF9A9AA0),
    secondaryContainer = Color(0xFF1F2A29)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0F766E), onPrimary = Color.White,
    background = Color(0xFFF4F5F7), onBackground = Color(0xFF111111),
    surface = Color.White, onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFE6E8EC), onSurfaceVariant = Color(0xFF5F6368),
    secondaryContainer = Color(0xFFCDEDE8)
)

class MainActivity : ComponentActivity() {
    private val vm: VaultVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent {
            val dark = when (vm.themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT)
                else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            LaunchedEffect(vm.blockShots) {
                if (vm.blockShots) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme) { App(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        if (i == null) return
        when (i.action) {
            ACT_SCAN -> { vm.autoScan = true; vm.screen = Screen.Form(null) }
            ACT_ADD -> vm.screen = Screen.Form(null)
            ACT_OPEN -> {
                val id = i.getStringExtra("id")
                if (id != null && vm.docs.any { it.id == id }) vm.screen = Screen.Detail(id)
            }
            Intent.ACTION_SEND -> {
                val u = IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java)
                if (u != null) vm.receiveShared(listOf(u))
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val l = IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java)
                if (!l.isNullOrEmpty()) vm.receiveShared(l)
            }
            Intent.ACTION_VIEW -> i.data?.let { vm.receiveShared(listOf(it)) }
        }
        setIntent(Intent())
    }
}

@Composable
fun App(vm: VaultVM) {
    BackHandler(vm.screen != Screen.Home) { vm.back() }
    AnimatedContent(
        targetState = vm.screen,
        transitionSpec = {
            (fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.94f)) togetherWith fadeOut(tween(160))
        },
        label = "screens"
    ) { s ->
        when (s) {
            Screen.Home -> HomeScreen(vm)
            is Screen.Detail -> DetailScreen(vm, s.id)
            is Screen.Form -> FormScreen(vm, s.id)
            is Screen.Viewer -> ViewerScreen(vm, s.id)
            Screen.Reorder -> ReorderScreen(vm)
            Screen.Settings -> SettingsScreen(vm)
        }
    }
}
