package com.nodecasttv.app.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class XtreamApi(
    private val serverUrl: String,
    private val username: String,
    private val password: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private fun baseUrl(): String {
        val url = serverUrl.trimEnd('/')
        return "$url/player_api.php?username=$username&password=$password"
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}: $url")
            response.body?.string() ?: ""
        }
    }

    suspend fun authenticate(): ServerInfo {
        val json = get(baseUrl())
        return gson.fromJson(json, ServerInfo::class.java)
    }

    // ─── Live TV ────────────────────────────────────────────────────────────

    suspend fun getLiveCategories(): List<Category> {
        val json = get("${baseUrl()}&action=get_live_categories")
        val type = object : TypeToken<List<Category>>() {}.type
        return try { gson.fromJson(json, type) } catch (e: Exception) { emptyList() }
    }

    suspend fun getLiveStreams(categoryId: String = ""): List<LiveStream> {
        val url = if (categoryId.isNotEmpty())
            "${baseUrl()}&action=get_live_streams&category_id=$categoryId"
        else
            "${baseUrl()}&action=get_live_streams"
        val json = get(url)
        val type = object : TypeToken<List<LiveStream>>() {}.type
        return try { gson.fromJson(json, type) } catch (e: Exception) { emptyList() }
    }

    fun getLiveStreamUrl(streamId: Int, useHls: Boolean = true): String {
        val base = serverUrl.trimEnd('/')
        return if (useHls) "$base/$username/$password/$streamId.m3u8"
        else "$base/$username/$password/$streamId"
    }

    // ─── VOD ────────────────────────────────────────────────────────────────

    suspend fun getVodCategories(): List<Category> {
        val json = get("${baseUrl()}&action=get_vod_categories")
        val type = object : TypeToken<List<Category>>() {}.type
        return try { gson.fromJson(json, type) } catch (e: Exception) { emptyList() }
    }

    suspend fun getVodStreams(categoryId: String = ""): List<VodStream> {
        val url = if (categoryId.isNotEmpty())
            "${baseUrl()}&action=get_vod_streams&category_id=$categoryId"
        else
            "${baseUrl()}&action=get_vod_streams"
        val json = get(url)
        val type = object : TypeToken<List<VodStream>>() {}.type
        return try { gson.fromJson(json, type) } catch (e: Exception) { emptyList() }
    }

    fun getVodStreamUrl(streamId: Int, containerExtension: String): String {
        val base = serverUrl.trimEnd('/')
        return "$base/movie/$username/$password/$streamId.$containerExtension"
    }

    // ─── Series ─────────────────────────────────────────────────────────────

    suspend fun getSeriesCategories(): List<Category> {
        val json = get("${baseUrl()}&action=get_series_categories")
        val type = object : TypeToken<List<Category>>() {}.type
        return try { gson.fromJson(json, type) } catch (e: Exception) { emptyList() }
    }

    suspend fun getSeries(categoryId: String = ""): List<Series> {
        val url = if (categoryId.isNotEmpty())
            "${baseUrl()}&action=get_series&category_id=$categoryId"
        else
            "${baseUrl()}&action=get_series"
        val json = get(url)
        val type = object : TypeToken<List<Series>>() {}.type
        return try { gson.fromJson(json, type) } catch (e: Exception) { emptyList() }
    }

    suspend fun getSeriesInfo(seriesId: String): SeriesInfo {
        val json = get("${baseUrl()}&action=get_series_info&series_id=$seriesId")
        return try { gson.fromJson(json, SeriesInfo::class.java) } catch (e: Exception) { SeriesInfo() }
    }

    fun getEpisodeUrl(episodeId: String, containerExtension: String): String {
        val base = serverUrl.trimEnd('/')
        return "$base/series/$username/$password/$episodeId.$containerExtension"
    }
}
