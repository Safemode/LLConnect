package com.safemode.llconnect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.safemode.llconnect.ui.LLConnectRoot
import com.safemode.llconnect.ui.theme.LLConnectTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LLConnectTheme {
                LLConnectRoot()
            }
        }
    }
}
