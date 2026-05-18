package com.nodecasttv.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nodecasttv.app.App
import com.nodecasttv.app.R
import com.nodecasttv.app.api.Episode
import kotlinx.coroutines.*

class SeriesDetailActivity : FragmentActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_series_detail)

        val seriesId = intent.getStringExtra("series_id") ?: ""
        val seriesName = intent.getStringExtra("series_name") ?: ""

        val tvTitle = findViewById<TextView>(R.id.tv_title)
        val rvSeasons = findViewById<RecyclerView>(R.id.rv_seasons)
        val rvEpisodes = findViewById<RecyclerView>(R.id.rv_episodes)
        val progress = findViewById<ProgressBar>(R.id.progress)
        val tvError = findViewById<TextView>(R.id.tv_error)
        val btnBack = findViewById<TextView>(R.id.btn_back)

        tvTitle.text = seriesName
        btnBack.setOnClickListener { finish() }

        var allEpisodes: Map<String, List<Episode>> = emptyMap()
        val episodeAdapter = EpisodeAdapter { episode ->
            val api = App.getApi() ?: return@EpisodeAdapter
            val url = api.getEpisodeUrl(episode.id, episode.containerExtension)
            val i = Intent(this, PlayerActivity::class.java).apply {
                putExtra("stream_url", url)
                putExtra("title", "${seriesName} - ${episode.title}")
            }
            startActivity(i)
        }
        rvEpisodes.layoutManager = LinearLayoutManager(this)
        rvEpisodes.adapter = episodeAdapter

        scope.launch {
            progress.visibility = View.VISIBLE
            try {
                val api = App.getApi() ?: return@launch
                val info = api.getSeriesInfo(seriesId)
                allEpisodes = info.episodes ?: emptyMap()

                val seasons = allEpisodes.keys.sortedBy { it.toIntOrNull() ?: 0 }

                // Season selector
                val seasonAdapter = SeasonAdapter(seasons) { season ->
                    val eps = allEpisodes[season] ?: emptyList()
                    episodeAdapter.submitList(eps)
                }
                rvSeasons.layoutManager = LinearLayoutManager(this@SeriesDetailActivity, LinearLayoutManager.HORIZONTAL, false)
                rvSeasons.adapter = seasonAdapter

                // Show first season
                if (seasons.isNotEmpty()) {
                    episodeAdapter.submitList(allEpisodes[seasons[0]] ?: emptyList())
                }

                progress.visibility = View.GONE
            } catch (e: Exception) {
                progress.visibility = View.GONE
                tvError.visibility = View.VISIBLE
                tvError.text = "Erro: ${e.message}"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

class SeasonAdapter(
    private val seasons: List<String>,
    private val onSelect: (String) -> Unit
) : RecyclerView.Adapter<SeasonAdapter.VH>() {

    private var selectedIndex = 0

    inner class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.WRAP_CONTENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
            setPadding(32, 20, 32, 20)
            textSize = 15f
            setTextColor(android.graphics.Color.WHITE)
            isFocusable = true
        }
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.tv.text = "Temporada ${seasons[position]}"
        holder.tv.setBackgroundColor(
            if (position == selectedIndex) 0xFF1A73E8.toInt() else 0xFF2D2D2D.toInt()
        )
        holder.tv.setOnClickListener {
            val old = selectedIndex
            selectedIndex = position
            notifyItemChanged(old)
            notifyItemChanged(position)
            onSelect(seasons[position])
        }
    }

    override fun getItemCount() = seasons.size
}

class EpisodeAdapter(
    private val onPlay: (Episode) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.VH>() {

    private val items = mutableListOf<Episode>()

    fun submitList(list: List<Episode>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
            setPadding(32, 24, 32, 24)
            textSize = 14f
            setTextColor(0xFFCCCCCC.toInt())
            isFocusable = true
        }
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ep = items[position]
        holder.tv.text = "Ep. ${ep.episodeNum}  —  ${ep.title}"
        holder.tv.setOnClickListener { onPlay(ep) }
        holder.tv.setOnFocusChangeListener { v, hasFocus ->
            (v as? TextView)?.setBackgroundColor(
                if (hasFocus) 0xFF1A73E8.toInt() else android.graphics.Color.TRANSPARENT
            )
        }
    }

    override fun getItemCount() = items.size
}
