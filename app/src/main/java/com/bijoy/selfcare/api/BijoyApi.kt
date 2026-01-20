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
    val upload: Double
)

data class PaymentHistoryItem(
    val date: String,
    val amount: String,
    val method: String,
    val status: String,
    val trxId: String
)

data class TicketItem(
    val id: String,
    val subject: String,
    val status: String,
    val date: String
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
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE })
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
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

            val doc = Jsoup.parse(dashBody)
            val name = doc.select("h2.flex.items-center").firstOrNull()?.ownText()?.trim() ?: "User"
            
            // Package: Look for "Mbps" text
            val pkg = doc.select("span:contains(Mbps)").firstOrNull()?.text()?.trim() ?: "Unknown"
            
            // Cards extraction
            val accStatus = doc.select("span:contains(Account Status)").firstOrNull()?.nextElementSibling()?.text()?.trim() ?: "N/A"
            val connStatus = doc.select("span:contains(Connection Status)").firstOrNull()?.nextElementSibling()?.text()?.trim() ?: "N/A"
            val expiry = doc.select("span:contains(Expiry Date)").firstOrNull()?.nextElementSibling()?.text()?.trim() ?: "N/A"
            val rate = doc.select("span:contains(Plan rate)").firstOrNull()?.nextElementSibling()?.text()?.trim() ?: "N/A"

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
            return@withContext LoginResult.Error(e.message ?: "Error")
        }
    }

    suspend fun getLiveSpeed(): LiveSpeed = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://selfcare.bijoy.net/du_graph_ajax?type=1")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            response.close()
            
            // Body format: "RX,TXRX,TX..."
            // Last one is most recent. Let's split by comma and handle the adjacent TXRX issue.
            // Actually, the format seems to be comma separated pairs but concatenated.
            // Example: 19784.0,38704.01264.0,3568.0
            // This is hard to parse reliably without a regex.
            val regex = Regex("""(\d+\.?\d*),(\d+\.?\d*)""")
            val matches = regex.findAll(body).toList()
            if (matches.isNotEmpty()) {
                val last = matches.last()
                return@withContext LiveSpeed(
                    download = last.groupValues[1].toDouble(),
                    upload = last.groupValues[2].toDouble()
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
            val jsonString = response.body?.string() ?: ""
            response.close()
            val jsonObject = JSONObject(jsonString)
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
