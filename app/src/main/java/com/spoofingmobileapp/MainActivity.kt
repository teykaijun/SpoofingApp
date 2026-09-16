package com.spoofingmobileapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.spoofingmobileapp.spoof.RemoteCommand
import com.spoofingmobileapp.ui.MainScreen
import com.spoofingmobileapp.ui.MainViewModel
import com.spoofingmobileapp.ui.theme.LocationSpooferTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /**
     * Commands are applied in onResume, not on arrival: starting a location foreground service is
     * only allowed once the activity is actually in the foreground.
     */
    private var pendingCommand: RemoteCommand? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingCommand = RemoteCommand.parse(intent)
        setContent {
            LocationSpooferTheme {
                MainScreen(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        RemoteCommand.parse(intent)?.let { pendingCommand = it }
    }

    override fun onResume() {
        super.onResume()
        pendingCommand?.let { command ->
            pendingCommand = null
            viewModel.applyRemoteCommand(command)
        }
    }
}
