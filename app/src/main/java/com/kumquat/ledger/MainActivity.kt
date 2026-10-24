package com.kumquat.ledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kumquat.ledger.ui.KumquatRoot
import com.kumquat.ledger.ui.theme.KumquatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KumquatTheme {
                KumquatRoot()
            }
        }
    }
}
