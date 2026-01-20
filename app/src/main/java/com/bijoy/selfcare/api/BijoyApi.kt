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
                if (index != -1) existingCookies[index] = cookie else existingCookies.add(cookie)
            }
            cookieStore[url.host] = existingCookies
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> = cookieStore[url.host] ?: ArrayList()
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
            client.newCall(Request.Builder().url("https://selfcare.bijoy.net/customer/").header("User-Agent", userAgent).build()).execute().close()

            val formBody = FormBody.Builder().add("USERNAME", username).add("PASS", pass).build()
            val loginRequest = Request.Builder()
                .url("https://selfcare.bijoy.net/customer/login")
                .post(formBody)
                .header("Referer", "https://selfcare.bijoy.net/customer/")
                .header("Origin", "https://selfcare.bijoy.net")
                .header("User-Agent", userAgent)
                .build()
            client.newCall(loginRequest).execute().close()

            val dashResponse = client.newCall(Request.Builder().url("https://selfcare.bijoy.net/customer/dashboard").header("User-Agent", userAgent).build()).execute()
            val dashBody = dashResponse.body?.string() ?: ""
            val dashUrl = dashResponse.request.url.toString()
            dashResponse.close()

            if (dashUrl.contains("/login") || dashBody.contains("Sign in")) {
                return@withContext LoginResult.Error("Login failed")
            }

            // Initialize graph session
            client.newCall(Request.Builder().url("https://selfcare.bijoy.net/customer/report").header("User-Agent", userAgent).build()).execute().close()
            client.newCall(Request.Builder().url("https://selfcare.bijoy.net/du_graph_ajax?type=2").header("User-Agent", userAgent).header("X-Requested-With", "XMLHttpRequest").build()).execute().close()

            val doc = Jsoup.parse(dashBody)
            
            // Precise extraction
            val name = doc.select("aside h2.flex.items-center").firstOrNull()?.ownText()?.trim() ?: "User"
            val pkg = doc.select("h1 span:contains(Mbps)").firstOrNull()?.text()?.trim() ?: "N/A"
            
            fun getCardValue(labelText: String): String {
                val label = doc.select("span:contains($labelText)").firstOrNull() ?: return "N/A"
                val p = label.parent()?.select("p")?.firstOrNull() ?: return "N/A"
                // Clean text from internal spans/fonts
                return p.text().trim()
            }

            val accStatus = getCardValue("Account Status")
            val connStatusRaw = getCardValue("Connection Status")
            val connStatus = if (connStatusRaw.contains("ONLINE", true)) "ONLINE" else if (connStatusRaw.contains("OFFLINE", true)) "OFFLINE" else connStatusRaw
            
            val expiry = getCardValue("Expiry Date")
            val rate = getCardValue("Plan rate")

            return@withContext LoginResult.Success(DashboardData(name, pkg, accStatus, connStatus, expiry, rate))
        } catch (e: Exception) {
            return@withContext LoginResult.Error(e.message ?: "Error")
        }
    }

    fun getSpeedFlow(): Flow<LiveSpeed> = flow {
        val speedClient = client.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)
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
                    
                    // The server keeps adding to the same stream
                    // We must read only what's new.
                    while (!source.exhausted()) {
                        // Wait for data to arrive in the buffer
                        if (source.buffer.size == 0L) {
                            source.request(1)
                        }
                        
                        // Read the entire current buffer content
                        val data = source.buffer.readUtf8()
                        
                        // Parse all pairs in this chunk and emit the last one
                        val matches = regex.findAll(data).toList()
                        if (matches.isNotEmpty()) {
                            val last = matches.last()
                            val rx = last.groupValues[1].toDouble()
                            val tx = last.groupValues[2].toDouble()
                            emit(LiveSpeed(rx / 1000.0, tx / 1000.0))
                        }
                        
                        delay(1500) // Match the website's visual refresh rate
                    }
                }
            } catch (e: Exception) {
                Log.e("BijoyApi", "Speed error", e)
                delay(3000)
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getUsageGraph(): List<UsageData> = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(Request.Builder().url("https://selfcare.bijoy.net/customer/totalUsage").header("X-Requested-With", "XMLHttpRequest").header("User-Agent", userAgent).build()).execute()
            val json = JSONObject(response.body?.string() ?: "")
            response.close()
            val array = json.getJSONArray("value")
            (0 until array.length()).map { i ->
                val obj = JSONObject(array.getString(i))
                UsageData(obj.getString("date"), obj.getLong("download"), obj.getLong("upload"))
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getPaymentHistory(): List<PaymentHistoryItem> = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(Request.Builder().url("https://selfcare.bijoy.net/customer/customerhistory").header("User-Agent", userAgent).build()).execute()
            val doc = Jsoup.parse(response.body?.string() ?: "")
            response.close()
            doc.select("table tbody tr").map { row ->
                val cols = row.select("td")
                PaymentHistoryItem(cols[0].text(), cols[1].text(), cols[2].text(), cols[3].text())
            }
        } catch (e: Exception) { emptyList() }
    }
}