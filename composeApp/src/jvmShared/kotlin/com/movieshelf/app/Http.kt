package com.movieshelf.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Shared HTTP for the Android and desktop apps: JSON in/out, timeouts, [HttpStatusException] on non-2xx. */
internal suspend fun httpJson(url: String, bearerToken: String = "", postBody: String? = null): String = withContext(Dispatchers.IO) {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 15_000
        requestMethod = if (postBody != null) "POST" else "GET"
        if (bearerToken.isNotBlank()) setRequestProperty("Authorization", "Bearer $bearerToken")
        setRequestProperty("Accept", "application/json")
        if (postBody != null) {
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            outputStream.use { it.write(postBody.toByteArray(Charsets.UTF_8)) }
        }
    }
    try {
        val code = connection.responseCode
        if (code !in 200..299) throw HttpStatusException(code, connection.getHeaderField("Retry-After")?.toLongOrNull())
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } finally { connection.disconnect() }
}
