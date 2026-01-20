package com.bijoy.selfcare

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
    var dashboardData by remember { mutableStateOf<DashboardData?>(null) }
    var isLoggedIn by remember { mutableStateOf(false) }
    val api = remember { BijoyApi() }

    if (isLoggedIn && dashboardData != null) {
        MainScreen(data = dashboardData!!, api = api) {
            isLoggedIn = false
            dashboardData = null
        }
    } else {
        LoginScreen(api) { data ->
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
                    icon = { Icon(Icons.Filled.Dashboard, null) },
                    label = { Text("Dashboard") },
                    selected = currentRoute == "dashboard",
                    onClick = { navController.navigate("dashboard") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Speed, null) },
                    label = { Text("Usage") },
                    selected = currentRoute == "usage",
                    onClick = { navController.navigate("usage") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.History, null) },
                    label = { Text("Payments") },
                    selected = currentRoute == "payment",
                    onClick = { navController.navigate("payment") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Settings, null) },
                    label = { Text("Settings") },
                    selected = currentRoute == "support",
                    onClick = { navController.navigate("support") }
                )
            }
        }
    ) { innerPadding ->
        NavHost(navController = navController, startDestination = "dashboard", modifier = Modifier.padding(innerPadding)) {
            composable("dashboard") { DashboardScreen(data, api, onLogout) }
            composable("usage") { UsageScreen(api) }
            composable("payment") { PaymentScreen(api) }
            composable("support") { SupportScreen(onLogout) }
        }
    }
}

@Composable
fun LoginScreen(api: BijoyApi, onLoginSuccess: (DashboardData) -> Unit) {
    var username by remember { mutableStateOf("840135") }
    var password by remember { mutableStateOf("6666") }
    var status by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        isLoading = true
        val result = api.login(username, password)
        isLoading = false
        if (result is LoginResult.Success) onLoginSuccess(result.data)
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Bijoy Self Care", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(48.dp))
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Customer ID") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            isLoading = true
            scope.launch {
                val result = api.login(username, password)
                isLoading = false
                if (result is LoginResult.Success) onLoginSuccess(result.data) else status = "Login Failed"
            }
        }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) {
            Text("Login")
        }
        if (isLoading) CircularProgressIndicator(Modifier.padding(top = 16.dp))
        Text(status, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun DashboardScreen(data: DashboardData, api: BijoyApi, onLogout: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Dashboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            
            // Live Speed Card
            LiveSpeedCard(api)
            Spacer(Modifier.height(16.dp))
            
            // User Card
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Welcome, ${data.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Package: ${data.packageInfo}", style = MaterialTheme.typography.bodyMedium)
                    Text("Status: ${data.connectionStatus}", color = if(data.connectionStatus.contains("ONLINE")) Color(0xFF00C853) else Color.Red, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
            
            // Detail Cards
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoCardCompact("Expiry", data.expiryDate, Modifier.weight(1f))
                InfoCardCompact("Plan Rate", data.planRate, Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            InfoCardCompact("Account Status", data.accountStatus, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun LiveSpeedCard(api: BijoyApi) {
    var speed by remember { mutableStateOf(LiveSpeed(0.0, 0.0)) }
    val history = remember { mutableStateListOf<LiveSpeed>() }

    LaunchedEffect(Unit) {
        while(true) {
            val newSpeed = api.getLiveSpeed()
            speed = newSpeed
            history.add(newSpeed)
            if (history.size > 20) history.removeAt(0)
            delay(2000)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Live Speed", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SpeedIndicator("Download", speed.download, Color(0xFF4CAF50))
                SpeedIndicator("Upload", speed.upload, Color(0xFF2196F3))
            }
            Spacer(Modifier.height(16.dp))
            RealTimeChart(history)
        }
    }
}

@Composable
fun SpeedIndicator(label: String, value: Double, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        Text(String.format(Locale.US, "%.2f Kbps", value), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RealTimeChart(history: List<LiveSpeed>) {
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        if (history.size < 2) return@Canvas
        val maxVal = history.maxOf { it.download.coerceAtLeast(it.upload) }.coerceAtLeast(100.0)
        val stepX = size.width / 19f
        val scaleY = size.height / maxVal.toFloat()

        val downPath = Path()
        val upPath = Path()

        history.forEachIndexed { i, s ->
            val x = i * stepX
            val dy = size.height - (s.download.toFloat() * scaleY)
            val uy = size.height - (s.upload.toFloat() * scaleY)
            if (i == 0) {
                downPath.moveTo(x, dy)
                upPath.moveTo(x, uy)
            } else {
                downPath.lineTo(x, dy)
                upPath.lineTo(x, uy)
            }
        }

        drawPath(downPath, Color(0xFF4CAF50), style = Stroke(width = 2.dp.toPx()))
        drawPath(upPath, Color(0xFF2196F3), style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
fun InfoCardCompact(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun UsageScreen(api: BijoyApi) {
    var usageData by remember { mutableStateOf<List<UsageData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        usageData = api.getUsageGraph()
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Daily Usage", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        if (isLoading) CircularProgressIndicator()
        else {
            LazyColumn {
                items(usageData) { item ->
                    ListItem(
                        headlineContent = { Text(item.date) },
                        supportingContent = { Text("Down: ${formatBytes(item.download)} | Up: ${formatBytes(item.upload)}") }
                    )
                    Divider()
                }
            }
        }
    }
}

@Composable
fun PaymentScreen(api: BijoyApi) {
    var history by remember { mutableStateOf<List<PaymentHistoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        history = api.getPaymentHistory()
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Payment History", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        if (isLoading) CircularProgressIndicator()
        else {
            LazyColumn {
                items(history) { item ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(item.date, fontWeight = FontWeight.Bold)
                                Text(item.amount, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Text("Method: ${item.method} | Status: ${item.status}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SupportScreen(onLogout: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Support & Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Open Support Ticket") }
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("View Reports") }
        Spacer(Modifier.weight(1f))
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
            Text("Logout")
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.US, "%.1f %cB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}