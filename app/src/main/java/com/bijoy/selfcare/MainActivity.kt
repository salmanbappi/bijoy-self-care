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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bijoy.selfcare.api.*
import com.bijoy.selfcare.ui.theme.BijoySelfCareTheme
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BijoySelfCareTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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

    if (isLoggedIn && dashboardData != null) {
        MainScreen(data = dashboardData!!) {
            isLoggedIn = false
            dashboardData = null
        }
    } else {
        LoginScreen { data ->
            dashboardData = data
            isLoggedIn = true
        }
    }
}

@Composable
fun MainScreen(data: DashboardData, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val api = remember { BijoyApi() }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val currentRoute = navController.currentBackStackEntry?.destination?.route
                
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") },
                    selected = false,
                    onClick = { navController.navigate("dashboard") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.BarChart, contentDescription = "Usage") },
                    label = { Text("Usage") },
                    selected = false,
                    onClick = { navController.navigate("usage") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Payment, contentDescription = "Payment") },
                    label = { Text("Payment") },
                    selected = false,
                    onClick = { navController.navigate("payment") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.SupportAgent, contentDescription = "Support") },
                    label = { Text("Support") },
                    selected = false,
                    onClick = { navController.navigate("support") }
                )
            }
        }
    ) { innerPadding ->
        NavHost(navController = navController, startDestination = "dashboard", modifier = Modifier.padding(innerPadding)) {
            composable("dashboard") { DashboardScreen(data, onLogout) }
            composable("usage") { UsageScreen(api) }
            composable("payment") { PaymentScreen(api) }
            composable("support") { SupportScreen(api) }
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: (DashboardData) -> Unit) {
    var username by remember { mutableStateOf("840135") }
    var password by remember { mutableStateOf("6666") }
    var status by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val api = remember { BijoyApi() }
    
    LaunchedEffect(Unit) {
        isLoading = true
        status = "Auto-logging in..."
        val result = api.login(username, password)
        isLoading = false
        if (result is LoginResult.Success) {
            onLoginSuccess(result.data)
        } else {
            status = "Auto-login failed. Please try manually."
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Bijoy Self Care", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                isLoading = true
                status = "Logging in..."
                scope.launch {
                    val result = api.login(username, password)
                    isLoading = false
                    when (result) {
                        is LoginResult.Success -> onLoginSuccess(result.data)
                        is LoginResult.Error -> status = result.message
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text("Login")
        }
        
        if (isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
        }
        
        if (status.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(status, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun DashboardScreen(data: DashboardData, onLogout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text("Dashboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Welcome,", style = MaterialTheme.typography.titleMedium)
                Text(data.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Status: ", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        data.connectionStatus, 
                        style = MaterialTheme.typography.bodyLarge, 
                        fontWeight = FontWeight.Bold,
                        color = if(data.connectionStatus.contains("ONLINE", true)) Color(0xFF00C853) else Color.Red
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoCardCompact("Package", data.packageInfo, Modifier.weight(1f))
            InfoCardCompact("Account", data.accountStatus, Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("Logout")
        }
    }
}

@Composable
fun InfoCardCompact(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
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
        Text("Internet Usage", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (usageData.isEmpty()) {
            Text("No usage data available.")
        } else {
            UsageChart(usageData)
            Spacer(modifier = Modifier.height(24.dp))
            LazyColumn {
                items(usageData) { item ->
                    ListItem(
                        headlineContent = { Text(item.date) },
                        supportingContent = { 
                            Text("Down: ${formatBytes(item.download)} | Up: ${formatBytes(item.upload)}") 
                        }
                    )
                    Divider()
                }
            }
        }
    }
}

@Composable
fun UsageChart(data: List<UsageData>) {
    val maxVal = data.maxOfOrNull { it.download.coerceAtLeast(it.upload) } ?: 1L
    
    Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        val barWidth = size.width / (data.size * 2.5f)
        val gap = barWidth / 2
        val scale = size.height / maxVal

        data.forEachIndexed { index, item ->
            val x = index * (barWidth * 2 + gap)
            
            // Download Bar (Green)
            val downHeight = item.download * scale
            drawRect(
                color = Color(0xFF4CAF50),
                topLeft = Offset(x, size.height - downHeight),
                size = Size(barWidth, downHeight)
            )
            
            // Upload Bar (Blue)
            val upHeight = item.upload * scale
            drawRect(
                color = Color(0xFF2196F3),
                topLeft = Offset(x + barWidth, size.height - upHeight),
                size = Size(barWidth, upHeight)
            )
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text("Green: Download", color = Color(0xFF4CAF50), fontSize = 12.sp)
        Spacer(modifier = Modifier.width(16.dp))
        Text("Blue: Upload", color = Color(0xFF2196F3), fontSize = 12.sp)
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.US, "%.1f %cB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
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
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn {
                items(history) { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(item.date, fontWeight = FontWeight.Bold)
                                Text(item.amount, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Method: ${item.method}")
                            Text("TrxID: ${item.trxId}", style = MaterialTheme.typography.bodySmall)
                            Text("Status: ${item.status}", color = if(item.status.contains("Success", true)) Color.Green else Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SupportScreen(api: BijoyApi) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Support", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Tickets & Reports will appear here.", style = MaterialTheme.typography.bodyLarge)
    }
}
