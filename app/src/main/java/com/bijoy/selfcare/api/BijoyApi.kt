package com.bijoy.selfcare.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.jsoup.Jsoup
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun login(username: String, pass: String): LoginResult = withContext(Dispatchers.IO) {
        try {
            // 1. Visit main page
            client.newCall(Request.Builder().url("https://selfcare.bijoy.net/customer/").build()).execute().close()

            // 2. Perform Login
            val form = FormBody.Builder().add("USERNAME", username).add("PASS", pass).build()
            val loginReq = Request.Builder()
                .url("https://selfcare.bijoy.net/customer/login")
                .post(form)
                .header("Referer", "https://selfcare.bijoy.net/customer/")
                .build()
            client.newCall(loginReq).execute().close()

            // 3. Get Dashboard
            val dashReq = Request.Builder().url("https://selfcare.bijoy.net/customer/dashboard").build()
            val response = client.newCall(dashReq).execute()
            val body = response.body?.string() ?: ""
            val url = response.request.url.toString()
            response.close()

            if (url.contains("/login") || body.contains("Sign in")) return@withContext LoginResult.Error("Invalid Credentials")

            val doc = Jsoup.parse(body)
            
            // Name: Specifically from the h2 in the user info section
            val name = doc.select("h2.flex.items-center").firstOrNull()?.ownText()?.trim() ?: "User"
            
            // Package: Inside the span with Mbps
            val pkg = doc.select("span:contains(Mbps)").firstOrNull()?.text()?.trim() ?: "N/A"
            
            // Extract using specific labels
            fun getVal(label: String): String {
                return doc.select("span:contains($label)").firstOrNull()?.parent()?.select("p")?.firstOrNull()?.text()?.trim() ?: "N/A"
            }

            val accStatusRaw = getVal("Account Status")
            val accStatus = if (accStatusRaw.contains("Active", true)) "Active" else accStatusRaw
            
            val connStatusRaw = doc.select("span:contains(Connection Status)").firstOrNull()?.parent()?.select("p")?.firstOrNull()?.text()?.trim() ?: "N/A"
            val connStatus = if (connStatusRaw.contains("ONLINE", true)) "ONLINE" else if (connStatusRaw.contains("OFFLINE", true)) "OFFLINE" else connStatusRaw
            
            val expiry = getVal("Expiry Date")
            val rate = getVal("Plan rate")

            return@withContext LoginResult.Success(DashboardData(name, pkg, accStatus, connStatus, expiry, rate))
        } catch (e: Exception) {
            return@withContext LoginResult.Error(e.message ?: "Network Error")
        }
    }

    fun getSpeedFlow(): Flow<LiveSpeed> = flow {
        // Initialize speed session
        try {
            client.newCall(Request.Builder().url("https://selfcare.bijoy.net/du_graph_ajax?type=2").build()).execute().close()
        } catch (e: Exception) {}

        val speedClient = client.newBuilder().readTimeout(0, TimeUnit.SECONDS).build()
        val regex = Regex("""(\d+\.?\d*),(\d+\.?\d*)""")
        
        while (true) {
            try {
                val request = Request.Builder()
                    .url("https://selfcare.bijoy.net/du_graph_ajax?type=1")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", "https://selfcare.bijoy.net/customer/report")
                    .build()

                speedClient.newCall(request).execute().use { response ->
                    val source = response.body?.source() ?: return@use
                    
                    // We read chunks from the stream
                    while (!source.exhausted()) {
                        // Read available data from the stream
                        val fullBuffer = source.buffer.readUtf8()
                        if (fullBuffer.isNotEmpty()) {
                            val matches = regex.findAll(fullBuffer).toList()
                            if (matches.isNotEmpty()) {
                                val last = matches.last()
                                emit(LiveSpeed(
                                    download = last.groupValues[1].toDouble() / 1000.0,
                                    upload = last.groupValues[2].toDouble() / 1000.0
                                ))
                            }
                        }
                        delay(2000)
                        source.request(1) // Wait for data
                    }
                }
            } catch (e: Exception) {
                Log.e("BijoyApi", "Stream error, retrying...", e)
                delay(5000)
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getUsageGraph(): List<UsageData> = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(Request.Builder().url("https://selfcare.bijoy.net/customer/totalUsage").header("X-Requested-With", "XMLHttpRequest").build()).execute()
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
            val response = client.newCall(Request.Builder().url("https://selfcare.bijoy.net/customer/customerhistory").build()).execute()
            val doc = Jsoup.parse(response.body?.string() ?: "")
            response.close()
            doc.select("table tbody tr").map { row ->
                val cols = row.select("td")
                PaymentHistoryItem(cols[0].text(), cols[1].text(), cols[2].text(), cols[3].text())
            }
        } catch (e: Exception) { emptyList() }
    }
}