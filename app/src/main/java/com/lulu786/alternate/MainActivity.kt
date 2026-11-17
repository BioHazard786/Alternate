package com.lulu786.alternate

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lulu786.alternate.data.ThemeMode
import com.lulu786.alternate.ui.AlternateNav
import com.lulu786.alternate.ui.AppViewModel
import com.lulu786.alternate.ui.lock.LockScreen
import com.lulu786.alternate.ui.theme.AlternateTheme

// FragmentActivity (not ComponentActivity) because androidx.biometric needs it.
class MainActivity : FragmentActivity() {
    private var vm: AppViewModel? = null
    private val container get() = (application as AlternateApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: AppViewModel = viewModel(factory = viewModelFactory { initializer { AppViewModel(container) } })
            vm = model
            val s = model.settings.collectAsStateWithLifecycle().value
            val dark = when (s?.theme) { ThemeMode.Light -> false; ThemeMode.Dark, ThemeMode.Amoled -> true; else -> isSystemInDarkTheme() }
            // Bar icons follow the app's own theme: with a Light app on a dark system they stayed white on a light bar.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.argb(0xe6, 0xff, 0xff, 0xff), Color.argb(0x80, 0x1b, 0x1b, 0x1b)) { dark },
                )
                onDispose { }
            }
            LaunchedEffect(s) { s?.let(model::onSettingsLoaded) }
            // No recents preview while an app lock is on.
            LaunchedEffect(s?.lock?.enabled) {
                if (s?.lock?.enabled == true) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            CompositionLocalProvider(LocalContainer provides container) {
                AlternateTheme(dark, s?.dynamicColor ?: true, s?.theme == ThemeMode.Amoled) {
                    // Surface supplies the content colour: without it text defaults to black, unreadable in dark mode.
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        val locked = model.locked
                        when {
                            s == null || locked == null -> Unit // one DataStore read; the window background covers it
                            locked -> LockScreen(model, s.lock)
                            else -> AlternateNav(model, s)
                        }
                    }
                }
            }
        }
    }

    override fun onStart() { super.onStart(); vm?.onForeground() }
    override fun onStop() { vm?.onBackground(); super.onStop() }
}
