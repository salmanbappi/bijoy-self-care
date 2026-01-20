package com.bijoy.selfcare

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bijoy.selfcare.api.*
import com.bijoy.selfcare.ui.theme.BijoySelfCareTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BijoySelfCareTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppContent()
                }
            }
        }
    }
}

@Composable
fun AppContent() {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("bijoy_prefs", Context.MODE_PRIVATE) }
    
    var username by remember { mutableStateOf(sharedPrefs.getString("username", "") ?: "") }
    var password by remember { mutableStateOf(sharedPrefs.getString("password", "") ?: "") }
    
    var dashboardData by remember { mutableStateOf<DashboardData?>(null) }
    var isLoggedIn by remember { mutableStateOf(false) }
    val api = remember { BijoyApi() }

    if (isLoggedIn && dashboardData != null) {
        MainScreen(data = dashboardData!!, api = api, onLogout = {
            sharedPrefs.edit().clear().apply()
            username = ""
            password = ""
            isLoggedIn = false
            dashboardData = null
        })
    } else {
        LoginScreen(api, username, password) { data, u, p ->
            sharedPrefs.edit().putString("username", u).putString("password", p).apply()
            username = u
            password = p
            dashboardData = data
            isLoggedIn = true
        }
    }
}

@Composable
fun MainScreen(data: DashboardData, api: BijoyApi, onLogout: () -> Unit) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Home, null) },
                    label = { Text("Home") },
                    selected = currentRoute == "dashboard",
                    onClick = { if(currentRoute != "dashboard") navController.navigate("dashboard") { popUpTo(0) } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Speed, null) },
                    label = { Text("Usage") },
                    selected = currentRoute == "usage",
                    onClick = { if(currentRoute != "usage") navController.navigate("usage") { popUpTo(0) } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.History, null) },
                    label = { Text("Bills") },
                    selected = currentRoute == "payment",
                    onClick = { if(currentRoute != "payment") navController.navigate("payment") { popUpTo(0) } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Settings, null) },
                    label = { Text("Settings") },
                    selected = currentRoute == "settings",
                    onClick = { if(currentRoute != "settings") navController.navigate("settings") { popUpTo(0) } }
                )
            }
        }
    ) { innerPadding ->
        NavHost(navController = navController, startDestination = "dashboard", modifier = Modifier.padding(innerPadding)) {
            composable("dashboard") { DashboardScreen(data, api) }
            composable("usage") { UsageScreen(api) }
            composable("payment") { PaymentScreen(api) }
            composable("settings") { SettingsScreen(onLogout) }
        }
    }
}

@Composable
fun LoginScreen(api: BijoyApi, initialU: String, initialP: String, onLoginSuccess: (DashboardData, String, String) -> Unit) {
    var username by remember { mutableStateOf(if(initialU.isEmpty()) "840135" else initialU) }
    var password by remember { mutableStateOf(if(initialP.isEmpty()) "6666" else initialP) }
    var status by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        if (initialU.isNotEmpty() && initialP.isNotEmpty()) {
            isLoading = true
            val result = api.login(initialU, initialP)
            isLoading = false
            if (result is LoginResult.Success) onLoginSuccess(result.data, initialU, initialP)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Bijoy Self Care", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(48.dp))
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Customer ID") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(32.dp))
        Button(onClick = {
            isLoading = true
            scope.launch {
                val result = api.login(username, password)
                isLoading = false
                if (result is LoginResult.Success) onLoginSuccess(result.data, username, password) else status = "Login Failed"
            }
        }, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = !isLoading) {
            Text("LOGIN", fontWeight = FontWeight.Bold)
        }
        if (isLoading) CircularProgressIndicator(Modifier.padding(top = 16.dp))
        Text(status, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun DashboardScreen(data: DashboardData, api: BijoyApi) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Dashboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item {
            LiveSpeedCard(api)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Welcome,", style = MaterialTheme.typography.titleMedium)
                    Text(data.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Connection: ", style = MaterialTheme.typography.bodyMedium)
                        val isOnline = data.connectionStatus.contains("ONLINE", true)
                        Text(data.connectionStatus, color = if(isOnline) Color(0xFF00C853) else Color.Red, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoCardCompact("Package", data.packageInfo, Modifier.weight(1f))
                InfoCardCompact("Plan Rate", data.planRate, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoCardCompact("Expiry Date", data.expiryDate, Modifier.weight(1f))
                InfoCardCompact("Account Status", data.accountStatus, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun LiveSpeedCard(api: BijoyApi) {
    var speed by remember { mutableStateOf(LiveSpeed(0.0, 0.0)) }
    val history = remember { mutableStateListOf<LiveSpeed>() }

    LaunchedEffect(Unit) {
        api.getSpeedFlow().collect { newSpeed ->
            speed = newSpeed
            history.add(newSpeed)
            if (history.size > 50) history.removeAt(0)
        }
    }

    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Real-Time Bandwidth", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SpeedDisplay("Download", speed.download, Color(0xFF4CAF50))
                SpeedDisplay("Upload", speed.upload, Color(0xFF2196F3))
            }
            Spacer(Modifier.height(16.dp))
            RealTimeChart(history)
        }
    }
}

@Composable
fun SpeedDisplay(label: String, value: Double, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        Text(String.format(Locale.US, "%.1f", value), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text("Kbps", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun RealTimeChart(history: List<LiveSpeed>) {
    Canvas(Modifier.fillMaxWidth().height(80.dp)) {
        if (history.size < 2) return@Canvas
        val maxVal = history.maxOf { it.download.coerceAtLeast(it.upload) }.coerceAtLeast(100.0)
        val stepX = size.width / 49f
        val scaleY = size.height / maxVal.toFloat()
        val downPath = Path()
        val upPath = Path()
        history.forEachIndexed { i, s ->
            val x = i * stepX
            val dy = size.height - (s.download.toFloat() * scaleY)
            val uy = size.height - (s.upload.toFloat() * scaleY)
            if (i == 0) { downPath.moveTo(x, dy); upPath.moveTo(x, uy) } 
            else { downPath.lineTo(x, dy); upPath.lineTo(x, uy) }
        }
        drawPath(downPath, Color(0xFF4CAF50), style = Stroke(width = 2.5.dp.toPx()))
        drawPath(upPath, Color(0xFF2196F3), style = Stroke(width = 2.5.dp.toPx()))
    }
}

@Composable
fun InfoCardCompact(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun UsageScreen(api: BijoyApi) {
    var usageData by remember { mutableStateOf<List<UsageData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { usageData = api.getUsageGraph(); isLoading = false }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Usage Reports", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(usageData) { item ->
                Card(Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(item.date, fontWeight = FontWeight.Bold) },
                        supportingContent = { Text("Download: ${formatBytes(item.download)} | Upload: ${formatBytes(item.upload)}") }
                    )
                }
            }
        }
    }
}

@Composable
fun PaymentScreen(api: BijoyApi) {
    var history by remember { mutableStateOf<List<PaymentHistoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { history = api.getPaymentHistory(); isLoading = false }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Billing History", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        if (isLoading) CircularProgressIndicator()
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(history) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.date, fontWeight = FontWeight.Bold)
                            Text(item.amount, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Method: ${item.method}", style = MaterialTheme.typography.bodySmall)
                        Text("Status: ${item.status}", color = if(item.status.contains("Success", true)) Color(0xFF00C853) else Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("bijoy_prefs", Context.MODE_PRIVATE) }
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Account Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Stored User: ${sharedPrefs.getString("username", "N/A")}")
                Text("Service: Bijoy Broadband")
            }
        }
        
        Spacer(Modifier.weight(1f))
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
            Text("CLEAR DATA & LOGOUT", fontWeight = FontWeight.Bold)
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.US, "%.1f %cB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
