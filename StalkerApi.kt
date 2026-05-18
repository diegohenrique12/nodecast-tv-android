package com.nodecasttv.app.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cliente Stalker Portal / MAG
 * Compatível com portais que usam portal.php + autenticação via MAC
 */
class StalkerApi(
    portalUrl: String,
    val macAddress: String
) {
    private val baseUrl: String
    private var token: String = ""
    private var serialNumber: String = "062014N062061"
    private var deviceId: String = "0e53facd087819f7496701703261e084bfe6903a"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    init {
        var url = portalUrl.trimEnd('/')
        if (!url.endsWith("portal.php")) url = "$url/portal.php"
        baseUrl = url
    }

    // ─── HTTP ─────────────────────────────────────────────────────────────────

    private fun buildRequest(params: Map<String, String>): Request {
        val sb = StringBuilder(baseUrl).append("?JsHttpRequest=1-xml")
        params.forEach { (k, v) -> sb.append("&").append(k).append("=").append(v) }

        val builder = Request.Builder()
            .url(sb.toString())
            .addHeader("User-Agent",
                "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 " +
                "(KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3")
            .addHeader("X-User-Agent", "Model: MAG250; Link: Embedded")
            .addHeader("Cookie", "mac=${macAddress}; stb_lang=pt; timezone=America/Sao_Paulo")

        if (token.isNotEmpty()) builder.addHeader("Authorization", "Bearer $token")
        return builder.build()
    }

    private suspend fun get(params: Map<String, String>): JSONObject = withContext(Dispatchers.IO) {
        val request = buildRequest(params)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            val raw = response.body?.string()?.trim() ?: ""
            // Stalker às vezes retorna "get_xxx({...})" — extrai só o JSON
            val cleaned = if (raw.startsWith("{")) raw
                          else raw.substringAfter('{').let { "{$it" }.trimEnd(')')
            JSONObject(cleaned)
        }
    }

    // ─── Autenticação ─────────────────────────────────────────────────────────

    /** Handshake inicial — retorna token */
    suspend fun handshake(): String {
        val data = get(mapOf("type" to "stb", "action" to "handshake"))
        token = data.optJSONObject("js")?.optString("token") ?: ""
        return token
    }

    /** get_profile — necessário após handshake em vários servidores */
    suspend fun getProfile() {
        get(mapOf(
            "type"       to "stb",
            "action"     to "get_profile",
            "hd"         to "1",
            "ver"        to "ImageDescription: 0.2.18-r14-pub-250; PORTAL version: 5.3.0",
            "num_banks"  to "2",
            "sn"         to serialNumber,
            "stb_type"   to "MAG250",
            "hw_version" to deviceId,
            "metrics"    to """{"mac":"$macAddress","model":"MAG250","sn":"$serialNumber","type":"stb"}"""
        ))
    }

    /** Autenticação completa: handshake + profile */
    suspend fun authenticate(): Boolean {
        val tok = handshake()
        if (tok.isEmpty()) return false
        getProfile()
        return true
    }

    // ─── Categorias ───────────────────────────────────────────────────────────

    suspend fun getLiveCategories(): List<StalkerCategory> {
        val data = get(mapOf("type" to "itv", "action" to "get_genres"))
        return parseCategoryList(data.optJSONArray("js"))
    }

    suspend fun getVodCategories(): List<StalkerCategory> {
        val data = get(mapOf("type" to "vod", "action" to "get_categories"))
        return parseCategoryList(data.optJSONArray("js"))
    }

    suspend fun getSeriesCategories(): List<StalkerCategory> {
        val data = get(mapOf("type" to "series", "action" to "get_categories"))
        return parseCategoryList(data.optJSONArray("js"))
    }

    private fun parseCategoryList(arr: JSONArray?): List<StalkerCategory> {
        arr ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val name = o.optString("title").ifEmpty { o.optString("name", "Outros") }
            StalkerCategory(id, name)
        }
    }

    // ─── Canais ao vivo ───────────────────────────────────────────────────────

    suspend fun getLiveChannels(genreId: String = "*", page: Int = 1): List<StalkerChannel> {
        val data = get(mapOf(
            "type"   to "itv",
            "action" to "get_ordered_list",
            "genre"  to genreId,
            "p"      to page.toString(),
            "JsHttpRequest" to "1-xml"
        ))
        val js = data.optJSONObject("js") ?: return emptyList()
        val arr = js.optJSONArray("data") ?: return emptyList()
        return parseChannelList(arr)
    }

    private fun parseChannelList(arr: JSONArray): List<StalkerChannel> {
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val cmd = o.optString("cmd")
            val id = extractStreamId(cmd) ?: return@mapNotNull null
            StalkerChannel(
                id       = id,
                name     = o.optString("name", "Canal"),
                logo     = o.optString("logo"),
                genreId  = o.optString("tv_genre_id"),
                cmd      = cmd
            )
        }
    }

    // ─── VOD ──────────────────────────────────────────────────────────────────

    suspend fun getVodList(categoryId: String = "*", page: Int = 1): List<StalkerVod> {
        val data = get(mapOf(
            "type"        to "vod",
            "action"      to "get_ordered_list",
            "category"    to categoryId,
            "p"           to page.toString(),
            "sortby"      to "added",
            "fav"         to "0",
            "search"      to ""
        ))
        val js = data.optJSONObject("js") ?: return emptyList()
        val arr = js.optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val cmd = o.optString("cmd")
            val id = extractStreamId(cmd) ?: return@mapNotNull null
            StalkerVod(
                id          = id,
                name        = o.optString("name", "Filme"),
                screenshot  = o.optString("screenshot_uri"),
                description = o.optString("description"),
                categoryId  = o.optString("category"),
                cmd         = cmd
            )
        }
    }

    // ─── Séries ───────────────────────────────────────────────────────────────

    suspend fun getSeriesList(categoryId: String = "*", page: Int = 1): List<StalkerSeries> {
        val data = get(mapOf(
            "type"     to "series",
            "action"   to "get_ordered_list",
            "category" to categoryId,
            "p"        to page.toString(),
            "sortby"   to "added"
        ))
        val js = data.optJSONObject("js") ?: return emptyList()
        val arr = js.optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            StalkerSeries(
                id         = o.optString("id"),
                name       = o.optString("name", "Série"),
                cover      = o.optString("screenshot_uri"),
                categoryId = o.optString("category")
            )
        }
    }

    suspend fun getSeriesSeasons(seriesId: String): List<StalkerSeason> {
        val data = get(mapOf(
            "type"      to "series",
            "action"    to "get_seasons",
            "movie_id"  to seriesId
        ))
        val arr = data.optJSONArray("js") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            StalkerSeason(
                id     = o.optString("id"),
                name   = o.optString("name", "Temporada"),
                season = o.optInt("season_number", i + 1)
            )
        }
    }

    suspend fun getSeriesEpisodes(seasonId: String): List<StalkerEpisode> {
        val data = get(mapOf(
            "type"      to "series",
            "action"    to "get_episodes",
            "season_id" to seasonId
        ))
        val arr = data.optJSONArray("js") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val cmd = o.optString("cmd")
            val id = extractStreamId(cmd) ?: return@mapNotNull null
            StalkerEpisode(
                id     = id,
                name   = o.optString("name", "Episódio"),
                cmd    = cmd
            )
        }
    }

    // ─── URLs de stream ───────────────────────────────────────────────────────

    /**
     * Resolve a URL definitiva de um canal/vod a partir do cmd.
     * Compatível com portais que retornam link direto ou link ffmpeg interno.
     */
    suspend fun createLink(cmd: String): String {
        return try {
            val data = get(mapOf(
                "type"   to "itv",
                "action" to "create_link",
                "cmd"    to cmd,
                "forced_storage" to "undefined",
                "disable_ad" to "0",
                "download" to "0",
                "JsHttpRequest" to "1-xml"
            ))
            val cmdResult = data.optJSONObject("js")?.optString("cmd") ?: ""
            // Remove prefixo "ffmpeg " se presente
            cmdResult.removePrefix("ffmpeg ").trim().ifEmpty { cmd }
        } catch (e: Exception) {
            // Fallback: tenta usar o cmd diretamente
            cmd.removePrefix("ffmpeg ").trim()
        }
    }

    // ─── Utilitários ──────────────────────────────────────────────────────────

    /**
     * Extrai o ID numérico do final do cmd.
     * Ex: "ffmpeg http://host/user/pass/12345" → "12345"
     */
    private fun extractStreamId(cmd: String): String? {
        val clean = cmd.removePrefix("ffmpeg ").trim()
        val id = clean.substringAfterLast("/").trim()
        return if (id.isNotEmpty()) id else null
    }
}

// ─── Modelos Stalker ──────────────────────────────────────────────────────────

data class StalkerCategory(val id: String, val name: String)

data class StalkerChannel(
    val id: String,
    val name: String,
    val logo: String,
    val genreId: String,
    val cmd: String
)

data class StalkerVod(
    val id: String,
    val name: String,
    val screenshot: String,
    val description: String,
    val categoryId: String,
    val cmd: String
)

data class StalkerSeries(
    val id: String,
    val name: String,
    val cover: String,
    val categoryId: String
)

data class StalkerSeason(val id: String, val name: String, val season: Int)

data class StalkerEpisode(val id: String, val name: String, val cmd: String)
