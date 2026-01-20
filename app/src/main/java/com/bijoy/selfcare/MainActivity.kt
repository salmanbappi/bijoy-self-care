package com.bijoy.selfcare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.bijoy.selfcare.api.BijoyApi
import com.bijoy.selfcare.api.DashboardData
import com.bijoy.selfcare.api.LoginResult
import com.bijoy.selfcare.ui.theme.BijoySelfCareTheme
import kotlinx.coroutines.launch

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
        DashboardScreen(data = dashboardData!!) {
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
fun LoginScreen(onLoginSuccess: (DashboardData) -> Unit) {
    var username by remember { mutableStateOf("840135") }
    var password by remember { mutableStateOf("6666") }
    var status by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val api = remember { BijoyApi() }
    
    // Auto-login on first load
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
        Text("Bijoy Self Care Login", style = MaterialTheme.typography.headlineMedium)
        
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.padding(top = 16.dp)
        )
        
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.padding(top = 8.dp)
        )
        
        Button(
            onClick = {
                isLoading = true
                status = "Logging in..."
                scope.launch {
                    val result = api.login(username, password)
                    isLoading = false
                    when (result) {
                        is LoginResult.Success -> {
                            status = "Success!"
                            onLoginSuccess(result.data)
                        }
                        is LoginResult.Error -> {
                            status = result.message
                        }
                    }
                }
            },
            modifier = Modifier.padding(top = 16.dp),
            enabled = !isLoading
        ) {
            Text("Login")
        }
        
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
        }
        
        Text(
            text = status,
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
fun DashboardScreen(data: DashboardData, onLogout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Dashboard", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 24.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Welcome,", style = MaterialTheme.typography.labelLarge)
                Text(data.name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        
        InfoCard(label = "Current Package", value = data.packageInfo)
        InfoCard(label = "Account Status", value = data.accountStatus)
        InfoCard(label = "Connection Status", value = data.connectionStatus)
        
        Button(onClick = onLogout, modifier = Modifier.padding(top = 32.dp)) {
            Text("Logout")
        }
    }
}

@Composable
fun InfoCard(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}
