package com.cleanguard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cleanguard.app.navigation.CleanGuardNavHost
import com.cleanguard.app.ui.theme.CleanGuardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CleanGuardApplication).container
        setContent {
            CleanGuardTheme {
                CleanGuardNavHost(container)
            }
        }
    }
}

