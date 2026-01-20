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
                return@withContext LoginResult.Error("Login failed: Invalid credentials")
            }

            val doc = Jsoup.parse(dashBody)
            
            val name = doc.select("h2.flex.items-center").firstOrNull()?.ownText()?.trim() ?: doc.select("h2").firstOrNull()?.text()?.trim() ?: "User"
            val pkg = doc.select("span:contains(Mbps)").firstOrNull()?.text()?.trim() ?: doc.select("p:contains(package)").firstOrNull()?.nextElementSibling()?.text()?.trim() ?: "N/A"
            
            val accStatus = doc.select("span:contains(Account Status)").firstOrNull()?.parent()?.select("p, font, span")?.lastOrNull()?.text()?.trim() ?: "N/A"
            
            // Connection Status search
            val connStatusLabel = doc.select("span:contains(Connection Status)").firstOrNull()
            val connStatus = connStatusLabel?.parent()?.select("span")?.filter { it.text().contains("ONLINE", true) || it.text().contains("OFFLINE", true) }?.firstOrNull()?.text()?.trim() 
                ?: connStatusLabel?.parent()?.select("p")?.firstOrNull()?.text()?.trim() ?: "N/A"
            
            val expiry = doc.select("span:contains(Expiry Date)").firstOrNull()?.nextElementSibling()?.text()?.trim() ?: "N/A"
            val rate = doc.select("span:contains(Plan rate)").firstOrNull()?.nextElementSibling()?.text()?.trim() ?: "N/A"

            return@withContext LoginResult.Success(
                DashboardData(name, pkg, accStatus, connStatus, expiry, rate)
            )
        } catch (e: Exception) {
            return@withContext LoginResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun getLiveSpeed(): LiveSpeed = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://selfcare.bijoy.net/du_graph_ajax?type=1")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://selfcare.bijoy.net/customer/dashboard")
                .build()
            
            // We use a dedicated call and read ONLY the currently available bytes
            val response = client.newCall(request).execute()
            val source = response.body?.source() ?: return@withContext LiveSpeed(0.0, 0.0)
            
            // Wait up to 100ms for some data if empty
            source.request(1)
            val data = source.buffer.clone().readUtf8()
            response.close() // Closing stops the stream for this call
            
            val regex = Regex("""(\d+\.?\d*),(\d+\.?\d*)""")
            val matches = regex.findAll(data).toList()
            if (matches.isNotEmpty()) {
                val last = matches.last()
                return@withContext LiveSpeed(
                    download = last.groupValues[1].toDouble() / 1000.0,
                    upload = last.groupValues[2].toDouble() / 1000.0
                )
            }
            return@withContext LiveSpeed(0.0, 0.0)
        } catch (e: Exception) {
            return@withContext LiveSpeed(0.0, 0.0)
        }
    }

    suspend fun getUsageGraph(): List<UsageData> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://selfcare.bijoy.net/customer/totalUsage")
                .header("X-Requested-With", "XMLHttpRequest")
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