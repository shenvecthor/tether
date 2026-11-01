package com.shane.tether

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shane.tether.ui.SosRed
import com.shane.tether.ui.TetherTheme
import kotlinx.coroutines.delay

/** Full-screen "Are you OK?" countdown shown after a hard impact while driving. */
class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TetherTheme {
                var remaining by remember { mutableIntStateOf(secondsLeft()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        remaining = secondsLeft()
                        if (remaining <= 0) {
                            finish()
                            break
                        }
                        delay(250)
                    }
                }
                Column(
                    Modifier.fillMaxSize().background(SosRed).systemBarsPadding().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Possible crash detected", color = Color.White, fontSize = 28.sp,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text("Your partner will be alerted with your location in", color = Color.White,
                        textAlign = TextAlign.Center)
                    Text("$remaining", color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            LocationService.send(this@CrashActivity, LocationService.ACTION_CANCEL_CRASH)
                            finish()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = SosRed),
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                    ) { Text("I'm OK", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            LocationService.send(this@CrashActivity, LocationService.ACTION_CRASH_NOW)
                            finish()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text("Alert my partner now") }
                }
            }
        }
    }

    private fun secondsLeft(): Int {
        val end = LocationService.crashCountdownEndsAt
        if (end == 0L) return 0
        return ((end - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
    }
}
