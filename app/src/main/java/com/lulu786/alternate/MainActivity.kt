package com.lulu786.alternate

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.lulu786.alternate.ui.AlternateNav
import com.lulu786.alternate.ui.theme.AlternateTheme

// FragmentActivity (not ComponentActivity) because androidx.biometric needs it.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AlternateTheme { AlternateNav() } }
    }
}
