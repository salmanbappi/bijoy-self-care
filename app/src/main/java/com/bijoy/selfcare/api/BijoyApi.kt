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

data class DashboardData(
    val name: String,
    val packageInfo: String,
    val accountStatus: String,
    val connectionStatus: String,
    val expiryDate: String,
    val planRate: String,
    val balance: String = "N/A"
)

data class UsageData(
    val date: String,
    val download: Long,
    val upload: Long
)

data class LiveSpeed(
    val download: Double,
    val upload: Double,
    val unit: String = "Kbps"
)

data class PaymentHistoryItem(
    val date: String,
    val amount: String,
    val method: String,
    val status: String,
    val trxId: String
)

sealed class LoginResult {
    data class Success(val data: DashboardData) : LoginResult()
    data class Error(val message: String) : LoginResult()
}

class BijoyApi {
    private val cookieStore = HashMap<String, List<Cookie>>()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val validCookies = cookies.filter { !it.value.equals("deleteMe", ignoreCase = true) }
            val existingCookies = cookieStore[url.host]?.toMutableList() ?: ArrayList()
            for (cookie in validCookies) {
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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun login(username: String, pass: String): LoginResult = withContext(Dispatchers.IO) {
        try {
            val initRequest = Request.Builder().url("https://selfcare.bijoy.net/customer/").build()
            client.newCall(initRequest).execute().close()

            val formBody = FormBody.Builder().add("USERNAME", username).add("PASS", pass).build()
            val loginRequest = Request.Builder()
                .url("https://selfcare.bijoy.net/customer/login")
                .post(formBody)
                .header("Referer", "https://selfcare.bijoy.net/customer/")
                .header("Origin", "https://selfcare.bijoy.net")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            client.newCall(loginRequest).execute().close()

            val dashboardRequest = Request.Builder().url("https://selfcare.bijoy.net/customer/dashboard").build()
            val dashResponse = client.newCall(dashboardRequest).execute()
            val dashBody = dashResponse.body?.string() ?: ""
            val dashUrl = dashResponse.request.url.toString()
            dashResponse.close()

            if (dashUrl.contains("/login") || dashBody.contains("Sign in")) {
                return@withContext LoginResult.Error("Login failed")
            }

            // Initialize Speed Session
            val initSpeedRequest = Request.Builder()
                .url("https://selfcare.bijoy.net/du_graph_ajax?type=2")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()
            client.newCall(initSpeedRequest).execute().close()

            val doc = Jsoup.parse(dashBody)
            val name = doc.select("h2.flex.items-center").firstOrNull()?.ownText()?.trim() ?: "User"
            val pkg = doc.select("span:contains(Mbps)").firstOrNull()?.text()?.trim() ?: "N/A"
            
            // Refined Status Extractors
            val accStatus = doc.select("div:has(span:contains(Account Status)) p").text().trim()
            val connStatus = doc.select("div:has(span:contains(Connection Status)) p").text().trim().split("\n").first().trim()
            
            val expiry = doc.select("span:contains(Expiry Date)").firstOrNull()?.nextElementSibling()?.text()?.trim() ?: "N/A"
            val rate = doc.select("span:contains(Plan rate)").firstOrNull()?.nextElementSibling()?.text()?.trim() ?: "N/A"

            return@withContext LoginResult.Success(DashboardData(name, pkg, accStatus, connStatus, expiry, rate))
        } catch (e: Exception) {
            return@withContext LoginResult.Error(e.message ?: "Error")
        }
    }

    suspend fun getLiveSpeed(): LiveSpeed = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://selfcare.bijoy.net/du_graph_ajax?type=1")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://selfcare.bijoy.net/customer/dashboard")
                .build()
            
            val response = client.newCall(request).execute()
            val source = response.body?.source() ?: return@withContext LiveSpeed(0.0, 0.0)
            
            // Peek at the first 4KB of the stream
            if (!source.request(1)) {
                response.close()
                return@withContext LiveSpeed(0.0, 0.0)
            }
            
            val data = source.buffer.clone().readUtf8()
            response.close()
            
            // Format is concatenated "RX,TXRX,TX..."
            // e.g. "1234.0,5678.0900.0,100.0"
            // We want the absolute last pair.
            val regex = Regex("""(\d+\.?\d*),(\d+\.?\d*)""")
            val matches = regex.findAll(data).toList()
            if (matches.isNotEmpty()) {
                val last = matches.last()
                val rx = last.groupValues[1].toDouble()
                val tx = last.groupValues[2].toDouble()
                // Convert to Kbps (assuming bits per second)
                return@withContext LiveSpeed(rx / 1000.0, tx / 1000.0)
            }
            return@withContext LiveSpeed(0.0, 0.0)
        } catch (e: Exception) {
            return@withContext LiveSpeed(0.0, 0.0)
        }
    }

    suspend fun getUsageGraph(): List<UsageData> = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(Request.Builder().url("https://selfcare.bijoy.net/customer/totalUsage").header("X-Requested-With", "XMLHttpRequest").build()).execute()
            val jsonObject = JSONObject(response.body?.string() ?: "")
            response.close()
            val valuesArray = jsonObject.getJSONArray("value")
            val usageList = ArrayList<UsageData>()
            for (i in 0 until valuesArray.length()) {
                val innerObj = JSONObject(valuesArray.getString(i))
                usageList.add(UsageData(innerObj.getString("date"), innerObj.getLong("download"), innerObj.getLong("upload")))
            }
            usageList
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getPaymentHistory(): List<PaymentHistoryItem> = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(Request.Builder().url("https://selfcare.bijoy.net/customer/customerhistory").build()).execute()
            val doc = Jsoup.parse(response.body?.string() ?: "")
            response.close()
            doc.select("table tbody tr").map { row ->
                val cols = row.select("td")
                PaymentHistoryItem(cols[0].text(), cols[1].text(), cols[2].text(), cols[3].text(), if(cols.size > 4) cols[4].text() else "")
            }
        } catch (e: Exception) { emptyList() }
    }
}