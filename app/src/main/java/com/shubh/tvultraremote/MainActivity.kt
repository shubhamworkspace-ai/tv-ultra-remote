package com.shubh.tvultraremote

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var hidController: HidController
    private val adb = TvAdbController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var btStatus by mutableStateOf("BT HID Ready")
        hidController = HidController(this) { btStatus = it }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.RECORD_AUDIO), 101)
        }
        hidController.initialize()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF00E5FF),
                secondary = Color(0xFF7C4DFF),
                background = Color(0xFF0B0F19),
                surface = Color(0xFF161F30)
            )) {
                MainControllerApp(btStatus)
            }
        }
    }

    @Composable
    fun MainControllerApp(btStatus: String) {
        val scope = rememberCoroutineScope()
        var tvIp by remember { mutableStateOf("192.168.1.50") }
        var statusBanner by remember { mutableStateOf("Enter TV IP & Tap Connect") }
        var selectedTab by remember { mutableStateOf(0) }
        var textInput by remember { mutableStateOf("") }

        val voiceLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { res ->
            if (res.resultCode == Activity.RESULT_OK) {
                val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                if (!spoken.isNullOrBlank()) {
                    scope.launch {
                        statusBanner = adb.handleVoiceCommand(spoken)
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0F19))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Connection Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = tvIp,
                    onValueChange = { tvIp = it },
                    label = { Text("TV IP Address") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(
                    onClick = {
                        scope.launch {
                            statusBanner = "Connecting..."
                            val ok = adb.connect(tvIp)
                            statusBanner = if (ok) "Connected to TV ($tvIp)" else "ADB Connection Failed (Check TV Wireless Debugging)"
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(54.dp)
                ) {
                    Text("Connect", fontWeight = FontWeight.Bold)
                }
            }

            // Status & Voice Floating Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161F30), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(statusBanner, color = Color(0xFF00E5FF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(btStatus, color = Color.Gray, fontSize = 11.sp)
                }
                Button(
                    onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say 'Open YouTube' or 'Search ... on YouTube'")
                        }
                        voiceLauncher.launch(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("🎤 Voice", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            // Navigation Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF161F30),
                contentColor = Color(0xFF00E5FF)
            ) {
                listOf("Remote", "Trackpad", "Apps & Keyboard", "Screen Mirror").forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            // Active View
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    0 -> RemoteDpadTab(onKey = { code -> scope.launch { adb.keyEvent(code) } })
                    1 -> TrackpadTab()
                    2 -> AppsAndKeyboardTab(
                        textInput = textInput,
                        onTextChange = { textInput = it },
                        onSendText = {
                            val t = textInput
                            textInput = ""
                            scope.launch { adb.sendText(t) }
                        },
                        onLaunchApp = { app ->
                            scope.launch {
                                adb.launchApp(app)
                                statusBanner = "Launched ${app.uppercase()}"
                            }
                        }
                    )
                    3 -> ScreenMirrorTab()
                }
            }
        }
    }

    @Composable
    fun RemoteDpadTab(onKey: (Int) -> Unit) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Power & Mute Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(
                    onClick = { onKey(26) }, // KEYCODE_POWER
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000))
                ) { Text("⏻ Power", color = Color.White, fontWeight = FontWeight.Bold) }

                Button(
                    onClick = { onKey(164) }, // KEYCODE_VOLUME_MUTE
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263238))
                ) { Text("🔇 Mute", color = Color.White) }
            }

            // Crystal-Clear Circular D-Pad
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .background(Color(0xFF161F30), CircleShape)
                    .border(2.dp, Color(0xFF00E5FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                TextButton(onClick = { onKey(19) }, modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)) {
                    Text("▲", fontSize = 26.sp, color = Color.White)
                }
                TextButton(onClick = { onKey(20) }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)) {
                    Text("▼", fontSize = 26.sp, color = Color.White)
                }
                TextButton(onClick = { onKey(21) }, modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp)) {
                    Text("◀", fontSize = 26.sp, color = Color.White)
                }
                TextButton(onClick = { onKey(22) }, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)) {
                    Text("▶", fontSize = 26.sp, color = Color.White)
                }
                Button(
                    onClick = { onKey(23) }, // DPAD_CENTER
                    shape = CircleShape,
                    modifier = Modifier.size(84.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Text("OK", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
            }

            // Back / Home / Menu Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                FilledTonalButton(onClick = { onKey(4) }) { Text("⬅ Back") }
                FilledTonalButton(onClick = { onKey(3) }) { Text("🏠 Home") }
                FilledTonalButton(onClick = { onKey(82) }) { Text("☰ Menu") }
            }

            // Media & Volume Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = { onKey(25) }) { Text("🔉 Vol -") }
                Button(onClick = { onKey(85) }) { Text("⏯ Play/Pause") }
                Button(onClick = { onKey(24) }) { Text("🔊 Vol +") }
            }
        }
    }

    @Composable
    fun TrackpadTab() {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF161F30), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFF263238), RoundedCornerShape(20.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            hidController.sendMouseEvent(drag.x * 1.5f, drag.y * 1.5f)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                hidController.sendMouseEvent(0f, 0f, leftClick = true)
                                hidController.sendMouseEvent(0f, 0f, leftClick = false)
                            },
                            onLongPress = {
                                hidController.sendMouseEvent(0f, 0f, rightClick = true)
                                hidController.sendMouseEvent(0f, 0f, rightClick = false)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("60Hz Zero-Lag Bluetooth Trackpad", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Drag to Move • Tap = Left Click • Hold = Right Click/Back", color = Color.Gray, fontSize = 12.sp)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        hidController.sendMouseEvent(0f, 0f, leftClick = true)
                        hidController.sendMouseEvent(0f, 0f, leftClick = false)
                    },
                    modifier = Modifier.weight(1f).height(52.dp)
                ) { Text("Left Click") }

                Button(
                    onClick = {
                        hidController.sendMouseEvent(0f, 0f, rightClick = true)
                        hidController.sendMouseEvent(0f, 0f, rightClick = false)
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263238))
                ) { Text("Right Click (Back)", color = Color.White) }
            }
        }
    }

    @Composable
    fun AppsAndKeyboardTab(
        textInput: String,
        onTextChange: (String) -> Unit,
        onSendText: () -> Unit,
        onLaunchApp: (String) -> Unit
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Instant TV Keyboard Injection", color = Color.White, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = onTextChange,
                    placeholder = { Text("Type text or password to send to TV...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(onClick = onSendText, modifier = Modifier.height(54.dp)) {
                    Text("Send")
                }
            }

            Text("One-Tap App Launcher", color = Color.White, fontWeight = FontWeight.Bold)
            val apps = listOf(
                "YouTube" to Color(0xFFFF1744),
                "Netflix" to Color(0xFFD50000),
                "Prime Video" to Color(0xFF00B0FF),
                "Hotstar" to Color(0xFF1DE9B6),
                "Spotify" to Color(0xFF00E676),
                "VLC" to Color(0xFFFF9100)
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                apps.chunked(2).forEach { rowApps ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowApps.forEach { (name, color) ->
                            Button(
                                onClick = { onLaunchApp(name) },
                                modifier = Modifier.weight(1f).height(58.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.85f))
                            ) {
                                Text(name, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ScreenMirrorTab() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF161F30), RoundedCornerShape(20.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Hardware Wi-Fi Direct / Miracast Mirroring", color = Color(0xFF00E5FF), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Opens your phone's direct hardware display encoder (Miracast / Cast) for ultra-low-latency mirroring without router buffering.",
                color = Color.LightGray,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { startActivity(Intent(Settings.ACTION_CAST_SETTINGS)) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("📺 Start Zero-Lag Screen Mirror", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
