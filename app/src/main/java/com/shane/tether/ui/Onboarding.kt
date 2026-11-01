@file:OptIn(ExperimentalMaterial3Api::class)

package com.shane.tether.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.shane.tether.Perms
import com.shane.tether.Prefs

@Composable
fun OnboardingScreen(onDone: () -> Unit, onSkip: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(Prefs.myName) }
    var phone by remember { mutableStateOf(Prefs.myPhone) }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    val fineLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { tick++ }
    val singleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { tick++ }

    val hasFine = remember(tick) { Perms.hasFine(ctx) }
    val hasBg = remember(tick) { Perms.hasBackground(ctx) }
    val hasNotif = remember(tick) { Perms.hasNotifications(ctx) }
    val hasBattery = remember(tick) { Perms.ignoresBatteryOpt(ctx) }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Tether 💞", fontSize = 40.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        Text(
            "Your own little world for two: see each other, send love, and stay close. Everything is end-to-end encrypted before it leaves your phone.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = phone, onValueChange = { phone = it },
            label = { Text("Your phone number (for one-tap calling)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Permissions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))

        PermRow(Icons.Filled.LocationOn, "Location", "So your partner can see where you are.", hasFine) {
            fineLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
        PermRow(
            Icons.Filled.MyLocation, "Allow all the time",
            "Needed to keep sharing while the app is closed. Choose \"Allow all the time\".",
            hasBg, enabled = hasFine,
        ) {
            if (Build.VERSION.SDK_INT >= 29) singleLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        PermRow(Icons.Filled.Notifications, "Notifications", "Arrivals, SOS alerts and messages.", hasNotif) {
            if (Build.VERSION.SDK_INT >= 33) singleLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        PermRow(
            Icons.Filled.BatteryChargingFull, "Unrestricted battery",
            "Stops Android from putting Tether to sleep.", hasBattery,
        ) {
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:${ctx.packageName}"))
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                Prefs.myName = name.trim()
                Prefs.myPhone = phone.trim()
                onDone()
            },
            enabled = name.isNotBlank() && hasFine,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Continue") }
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Skip for now — just let me look around 👀")
        }
        if (!hasBg && hasFine) {
            Text(
                "Tip: without \"Allow all the time\", sharing stops when the app is closed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PermRow(
    icon: ImageVector,
    title: String,
    desc: String,
    granted: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (granted) {
                Icon(Icons.Filled.CheckCircle, "Granted", tint = MaterialTheme.colorScheme.primary)
            } else {
                FilledTonalButton(onClick = onClick, enabled = enabled) { Text("Allow") }
            }
        }
    }
}
