package com.codespacemanager.app.data.remote.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(
    val version: String,
    val url: String,
    val publishedAt: String? = null,
)

/** Prueft, ob im GitHub-Repository eine neuere Version als Release vorliegt. */
@Singleton
class UpdateChecker @Inject constructor(
    private val httpClient: OkHttpClient,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(): UpdateInfo? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPOSITORY/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        val response = try {
            withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
        } catch (e: IOException) {
            return null
        }
        val text = response.use { it.body?.string().orEmpty() }
        if (!response.isSuccessful) return null
        val element = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val tag = element["tag_name"]?.jsonPrimitive?.content?.removePrefix("v") ?: return null
        val url = element["html_url"]?.jsonPrimitive?.content
            ?: "https://github.com/$REPOSITORY/releases"
        val published = element["published_at"]?.jsonPrimitive?.content
        return UpdateInfo(version = tag, url = url, publishedAt = published)
    }

    companion object {
        const val REPOSITORY = "jahdaganj02ki-sketch/AndroidCodespaceConnector"

        /** Vergleich zweier Versionen (semantisch, tolerant gegenüber "v"-Praefix). */
        fun isNewer(latest: String, current: String): Boolean = compare(latest, current) > 0

        private fun compare(a: String, b: String): Int {
            val left = a.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
            val right = b.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
            val size = maxOf(left.size, right.size)
            for (i in 0 until size) {
                val l = left.getOrElse(i) { 0 }
                val r = right.getOrElse(i) { 0 }
                if (l != r) return l.compareTo(r)
            }
            return 0
        }
    }
}
