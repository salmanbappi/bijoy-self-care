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
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                    modifier = Modifier.height(64.dp)
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
                        val selected = currentRoute == route
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp)) },
                            label = { 
                                Text(
                                    text = if (selected) "[ ${label.uppercase()} ]" else label.uppercase(),
                                    style = MaterialTheme.typography.labelSmall
                                ) 
                            },
                            selected = selected,
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
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
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
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(250, easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f))) + fadeIn(animationSpec = tween(200))
                } else {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(250, easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f))) + fadeIn(animationSpec = tween(200))
                }
            },
            exitTransition = {
                val initialIndex = getTabRouteIndex(initialState.destination.route)
                val targetIndex = getTabRouteIndex(targetState.destination.route)
                if (targetIndex > initialIndex) {
                    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(250, easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f))) + fadeOut(animationSpec = tween(200))
                } else {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(250, easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f))) + fadeOut(animationSpec = tween(200))
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
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Wifi, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.primary, 
                modifier = Modifier.size(36.dp)
            )
        }
        
        Spacer(Modifier.height(32.dp))
        Text(
            text = "bijoy self care", 
            style = MaterialTheme.typography.headlineLarge, 
            fontWeight = FontWeight.Bold, 
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Manage your broadband connection seamlessly", 
            style = MaterialTheme.typography.bodySmall, 
            color = MaterialTheme.colorScheme.secondary
        )
        
        Spacer(Modifier.height(48.dp))
        
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("CUSTOMER ID", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                placeholder = { Text("Enter ID", color = MaterialTheme.colorScheme.secondary) },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.labelLarge
            )
        }
        
        Spacer(Modifier.height(24.dp))
        
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("PASSWORD", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text("Enter Password", color = MaterialTheme.colorScheme.secondary) },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.labelLarge
            )
        }
        
        Spacer(Modifier.height(40.dp))
        
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
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            if (isLoading) {
                Text("[LOADING...]", style = MaterialTheme.typography.labelLarge)
            } else {
                Text("LOGIN", style = MaterialTheme.typography.labelLarge)
            }
        }
        
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "[ERROR: ${status.uppercase()}]", 
                color = MaterialTheme.colorScheme.error, 
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
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
            .size(8.dp)
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
                            .size(48.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = data.name.firstOrNull()?.toString()?.uppercase() ?: "U",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("HELLO,", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        Text(data.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                }
                
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                ) {
                    if (appState.isDashboardRefreshing) {
                        Text("[...]", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Filled.Refresh, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("ACCOUNT STATUS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(4.dp))
                        Text(data.accountStatus, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                    val isOnline = data.connectionStatus.contains("ONLINE", true)
                    val statusColor = if (isOnline) Color(0xFF4A9E5C) else Color(0xFFD71921)
                    Row(
                        modifier = Modifier
                            .border(1.dp, statusColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PulsingDot(color = statusColor)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = data.connectionStatus.uppercase(),
                            color = statusColor,
                            style = MaterialTheme.typography.labelSmall
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
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("PERSONAL INFORMATION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(16.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Phone, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("MOBILE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            Text(
                                text = if (data.mobile.isEmpty()) "Not Provided" else data.mobile, 
                                style = MaterialTheme.typography.bodyMedium, 
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Email, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("EMAIL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            Text(
                                text = if (data.email.isEmpty()) "Not Provided" else data.email, 
                                style = MaterialTheme.typography.bodyMedium, 
                                color = MaterialTheme.colorScheme.primary
                            )
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("REAL-TIME BANDWIDTH", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SpeedDisplay("Download", speed.download, Color(0xFF4A9E5C))
                SpeedDisplay("Upload", speed.upload, MaterialTheme.colorScheme.secondary)
            }
            Spacer(Modifier.height(20.dp))
            RealTimeChart(history)
        }
    }
}

@Composable
fun SpeedDisplay(label: String, kbpsValue: Double, color: Color) {
    var value = kbpsValue
    var unit = "KBPS"
    
    if (value >= 1000.0) {
        value /= 1000.0
        unit = "MBPS"
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = String.format(Locale.US, "%.1f", value), 
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = unit, 
                style = MaterialTheme.typography.labelSmall, 
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
    }
}

@Composable
fun RealTimeChart(history: List<LiveSpeed>) {
    val outlineColor = MaterialTheme.colorScheme.outline
    val downloadColor = Color(0xFF4A9E5C)
    val uploadColor = MaterialTheme.colorScheme.secondary
    
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        if (history.size < 2) return@Canvas
        
        // Horizontal grid lines
        val hGridLines = 3
        val hGridSpacing = size.height / hGridLines
        for (i in 0..hGridLines) {
            drawLine(
                color = outlineColor,
                start = Offset(0f, i * hGridSpacing),
                end = Offset(size.width, i * hGridSpacing),
                strokeWidth = 0.5.dp.toPx()
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
        
        // Draw outline stroke (No area fill, clean solid/dashed lines)
        drawPath(
            path = downPath, 
            color = downloadColor, 
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        
        drawPath(
            path = upPath, 
            color = uploadColor, 
            style = Stroke(
                width = 2.dp.toPx(), 
                cap = StrokeCap.Round,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )
        )
    }
}

@Composable
fun InfoCardCompact(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
            Text("usage reports", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            IconButton(
                onClick = { scope.launch { appState.loadUsage(force = true) } },
                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            ) {
                if (appState.isUsageLoading) {
                    Text("[...]", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Filled.Refresh, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))

        if (appState.isUsageLoading && appState.usageData.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("[LOADING...]", style = MaterialTheme.typography.labelLarge)
            }
        } else if (appState.usageError.isNotEmpty() && appState.usageData.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("[ERROR: ${appState.usageError.uppercase()}]", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
        } else {
            // Stats summary
            val totalBytes = appState.usageData.sumOf { it.download + it.upload }
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("TOTAL CONSUMPTION (ACTIVE PERIOD)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(8.dp))
                    Text(formatBytes(totalBytes), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(appState.usageData) { item ->
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(item.date.uppercase(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("TOTAL: ${formatBytes(item.download + item.upload)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            
                            Spacer(Modifier.height(12.dp))
                            
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("DOWN: ${formatBytes(item.download)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                Text("UP: ${formatBytes(item.upload)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            }
                            
                            Spacer(Modifier.height(10.dp))
                            
                            // Visual ratio indicator (monochrome progress bar)
                            val downRatio = if (item.download + item.upload > 0) item.download.toFloat() / (item.download + item.upload) else 0.5f
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(downRatio)
                                        .background(MaterialTheme.colorScheme.primary)
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
            Text("complain tickets", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            IconButton(
                onClick = { scope.launch { appState.loadTickets(force = true) } },
                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            ) {
                if (appState.isTicketsLoading) {
                    Text("[...]", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Filled.Refresh, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))

        if (appState.isTicketsLoading && appState.tickets.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("[LOADING...]", style = MaterialTheme.typography.labelLarge)
            }
        } else if (appState.ticketsError.isNotEmpty() && appState.tickets.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("[ERROR: ${appState.ticketsError.uppercase()}]", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
        } else if (appState.tickets.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("NO COMPLAIN TICKETS FOUND", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
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
        "NEW" -> Color(0xFFD4A843) // Warning amber
        "ASSIGNED" -> Color(0xFF5B9BF6) // Interactive blue
        "CLOSED", "RESOLVED" -> Color(0xFF4A9E5C) // Success green
        else -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("#${item.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(item.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
                
                Box(
                    modifier = Modifier
                        .border(1.dp, statusColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(item.status.uppercase(), color = statusColor, style = MaterialTheme.typography.labelSmall)
                }
            }
            
            Spacer(Modifier.height(12.dp))
            Text(item.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            
            if (item.assignedTo.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("ASSIGNED TO: ${item.assignedTo.uppercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    Text("DESCRIPTION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(6.dp))
                    Text(item.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            
            Spacer(Modifier.height(8.dp))
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
            Text("billing history", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            IconButton(
                onClick = { scope.launch { appState.loadPayment(force = true) } },
                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            ) {
                if (appState.isPaymentLoading) {
                    Text("[...]", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Filled.Refresh, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))

        if (appState.isPaymentLoading && appState.paymentHistory.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("[LOADING...]", style = MaterialTheme.typography.labelLarge)
            }
        } else if (appState.paymentError.isNotEmpty() && appState.paymentHistory.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("[ERROR: ${appState.paymentError.uppercase()}]", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(appState.paymentHistory) { item ->
                    val isNegative = item.amount.toDoubleOrNull()?.let { it < 0 } ?: item.amount.startsWith("-")
                    val cleanAmount = item.amount.replace("-", "").trim()
                    val trxId = extractTrxId(item.status)
                    val paymentMethod = if (item.status.contains("BKASH", true)) "BKASH" else item.method.uppercase()

                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(item.date, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = if (isNegative) "- ৳ $cleanAmount" else "+ ৳ $cleanAmount",
                                    color = if (isNegative) MaterialTheme.colorScheme.error else Color(0xFF4A9E5C),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontSize = 16.sp
                                )
                            }
                            
                            Spacer(Modifier.height(8.dp))
                            
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (isNegative) MaterialTheme.colorScheme.error else Color(0xFF4A9E5C))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (isNegative) "CHARGE / BILL" else "RECHARGE RECEIVED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = paymentMethod, 
                                        style = MaterialTheme.typography.labelSmall, 
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            
                            Spacer(Modifier.height(6.dp))
                            Text(item.status.uppercase(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            
                            if (trxId != null) {
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                        .clickable { copyToClipboard(context, trxId) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(6.dp))
                                    Text("TRXID: $trxId", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text("(TAP TO COPY)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
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
    val data = appState.dashboardData

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("APP INFORMATION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(16.dp))
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("APP VERSION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    Text("1.2.0", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("PLATFORM STATUS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    Text("READY", color = Color(0xFF4A9E5C), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (data != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("CONNECTION DETAILS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(16.dp))
                    
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("SERVICE PROVIDER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        Text("BIJOY BROADBAND", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("CUSTOMER ID", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        Text(data.userId, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("PACKAGE DETAILS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        Text(data.packageInfo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
        Spacer(Modifier.weight(1f))
        
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Filled.ExitToApp, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("LOGOUT FROM ACCOUNT", style = MaterialTheme.typography.labelLarge)
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.US, "%.1f %cB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}