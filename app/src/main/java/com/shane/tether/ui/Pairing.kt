package com.shane.tether.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.shane.tether.Cloud
import com.shane.tether.Prefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun PairingScreen(onPaired: () -> Unit, onSkip: () -> Unit) {
    val scope = rememberCoroutineScope()
    var qr by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@rememberLauncherForActivityResult
        busy = true
        error = null
        scope.launch {
            try {
                Cloud.joinCircle(text)
                onPaired()
            } catch (e: Exception) {
                error = e.message ?: "Couldn't pair. Check your internet connection."
            } finally {
                busy = false
            }
        }
    }

    // Creator side: wait for the partner to scan.
    val code = qr
    if (code != null) {
        LaunchedEffect(code) {
            try {
                Cloud.partnerJoinedFlow().first { it }
                Prefs.pairComplete = true
                Cloud.saveProfile()
                onPaired()
            } catch (e: Exception) {
                error = "Lost connection while waiting. Try again."
                qr = null
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("💞", fontSize = 56.sp, modifier = Modifier.heartbeat())
        Text("Connect with your love", fontSize = 28.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        Text(
            "One of you shows a code, the other scans it. The code carries your shared encryption key, so only show it to your partner.",
            textAlign = TextAlign.Center,
        )

        if (code != null) {
            val bmp = remember(code) { BarcodeEncoder().encodeBitmap(code, BarcodeFormat.QR_CODE, 720, 720) }
            Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)) {
                Image(bmp.asImageBitmap(), "Pairing code", Modifier.size(280.dp).padding(12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Waiting for your partner to scan…")
            }
            TextButton(onClick = { qr = null }) { Text("Cancel") }
        } else {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            qr = Cloud.createCircle()
                        } catch (e: Exception) {
                            error = "Couldn't reach the server. Check your internet connection and Firebase setup.\n(${e.message})"
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.QrCode, null)
                Spacer(Modifier.width(10.dp))
                Text("Show my pairing code")
            }
            OutlinedButton(
                onClick = {
                    scanner.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("Scan the code on your partner's phone")
                            .setBeepEnabled(false)
                            .setOrientationLocked(false)
                    )
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.QrCodeScanner, null)
                Spacer(Modifier.width(10.dp))
                Text("Scan my partner's code")
            }
            if (busy) CircularProgressIndicator()
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSkip) { Text("Skip for now — pair later in Settings") }
    }
}
