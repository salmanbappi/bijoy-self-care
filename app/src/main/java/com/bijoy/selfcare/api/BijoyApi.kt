package com.bijoy.selfcare.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

data class DashboardData(
    val name: String,
    val packageInfo: String,
    val accountStatus: String,
    val connectionStatus: String,
    val expiryDate: String,
    val planRate: String
)

data class UsageData(
    val date: String,
    val download: Long,
    val upload: Long
)

data class LiveSpeed(
    val download: Double,
    val upload: Double
)

data class PaymentHistoryItem(
    val date: String,
    val amount: String,
    val method: String,
    val status: String
)

sealed class LoginResult {
    data class Success(val data: DashboardData) : LoginResult()
    data class Error(val message: String) : LoginResult()
}

class BijoyApi {
    private val cookieStore = HashMap<String, List<Cookie>>()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val existingCookies = cookieStore[url.host]?.toMutableList() ?: ArrayList()
            for (cookie in cookies) {
                if (cookie.value.equals("deleteMe", true)) continue
                val index = existingCookies.indexOfFirst { it.name == cookie.name }
                if (index != -1) {
                    existingCookies[index] = cookie
                } else {
                    existingCookies.add(cookie)
                }
            }
            cookieStore[url.host] = existingCookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: ArrayList()
        }
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    suspend fun login(username: String, pass: String): LoginResult = withContext(Dispatchers.IO) {
        try {
            // 1. Initial GET to set cookies
            val initRequest = Request.Builder()
                .url("https://selfcare.bijoy.net/customer/")
                .header("User-Agent", userAgent)
                .build()
            client.newCall(initRequest).execute().close()

            // 2. POST Login with all required headers
            val formBody = FormBody.Builder()
                .add("USERNAME", username)
                .add("PASS", pass)
                .build()

            val loginRequest = Request.Builder()
                .url("https://selfcare.bijoy.net/customer/login")
                .post(formBody)
                .header("Referer", "https://selfcare.bijoy.net/customer/")
                .header("Origin", "https://selfcare.bijoy.net")
                .header("User-Agent", userAgent)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            client.newCall(loginRequest).execute().close()

            // 3. Get Dashboard and verify session
            val dashboardRequest = Request.Builder()
                .url("https://selfcare.bijoy.net/customer/dashboard")
                .header("User-Agent", userAgent)
                .build()
                
            val dashResponse = client.newCall(dashboardRequest).execute()
            val dashBody = dashResponse.body?.string() ?: ""
            val dashUrl = dashResponse.request.url.toString()
            dashResponse.close()

            if (dashUrl.contains("/login") || dashBody.contains("Sign in") || dashBody.contains("customerData")) {
                return@withContext LoginResult.Error("Login failed: Invalid credentials or session rejected.")
            }

            // 4. Initialize Speed Session (Required by server)
            val initSpeedRequest = Request.Builder()
                .url("https://selfcare.bijoy.net/du_graph_ajax?type=2")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("User-Agent", userAgent)
                .build()
            client.newCall(initSpeedRequest).execute().close()

            // 5. Parse Dashboard Data
            val doc = Jsoup.parse(dashBody)
            
            // Name: Using exact ID or very specific path
            // The name is in the sidebar user info section
            val name = doc.select("h2.flex.items-center").firstOrNull()?.ownText()?.trim() ?: "User"
            
            // Package: Inside span with specific color class or containing Mbps
            val pkg = doc.select("span:contains(Mbps)").firstOrNull()?.text()?.trim() ?: "N/A"
            
            // Stats: Targeting the cards in the main grid
            fun extractCardValue(label: String): String {
                val labelSpan = doc.select("span:contains($label)").firstOrNull()
                return labelSpan?.parent()?.select("p")?.firstOrNull()?.text()?.trim() ?: "N/A"
            }

            val accStatus = extractCardValue("Account Status")
            
            // Connection Status usually has a span inside the p
            val connStatusLabel = doc.select("span:contains(Connection Status)").firstOrNull()
            val connStatus = connStatusLabel?.parent()?.select("p span")?.firstOrNull()?.text()?.trim() 
                ?: connStatusLabel?.parent()?.select("p")?.firstOrNull()?.text()?.trim() ?: "N/A"
            
            val expiry = extractCardValue("Expiry Date")
            val rate = extractCardValue("Plan rate")

            return@withContext LoginResult.Success(
                DashboardData(
                    name = name,
                    packageInfo = pkg,
                    accountStatus = accStatus,
                    connectionStatus = connStatus,
                    expiryDate = expiry,
                    planRate = rate
                )
            )

        } catch (e: Exception) {
            Log.e("BijoyApi", "Login Error", e)
            return@withContext LoginResult.Error(e.message ?: "Network error occurred.")
        }
    }

    fun getSpeedFlow(): Flow<LiveSpeed> = flow {
        val speedClient = client.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS) // Keep connection open
            .build()
            
        val regex = Regex("""(\d+\.?\d*),(\d+\.?\d*)""")
        
        while (true) {
            try {
                val request = Request.Builder()
                    .url("https://selfcare.bijoy.net/du_graph_ajax?type=1")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", "https://selfcare.bijoy.net/customer/report")
                    .header("User-Agent", userAgent)
                    .build()

                speedClient.newCall(request).execute().use { response ->
                    val source = response.body?.source() ?: return@use
                    var lastData = ""
                    
                    // The server sends a continuous stream of "RX,TX" pairs
                    while (!source.exhausted()) {
                        // Read what's currently available in the buffer
                        val currentBuffer = source.buffer.clone().readUtf8()
                        
                        // We only care about the very last complete pair
                        val matches = regex.findAll(currentBuffer).toList()
                        if (matches.isNotEmpty()) {
                            val last = matches.last()
                            val rx = last.groupValues[1].toDouble()
                            val tx = last.groupValues[2].toDouble()
                            
                            // Emit speed in Kbps (assuming raw values are bps)
                            emit(LiveSpeed(rx / 1000.0, tx / 1000.0))
                        }
                        
                        delay(2000) // Poll the buffer every 2 seconds
                        source.request(1) // Block until more data is available
                    }
                }
            } catch (e: Exception) {
                Log.e("BijoyApi", "Speed Stream Error", e)
                delay(5000) // Retry after 5 seconds
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getUsageGraph(): List<UsageData> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://selfcare.bijoy.net/customer/totalUsage")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("User-Agent", userAgent)
                .build()
            val response = client.newCall(request).execute()
            val jsonObject = JSONObject(response.body?.string() ?: "")
            response.close()
            val valuesArray = jsonObject.getJSONArray("value")
            val usageList = ArrayList<UsageData>()
            for (i in 0 until valuesArray.length()) {
                val innerObj = JSONObject(valuesArray.getString(i))
                usageList.add(UsageData(innerObj.getString("date"), innerObj.getLong("download"), innerObj.getLong("upload")))
            }
            return@withContext usageList
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getPaymentHistory(): List<PaymentHistoryItem> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://selfcare.bijoy.net/customer/customerhistory")
                .header("User-Agent", userAgent)
                .build()
            val response = client.newCall(request).execute()
            val doc = Jsoup.parse(response.body?.string() ?: "")
            response.close()
            return@withContext doc.select("table tbody tr").map { row ->
                val cols = row.select("td")
                PaymentHistoryItem(cols[0].text(), cols[1].text(), cols[2].text(), cols[3].text())
            }
        } catch (e: Exception) { emptyList() }
    }
}
