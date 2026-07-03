package com.bijoy.selfcare

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.romainguy.kotlin.math.*
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

class AppState(val api: BijoyApi) {
    var dashboardData by mutableStateOf<DashboardData?>(null)
    var usageData by mutableStateOf<List<UsageData>>(emptyList())
    var paymentHistory by mutableStateOf<List<PaymentHistoryItem>>(emptyList())
    var tickets by mutableStateOf<List<TicketItem>>(emptyList())

    var isDashboardRefreshing by mutableStateOf(false)
    var isUsageLoading by mutableStateOf(false)
    var isPaymentLoading by mutableStateOf(false)
    var isTicketsLoading by mutableStateOf(false)

    var usageError by mutableStateOf("")
    var paymentError by mutableStateOf("")
    var ticketsError by mutableStateOf("")

    suspend fun refreshDashboard(username: String, pass: String) {
        isDashboardRefreshing = true
        val result = api.login(username, pass)
        if (result is LoginResult.Success) {
            dashboardData = result.data
        }
        isDashboardRefreshing = false
    }

    suspend fun loadUsage(force: Boolean = false) {
        if (usageData.isNotEmpty() && !force) return
        isUsageLoading = true
        usageError = ""
        try {
            val data = api.getUsageGraph()
            usageData = data
        } catch (e: Exception) {
            usageError = e.message ?: "Failed to load usage reports"
        } finally {
            isUsageLoading = false
        }
    }

    suspend fun loadPayment(force: Boolean = false) {
        if (paymentHistory.isNotEmpty() && !force) return
        isPaymentLoading = true
        paymentError = ""
        try {
            val data = api.getPaymentHistory()
            paymentHistory = data
        } catch (e: Exception) {
            paymentError = e.message ?: "Failed to load payment history"
        } finally {
            isPaymentLoading = false
        }
    }

    suspend fun loadTickets(force: Boolean = false) {
        if (tickets.isNotEmpty() && !force) return
        isTicketsLoading = true
        ticketsError = ""
        try {
            val data = api.getTickets()
            tickets = data
        } catch (e: Exception) {
            ticketsError = e.message ?: "Failed to load support tickets"
        } finally {
            isTicketsLoading = false
        }
    }
}

@Composable
fun AppContent() {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("bijoy_prefs", Context.MODE_PRIVATE) }
    
    var username by remember { mutableStateOf(sharedPrefs.getString("username", "") ?: "") }
    var password by remember { mutableStateOf(sharedPrefs.getString("password", "") ?: "") }
    
    val api = remember { BijoyApi() }
    val appState = remember { AppState(api) }
    var isLoggedIn by remember { mutableStateOf(false) }

    if (isLoggedIn && appState.dashboardData != null) {
        MainScreen(appState = appState, username = username, password = password, onLogout = {
            sharedPrefs.edit().clear().apply()
            username = ""
            password = ""
            isLoggedIn = false
            appState.dashboardData = null
            appState.usageData = emptyList()
            appState.paymentHistory = emptyList()
            appState.tickets = emptyList()
        })
    } else {
        LoginScreen(api, username, password) { data, u, p ->
            sharedPrefs.edit().putString("username", u).putString("password", p).apply()
            username = u
            password = p
            appState.dashboardData = data
            isLoggedIn = true
        }
    }
}

fun getTabRouteIndex(route: String?): Int {
    return when (route) {
        "dashboard" -> 0
        "usage" -> 1
        "tickets" -> 2
        "payment" -> 3
        "settings" -> 4
        else -> 0
    }
}

@Composable
fun MainScreen(appState: AppState, username: String, password: String, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                
                val items = listOf(
                    Triple("dashboard", "Home", Icons.Filled.Home),
                    Triple("usage", "Usage", Icons.Filled.BarChart),
                    Triple("tickets", "Tickets", Icons.Filled.ConfirmationNumber),
                    Triple("payment", "Bills", Icons.Filled.ReceiptLong),
                    Triple("settings", "Settings", Icons.Filled.Settings)
                )

                items.forEach { (route, label, icon) ->
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontWeight = FontWeight.Medium) },
                        selected = currentRoute == route,
                        onClick = {
                            if (currentRoute != route) {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.secondary,
                            unselectedTextColor = MaterialTheme.colorScheme.secondary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                val initialIndex = getTabRouteIndex(initialState.destination.route)
                val targetIndex = getTabRouteIndex(targetState.destination.route)
                if (targetIndex > initialIndex) {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(300))
                } else {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(300))
                }
            },
            exitTransition = {
                val initialIndex = getTabRouteIndex(initialState.destination.route)
                val targetIndex = getTabRouteIndex(targetState.destination.route)
                if (targetIndex > initialIndex) {
                    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(300))
                } else {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(300))
                }
            }
        ) {
            composable("dashboard") {
                DashboardScreen(appState, onRefresh = {
                    scope.launch { appState.refreshDashboard(username, password) }
                })
            }
            composable("usage") {
                UsageScreen(appState)
            }
            composable("tickets") {
                TicketsScreen(appState)
            }
            composable("payment") {
                PaymentScreen(appState)
            }
            composable("settings") {
                SettingsScreen(appState, onLogout)
            }
        }
    }
}

@Composable
fun LoginScreen(api: BijoyApi, initialU: String, initialP: String, onLoginSuccess: (DashboardData, String, String) -> Unit) {
    var username by remember { mutableStateOf(initialU) }
    var password by remember { mutableStateOf(initialP) }
    var status by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        if (initialU.isNotEmpty() && initialP.isNotEmpty()) {
            isLoading = true
            val result = api.login(initialU, initialP)
            isLoading = false
            if (result is LoginResult.Success) {
                onLoginSuccess(result.data, initialU, initialP)
            } else if (result is LoginResult.Error) {
                status = result.message
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.size(80.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Wifi, null, tint = Color.White, modifier = Modifier.size(44.dp))
            }
        }
        
        Spacer(Modifier.height(24.dp))
        Text("Bijoy Self Care", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
        Text("Manage your broadband connection seamlessly", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        
        Spacer(Modifier.height(48.dp))
        
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Customer ID") },
            leadingIcon = { Icon(Icons.Filled.Person, null) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Filled.Lock, null) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(Modifier.height(32.dp))
        
        Button(
            onClick = {
                isLoading = true
                scope.launch {
                    val result = api.login(username, password)
                    isLoading = false
                    if (result is LoginResult.Success) {
                        onLoginSuccess(result.data, username, password)
                    } else {
                        status = "Login Failed"
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("LOGIN", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        
        if (status.isNotEmpty()) {
            Text(status, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun PulsingDot(color: Color, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
fun DashboardScreen(appState: AppState, onRefresh: () -> Unit) {
    val data = appState.dashboardData ?: return

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            data.name.firstOrNull()?.toString()?.uppercase() ?: "U",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Hello,", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                        Text(data.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
                
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    if (appState.isDashboardRefreshing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        
        item {
            LiveSpeedCard(appState.api)
        }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Account Status", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        Text(data.accountStatus, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    val isOnline = data.connectionStatus.contains("ONLINE", true)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isOnline) Color(0xFF00C853).copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PulsingDot(color = if (isOnline) Color(0xFF00C853) else Color.Red)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            data.connectionStatus,
                            color = if (isOnline) Color(0xFF00C853) else Color.Red,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
        
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoCardCompact("Package Speed", data.packageInfo, Modifier.weight(1f))
                InfoCardCompact("Plan Rate", "${data.planRate} Tk", Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoCardCompact("Expiry Date", data.expiryDate, Modifier.weight(1f))
                InfoCardCompact("User ID", data.userId, Modifier.weight(1f))
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Personal Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Phone, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Mobile", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            Text(if (data.mobile.isEmpty()) "Not Provided" else data.mobile, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Email, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Email", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            Text(if (data.email.isEmpty()) "Not Provided" else data.email, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        }
                    }
                }
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Real-Time Bandwidth", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SpeedDisplay("Download", speed.download, Color(0xFF00C853))
                SpeedDisplay("Upload", speed.upload, Color(0xFF2196F3))
            }
            Spacer(Modifier.height(16.dp))
            RealTimeChart(history)
        }
    }
}

@Composable
fun SpeedDisplay(label: String, kbpsValue: Double, color: Color) {
    var value = kbpsValue
    var unit = "Kbps"
    
    if (value >= 1000.0) {
        value /= 1000.0
        unit = "Mbps"
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        }
        Text(String.format(Locale.US, "%.1f", value), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun RealTimeChart(history: List<LiveSpeed>) {
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        if (history.size < 2) return@Canvas
        
        // Horizontal grid lines
        val hGridLines = 3
        val hGridSpacing = size.height / hGridLines
        for (i in 0..hGridLines) {
            drawLine(
                color = Color.Gray.copy(alpha = 0.12f),
                start = Offset(0f, i * hGridSpacing),
                end = Offset(size.width, i * hGridSpacing),
                strokeWidth = 1.dp.toPx()
            )
        }

        val maxVal = history.maxOf { it.download.coerceAtLeast(it.upload) }.coerceAtLeast(100.0)
        val stepX = size.width / 49f
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
                val prevS = history[i - 1]
                val prevX = (i - 1) * stepX
                val prevDy = size.height - (prevS.download.toFloat() * scaleY)
                val prevUy = size.height - (prevS.upload.toFloat() * scaleY)

                val p0X = if (i > 1) (i - 2) * stepX else prevX
                val p0Dy = if (i > 1) size.height - (history[i - 2].download.toFloat() * scaleY) else prevDy
                val p0Uy = if (i > 1) size.height - (history[i - 2].upload.toFloat() * scaleY) else prevUy

                val p3X = if (i < history.size - 1) (i + 1) * stepX else x
                val p3Dy = if (i < history.size - 1) size.height - (history[i + 1].download.toFloat() * scaleY) else dy
                val p3Uy = if (i < history.size - 1) size.height - (history[i + 1].upload.toFloat() * scaleY) else uy

                val tension = 0.2f
                val cp1X = prevX + (x - p0X) * tension
                val cp1Dy = prevDy + (dy - p0Dy) * tension
                val cp1Uy = prevUy + (uy - p0Uy) * tension

                val cp2X = x - (p3X - prevX) * tension
                val cp2Dy = dy - (p3Dy - prevDy) * tension
                val cp2Uy = uy - (p3Uy - prevUy) * tension

                val cp1D = Float2(cp1X, cp1Dy)
                val cp2D = Float2(cp2X, cp2Dy)
                val cp1U = Float2(cp1X, cp1Uy)
                val cp2U = Float2(cp2X, cp2Uy)

                downPath.cubicTo(cp1D.x, cp1D.y, cp2D.x, cp2D.y, x, dy)
                upPath.cubicTo(cp1U.x, cp1U.y, cp2U.x, cp2U.y, x, uy)
            }
        }
        
        // Draw area fill
        val downFillPath = Path().apply {
            addPath(downPath)
            lineTo((history.size - 1) * stepX, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            downFillPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF00C853).copy(alpha = 0.2f), Color.Transparent)
            )
        )
        
        val upFillPath = Path().apply {
            addPath(upPath)
            lineTo((history.size - 1) * stepX, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            upFillPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF2196F3).copy(alpha = 0.2f), Color.Transparent)
            )
        )

        // Draw outline stroke
        drawPath(downPath, Color(0xFF00C853), style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        drawPath(upPath, Color(0xFF2196F3), style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun InfoCardCompact(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun UsageScreen(appState: AppState) {
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        appState.loadUsage()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Usage Reports", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            IconButton(
                onClick = { scope.launch { appState.loadUsage(force = true) } },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                if (appState.isUsageLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))

        if (appState.isUsageLoading && appState.usageData.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (appState.usageError.isNotEmpty() && appState.usageData.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(appState.usageError, color = MaterialTheme.colorScheme.error)
            }
        } else {
            // Stats summary
            val totalBytes = appState.usageData.sumOf { it.download + it.upload }
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Total Consumption (Active Period)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Text(formatBytes(totalBytes), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(appState.usageData) { item ->
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(item.date, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                Text("Total: ${formatBytes(item.download + item.upload)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            
                            Spacer(Modifier.height(8.dp))
                            
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Down: ${formatBytes(item.download)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF00C853))
                                Text("Up: ${formatBytes(item.upload)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2196F3))
                            }
                            
                            Spacer(Modifier.height(8.dp))
                            
                            // Visual ratio indicator
                            val downRatio = if (item.download + item.upload > 0) item.download.toFloat() / (item.download + item.upload) else 0.5f
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2196F3))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(downRatio)
                                        .background(Color(0xFF00C853))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TicketsScreen(appState: AppState) {
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        appState.loadTickets()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Complain Tickets", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            IconButton(
                onClick = { scope.launch { appState.loadTickets(force = true) } },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                if (appState.isTicketsLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))

        if (appState.isTicketsLoading && appState.tickets.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (appState.ticketsError.isNotEmpty() && appState.tickets.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(appState.ticketsError, color = MaterialTheme.colorScheme.error)
            }
        } else if (appState.tickets.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No complain tickets found", color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(appState.tickets) { item ->
                    TicketCardItem(item)
                }
            }
        }
    }
}

@Composable
fun TicketCardItem(item: TicketItem) {
    var expanded by remember { mutableStateOf(false) }
    
    val statusColor = when (item.status.uppercase()) {
        "NEW" -> Color(0xFFFF9100)
        "ASSIGNED" -> Color(0xFF2979FF)
        "CLOSED", "RESOLVED" -> Color(0xFF00C853)
        else -> Color.Gray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("#${item.id}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(item.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(item.status, color = statusColor, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                }
            }
            
            Spacer(Modifier.height(10.dp))
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            if (item.assignedTo.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Assigned To: ${item.assignedTo}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(Modifier.height(12.dp))
                    Text("Description:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(4.dp))
                    Text(item.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

fun extractTrxId(remarks: String): String? {
    val regex = Regex("""TrxID\s+([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
    val match = regex.find(remarks)
    return match?.groupValues?.getOrNull(1)
}

@Composable
fun PaymentScreen(appState: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        appState.loadPayment()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Billing History", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            IconButton(
                onClick = { scope.launch { appState.loadPayment(force = true) } },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                if (appState.isPaymentLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))

        if (appState.isPaymentLoading && appState.paymentHistory.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (appState.paymentError.isNotEmpty() && appState.paymentHistory.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(appState.paymentError, color = MaterialTheme.colorScheme.error)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(appState.paymentHistory) { item ->
                    val isNegative = item.amount.toDoubleOrNull()?.let { it < 0 } ?: item.amount.startsWith("-")
                    val cleanAmount = item.amount.replace("-", "").trim()
                    val trxId = extractTrxId(item.status) // status contains Remarks from site
                    val paymentMethod = if (item.status.contains("BKASH", true)) "bKash" else item.method

                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(item.date, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (isNegative) "- ৳ $cleanAmount" else "+ ৳ $cleanAmount",
                                    color = if (isNegative) MaterialTheme.colorScheme.error else Color(0xFF00C853),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp
                                )
                            }
                            
                            Spacer(Modifier.height(8.dp))
                            
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isNegative) MaterialTheme.colorScheme.error else Color(0xFF00C853))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (isNegative) "Charge / Bill" else "Recharge Received",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(paymentMethod, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            
                            Spacer(Modifier.height(6.dp))
                            Text(item.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            
                            if (trxId != null) {
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .clickable { copyToClipboard(context, trxId) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(6.dp))
                                    Text("TrxID: $trxId", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(8.dp))
                                    Text("(Tap to Copy)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun copyToClipboard(context: Context, text: String, label: String = "Transaction ID") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied!", Toast.LENGTH_SHORT).show()
}

@Composable
fun SettingsScreen(appState: AppState, onLogout: () -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("bijoy_prefs", Context.MODE_PRIVATE) }
    val data = appState.dashboardData

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("App Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("App Version", color = MaterialTheme.colorScheme.secondary)
                    Text("1.1.0", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Platform Status", color = MaterialTheme.colorScheme.secondary)
                    Text("Ready", color = Color(0xFF00C853), fontWeight = FontWeight.Bold)
                }
            }
        }

        if (data != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Connection Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Service Provider", color = MaterialTheme.colorScheme.secondary)
                        Text("Bijoy Broadband", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Customer ID", color = MaterialTheme.colorScheme.secondary)
                        Text(data.userId, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Package Details", color = MaterialTheme.colorScheme.secondary)
                        Text(data.packageInfo, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
        Spacer(Modifier.weight(1f))
        
        Button(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.ExitToApp, null)
            Spacer(Modifier.width(8.dp))
            Text("LOGOUT FROM ACCOUNT", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.US, "%.1f %cB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}