package com.nodecasttv.app.api

import com.google.gson.annotations.SerializedName

// ─── Models ───────────────────────────────────────────────────────────────────

data class ServerInfo(
    @SerializedName("user_info") val userInfo: UserInfo?,
    @SerializedName("server_info") val serverInfo: XtreamServerInfo?
)

data class UserInfo(
    val username: String = "",
    val password: String = "",
    val status: String = "",
    @SerializedName("exp_date") val expDate: String? = null,
    @SerializedName("is_trial") val isTrial: String = "0",
    @SerializedName("active_cons") val activeCons: String = "0",
    @SerializedName("max_connections") val maxConnections: String = "1"
)

data class XtreamServerInfo(
    val url: String = "",
    val port: String = "",
    val https_port: String = "",
    val server_protocol: String = "http",
    val rtmp_port: String = "",
    val timezone: String = "",
    val timestamp_now: Long = 0,
    val time_now: String = ""
)

data class Category(
    @SerializedName("category_id") val id: String,
    @SerializedName("category_name") val name: String,
    @SerializedName("parent_id") val parentId: Int = 0
)

data class LiveStream(
    @SerializedName("num") val num: Int = 0,
    val name: String = "",
    @SerializedName("stream_type") val streamType: String = "live",
    @SerializedName("stream_id") val streamId: Int = 0,
    @SerializedName("stream_icon") val streamIcon: String? = null,
    val epg: String? = null,
    @SerializedName("added") val added: String? = null,
    @SerializedName("category_id") val categoryId: String = "",
    @SerializedName("custom_sid") val customSid: String? = null,
    @SerializedName("tv_archive") val tvArchive: Int = 0,
    @SerializedName("tv_archive_duration") val tvArchiveDuration: Int = 0,
    @SerializedName("epg_channel_id") val epgChannelId: String? = null
)

data class VodStream(
    val num: Int = 0,
    val name: String = "",
    @SerializedName("stream_type") val streamType: String = "movie",
    @SerializedName("stream_id") val streamId: Int = 0,
    @SerializedName("stream_icon") val streamIcon: String? = null,
    val rating: String? = null,
    @SerializedName("rating_5based") val rating5: Float = 0f,
    @SerializedName("added") val added: String? = null,
    @SerializedName("category_id") val categoryId: String = "",
    @SerializedName("container_extension") val containerExtension: String = "mp4",
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    val youtube_trailer: String? = null,
    @SerializedName("episode_run_time") val episodeRunTime: String? = null,
    val cover: String? = null
)

data class Series(
    @SerializedName("series_id") val seriesId: String = "",
    val name: String = "",
    val cover: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("last_modified") val lastModified: String? = null,
    val rating: String? = null,
    @SerializedName("rating_5based") val rating5: Float = 0f,
    val backdrop_path: List<String>? = null,
    val youtube_trailer: String? = null,
    @SerializedName("episode_run_time") val episodeRunTime: String? = null,
    @SerializedName("category_id") val categoryId: String = ""
)

data class SeriesInfo(
    val info: SeriesDetail? = null,
    val episodes: Map<String, List<Episode>>? = null
)

data class SeriesDetail(
    val name: String = "",
    val cover: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val rating: String? = null
)

data class Episode(
    val id: String = "",
    @SerializedName("episode_num") val episodeNum: String = "",
    val title: String = "",
    @SerializedName("container_extension") val containerExtension: String = "mp4",
    @SerializedName("season") val season: Int = 1,
    val info: EpisodeInfo? = null,
    @SerializedName("custom_sid") val customSid: String? = null,
    @SerializedName("added") val added: String? = null
)

data class EpisodeInfo(
    val movie_image: String? = null,
    val plot: String? = null,
    val duration_secs: Int? = null,
    val duration: String? = null,
    val rating: Float? = null,
    val release_date: String? = null
)

// ─── Content types for UI ─────────────────────────────────────────────────────

enum class ContentType { LIVE, VOD, SERIES }

data class ContentItem(
    val id: String,
    val name: String,
    val icon: String?,
    val type: ContentType,
    val extra: Any? = null // LiveStream, VodStream, or Series
)
