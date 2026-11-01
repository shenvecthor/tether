package com.shane.tether

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.shane.tether.ui.HomeScreen
import com.shane.tether.ui.OnboardingScreen
import com.shane.tether.ui.PairingScreen
import com.shane.tether.ui.TetherTheme

class MainActivity : ComponentActivity() {

    companion object {
        @Volatile var chatTabOpen = false
        @Volatile var resumed = false
        val chatVisible: Boolean get() = chatTabOpen && resumed
    }

    private val tabRequest = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        tabRequest.value = intent?.getStringExtra("tab")
        setContent {
            TetherTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(tabRequest)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tabRequest.value = intent.getStringExtra("tab")
    }

    override fun onResume() {
        super.onResume()
        resumed = true
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }
}

private enum class Stage { ONBOARD, PAIR, HOME }

private fun currentStage(): Stage = when {
    !Prefs.onboarded -> Stage.ONBOARD
    !Prefs.isPaired && !Prefs.skippedPairing -> Stage.PAIR
    else -> Stage.HOME
}

@Composable
private fun AppRoot(tabRequest: MutableState<String?>) {
    var stage by remember { mutableStateOf(currentStage()) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var crash by remember { mutableStateOf(CrashLog.last(ctx)) }
    crash?.let { report ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { CrashLog.clear(ctx); crash = null },
            title = { androidx.compose.material3.Text("Tether closed unexpectedly 😢") },
            text = {
                androidx.compose.material3.Text(
                    "Sorry about that! Copy the crash report and send it to whoever builds your copy of Tether so it can be fixed.\n\n" +
                        report.lineSequence().take(6).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                androidx.compose.material3.Button(onClick = {
                    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("Tether crash report", report))
                    android.widget.Toast.makeText(ctx, "Crash report copied", android.widget.Toast.LENGTH_SHORT).show()
                    CrashLog.clear(ctx)
                    crash = null
                }) { androidx.compose.material3.Text("Copy report") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { CrashLog.clear(ctx); crash = null }) {
                    androidx.compose.material3.Text("Dismiss")
                }
            },
        )
    }
    Box(Modifier.fillMaxSize()) {
        when (stage) {
            Stage.ONBOARD -> OnboardingScreen(
                onDone = {
                    Prefs.onboarded = true
                    stage = currentStage()
                },
                onSkip = {
                    Prefs.onboarded = true
                    Prefs.skippedPairing = true
                    stage = currentStage()
                },
            )
            Stage.PAIR -> PairingScreen(
                onPaired = {
                    Prefs.skippedPairing = false
                    stage = currentStage()
                },
                onSkip = {
                    Prefs.skippedPairing = true
                    stage = currentStage()
                },
            )
            Stage.HOME -> HomeScreen(tabRequest, onPairingChanged = { stage = currentStage() })
        }
    }
}
