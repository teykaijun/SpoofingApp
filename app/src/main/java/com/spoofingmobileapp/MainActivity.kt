package com.spoofingmobileapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.spoofingmobileapp.ui.MainScreen
import com.spoofingmobileapp.ui.theme.LocationSpooferTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LocationSpooferTheme {
                MainScreen()
            }
        }
    }
}
