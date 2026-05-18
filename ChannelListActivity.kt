package com.nodecasttv.app.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.nodecasttv.app.App
import com.nodecasttv.app.R
import com.nodecasttv.app.api.*
import kotlinx.coroutines.*

class ChannelListActivity : FragmentActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var allItems: List<ContentItem> = emptyList()
    private lateinit var adapter: ContentItemAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_channel_list)

        val categoryId = intent.getStringExtra("category_id") ?: ""
        val categoryName = intent.getStringExtra("category_name") ?: ""
        val contentType = ContentType.valueOf(intent.getStringExtra("content_type") ?: "LIVE")

        val rvContent = findViewById<RecyclerView>(R.id.rv_content)
        val progress = findViewById<ProgressBar>(R.id.progress)
        val tvTitle = findViewById<TextView>(R.id.tv_title)
        val tvCount = findViewById<TextView>(R.id.tv_count)
        val etSearch = findViewById<EditText>(R.id.et_search)
        val tvError = findViewById<TextView>(R.id.tv_error)
        val btnBack = findViewById<TextView>(R.id.btn_back)

        tvTitle.text = categoryName

        btnBack.setOnClickListener { finish() }

        adapter = ContentItemAdapter { item ->
            if (App.getPortalMode() == "stalker") {
                handleStalkerItemClick(item)
            } else {
                handleXtreamItemClick(item)
            }
        }

        // Grid: 3 columns for VOD/Series, 1 for Live
        val spanCount = if (contentType == ContentType.LIVE) 1 else 3
        rvContent.layoutManager = GridLayoutManager(this, spanCount)
        rvContent.adapter = adapter

        // Search
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().lowercase()
                val filtered = if (query.isEmpty()) allItems
                else allItems.filter { it.name.lowercase().contains(query) }
                adapter.submitList(filtered)
                tvCount.text = "${filtered.size} itens"
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        loadContent(contentType, categoryId, progress, tvCount, tvError)
    }

    private fun handleXtreamItemClick(item: ContentItem) {
        val api = App.getApi() ?: return
        val intent = Intent(this, PlayerActivity::class.java)
        when (item.type) {
            ContentType.LIVE -> {
                val s = item.extra as? LiveStream ?: return
                intent.putExtra("stream_url", api.getLiveStreamUrl(s.streamId))
                intent.putExtra("title", item.name)
                startActivity(intent)
            }
            ContentType.VOD -> {
                val s = item.extra as? VodStream ?: return
                intent.putExtra("stream_url", api.getVodStreamUrl(s.streamId, s.containerExtension))
                intent.putExtra("title", item.name)
                startActivity(intent)
            }
            ContentType.SERIES -> {
                val s = item.extra as? Series ?: return
                startActivity(Intent(this, SeriesDetailActivity::class.java).apply {
                    putExtra("series_id",    s.seriesId)
                    putExtra("series_name",  item.name)
                    putExtra("series_cover", item.icon)
                })
            }
        }
    }

    private fun handleStalkerItemClick(item: ContentItem) {
        if (item.type == ContentType.SERIES) {
            val s = item.extra as? com.nodecasttv.app.api.StalkerSeries ?: return
            startActivity(Intent(this, SeriesDetailActivity::class.java).apply {
                putExtra("series_id",    s.id)
                putExtra("series_name",  item.name)
                putExtra("series_cover", item.icon)
                putExtra("portal_mode",  "stalker")
            })
            return
        }

        // Para canais e VOD: resolve a URL via create_link em background
        val cmd = when (item.type) {
            ContentType.LIVE -> (item.extra as? com.nodecasttv.app.api.StalkerChannel)?.cmd
            ContentType.VOD  -> (item.extra as? com.nodecasttv.app.api.StalkerVod)?.cmd
            else -> null
        } ?: return

        scope.launch {
            try {
                val api = App.getStalkerApi() ?: return@launch
                api.authenticate()
                val url = api.createLink(cmd)
                val intent = Intent(this@ChannelListActivity, PlayerActivity::class.java)
                intent.putExtra("stream_url", url)
                intent.putExtra("title", item.name)
                startActivity(intent)
            } catch (e: Exception) {
                // ignora silenciosamente — o PlayerActivity trata erro de stream
            }
        }
    }

    private fun loadContent(
        type: ContentType,
        categoryId: String,
        progress: ProgressBar,
        tvCount: TextView,
        tvError: TextView
    ) {
        scope.launch {
            progress.visibility = View.VISIBLE
            tvError.visibility  = View.GONE
            try {
                allItems = if (App.getPortalMode() == "stalker")
                    loadStalkerContent(type, categoryId)
                else
                    loadXtreamContent(type, categoryId)
                adapter.submitList(allItems)
                tvCount.text = "${allItems.size} itens"
            } catch (e: Exception) {
                tvError.text = "Erro: ${e.message}"
                tvError.visibility = View.VISIBLE
            } finally {
                progress.visibility = View.GONE
            }
        }
    }

    private suspend fun loadXtreamContent(type: ContentType, categoryId: String): List<ContentItem> {
        val api = App.getApi() ?: return emptyList()
        return when (type) {
            ContentType.LIVE   -> api.getLiveStreams(categoryId).map { s ->
                ContentItem(s.streamId.toString(), s.name, s.streamIcon, ContentType.LIVE, s) }
            ContentType.VOD    -> api.getVodStreams(categoryId).map { s ->
                ContentItem(s.streamId.toString(), s.name, s.streamIcon ?: s.cover, ContentType.VOD, s) }
            ContentType.SERIES -> api.getSeries(categoryId).map { s ->
                ContentItem(s.seriesId, s.name, s.cover, ContentType.SERIES, s) }
        }
    }

    private suspend fun loadStalkerContent(type: ContentType, categoryId: String): List<ContentItem> {
        val api = App.getStalkerApi() ?: return emptyList()
        api.authenticate()
        val genreId = categoryId.ifEmpty { "*" }
        return when (type) {
            ContentType.LIVE   -> api.getLiveChannels(genreId).map { ch ->
                ContentItem(ch.id, ch.name, ch.logo, ContentType.LIVE, ch) }
            ContentType.VOD    -> api.getVodList(genreId).map { v ->
                ContentItem(v.id, v.name, v.screenshot, ContentType.VOD, v) }
            ContentType.SERIES -> api.getSeriesList(genreId).map { s ->
                ContentItem(s.id, s.name, s.cover, ContentType.SERIES, s) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

// ─── Content Item Adapter ─────────────────────────────────────────────────────

class ContentItemAdapter(
    private val onClick: (ContentItem) -> Unit
) : RecyclerView.Adapter<ContentItemAdapter.VH>() {

    private val items = mutableListOf<ContentItem>()

    fun submitList(list: List<ContentItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val ivIcon: android.widget.ImageView = itemView.findViewById(R.id.iv_icon)
        val tvName: TextView = itemView.findViewById(R.id.tv_name)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_content, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name

        if (!item.icon.isNullOrEmpty()) {
            holder.ivIcon.load(item.icon) {
                crossfade(true)
                placeholder(android.R.drawable.ic_media_play)
                error(android.R.drawable.ic_media_play)
            }
            holder.ivIcon.visibility = View.VISIBLE
        } else {
            holder.ivIcon.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            v.alpha = if (hasFocus) 1f else 0.85f
            v.scaleX = if (hasFocus) 1.05f else 1f
            v.scaleY = if (hasFocus) 1.05f else 1f
        }
    }

    override fun getItemCount() = items.size
}
