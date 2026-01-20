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
import java.util.concurrent.TimeUnit

data class DashboardData(
    val name: String,
    val packageInfo: String,
    val accountStatus: String,
    val connectionStatus: String,
    val balance: String = "N/A"
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
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun login(username: String, pass: String): LoginResult = withContext(Dispatchers.IO) {
        try {
            // 1. Initial GET to set cookies
            val initRequest = Request.Builder()
                .url("https://selfcare.bijoy.net/customer/")
                .build()
            
            client.newCall(initRequest).execute().close()

            // 2. POST Login
            val formBody = FormBody.Builder()
                .add("USERNAME", username)
                .add("PASS", pass)
                .build()

            val loginRequest = Request.Builder()
                .url("https://selfcare.bijoy.net/customer/login")
                .post(formBody)
                .header("Referer", "https://selfcare.bijoy.net/customer/")
                .header("Origin", "https://selfcare.bijoy.net")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            val response = client.newCall(loginRequest).execute()
            response.close()

            // 3. Check Dashboard
            val dashboardRequest = Request.Builder()
                .url("https://selfcare.bijoy.net/customer/dashboard")
                .header("Referer", "https://selfcare.bijoy.net/customer/login")
                .build()
                
            val dashResponse = client.newCall(dashboardRequest).execute()
            val dashBody = dashResponse.body?.string() ?: ""
            val dashUrl = dashResponse.request.url.toString()
            dashResponse.close()

            // If redirected back to login or root, it failed
            if (dashUrl.contains("/customer/login") || dashUrl.endsWith("/customer/") || dashBody.contains("Sign in")) {
                return@withContext LoginResult.Error("Login failed: Invalid credentials or session expired.")
            }

            // 4. Parse Dashboard Data
            val doc = Jsoup.parse(dashBody)
            
            // Name: Bappy Shikder (inside h2 text)
            // It might be complex to extract exactly because of the <i class="fa... status-indicator"> inside the h2
            // We can take the text node of h2
            val name = doc.select("h2.flex.items-center").firstOrNull()?.ownText()?.trim() ?: "User"
            
            // Package: Inside span with bg-[#7674F8]
            val pkg = doc.select("span.bg-\\[\\#7674F8\\]").text().trim()
            
            // Account Status: p under "Account Status" span
            // This is a bit tricky with tailwind classes, let's look for the label
            val accStatusLabel = doc.select("span:contains(Account Status)").first()
            val accStatus = accStatusLabel?.nextElementSibling()?.text()?.trim() ?: "Unknown"
            
            // Connection Status: p under "Connection Status" span
            val connStatusLabel = doc.select("span:contains(Connection Status)").first()
            val connStatus = connStatusLabel?.nextElementSibling()?.text()?.trim() ?: "Unknown"

            return@withContext LoginResult.Success(
                DashboardData(
                    name = name,
                    packageInfo = pkg,
                    accountStatus = accStatus,
                    connectionStatus = connStatus
                )
            )

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext LoginResult.Error(e.message ?: "Unknown error")
        }
    }
}
