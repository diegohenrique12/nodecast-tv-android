package com.nodecasttv.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nodecasttv.app.App
import com.nodecasttv.app.R
import com.nodecasttv.app.api.Category
import com.nodecasttv.app.api.ContentType
import com.nodecasttv.app.api.StalkerCategory
import kotlinx.coroutines.*

class BrowseActivity : FragmentActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var tabAdapter: TabAdapter
    private lateinit var categoryAdapter: CategoryAdapter

    private val tabs = listOf(
        Pair(ContentType.LIVE,   "📺  AO VIVO"),
        Pair(ContentType.VOD,    "🎬  FILMES"),
        Pair(ContentType.SERIES, "📺  SÉRIES")
    )
    private var selectedTab = ContentType.LIVE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browse)

        if (!App.hasCredentials()) {
            startActivity(Intent(this, SetupActivity::class.java)); finish(); return
        }

        val rvTabs       = findViewById<RecyclerView>(R.id.rv_tabs)
        val rvCategories = findViewById<RecyclerView>(R.id.rv_categories)
        val tvTitle      = findViewById<TextView>(R.id.tv_title)
        val progress     = findViewById<ProgressBar>(R.id.progress)
        val tvError      = findViewById<TextView>(R.id.tv_error)
        val btnSettings  = findViewById<TextView>(R.id.btn_settings)

        tabAdapter = TabAdapter(tabs.map { it.second }) { index ->
            selectedTab = tabs[index].first
            loadCategories(progress, tvError)
        }
        rvTabs.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        rvTabs.adapter = tabAdapter

        categoryAdapter = CategoryAdapter { id, name ->
            val intent = Intent(this, ChannelListActivity::class.java).apply {
                putExtra("category_id",   id)
                putExtra("category_name", name)
                putExtra("content_type",  selectedTab.name)
                putExtra("portal_mode",   App.getPortalMode())
            }
            startActivity(intent)
        }
        rvCategories.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        rvCategories.adapter = categoryAdapter

        btnSettings.setOnClickListener {
            App.clearCredentials()
            startActivity(Intent(this, SetupActivity::class.java)); finish()
        }

        val mode = if (App.getPortalMode() == "stalker") "MAG/Stalker" else "Xtream"
        tvTitle.text = "NodeCast TV · $mode"
        loadCategories(progress, tvError)
    }

    private fun loadCategories(progress: ProgressBar, tvError: TextView) {
        scope.launch {
            progress.visibility = View.VISIBLE
            tvError.visibility  = View.GONE
            try {
                val list: List<Pair<String, String>> = when (App.getPortalMode()) {
                    "stalker" -> loadStalkerCategories()
                    else      -> loadXtreamCategories()
                }
                val all = listOf(Pair("", "🔤 Todos")) + list
                categoryAdapter.submitList(all)
            } catch (e: Exception) {
                tvError.text = "Erro ao carregar: ${e.message}"
                tvError.visibility = View.VISIBLE
            } finally {
                progress.visibility = View.GONE
            }
        }
    }

    private suspend fun loadXtreamCategories(): List<Pair<String, String>> {
        val api = App.getApi() ?: return emptyList()
        val cats: List<Category> = when (selectedTab) {
            ContentType.LIVE   -> api.getLiveCategories()
            ContentType.VOD    -> api.getVodCategories()
            ContentType.SERIES -> api.getSeriesCategories()
        }
        return cats.map { Pair(it.id, it.name) }
    }

    private suspend fun loadStalkerCategories(): List<Pair<String, String>> {
        val api = App.getStalkerApi() ?: return emptyList()
        // re-autentica para garantir token fresco
        api.authenticate()
        val cats: List<StalkerCategory> = when (selectedTab) {
            ContentType.LIVE   -> api.getLiveCategories()
            ContentType.VOD    -> api.getVodCategories()
            ContentType.SERIES -> api.getSeriesCategories()
        }
        return cats.map { Pair(it.id, it.name) }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            App.clearCredentials()
            startActivity(Intent(this, SetupActivity::class.java)); finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}

// ─── Tab Adapter ──────────────────────────────────────────────────────────────

class TabAdapter(
    private val tabs: List<String>,
    private val onTabSelected: (Int) -> Unit
) : RecyclerView.Adapter<TabAdapter.VH>() {

    private var selectedIndex = 0

    inner class VH(val view: TextView) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
            setPadding(32, 24, 32, 24)
            textSize = 16f
            isFocusable = true
            isFocusableInTouchMode = true
            setTextColor(android.graphics.Color.WHITE)
        }
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.view.text = tabs[position]
        if (position == selectedIndex) {
            holder.view.setBackgroundColor(0xFF1A73E8.toInt())
            holder.view.setTypeface(holder.view.typeface, android.graphics.Typeface.BOLD)
        } else {
            holder.view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            holder.view.setTypeface(android.graphics.Typeface.DEFAULT)
        }
        holder.view.setOnClickListener {
            val old = selectedIndex
            selectedIndex = position
            notifyItemChanged(old); notifyItemChanged(position)
            onTabSelected(position)
        }
        holder.view.setOnFocusChangeListener { v, hasFocus ->
            if (position != selectedIndex)
                (v as? TextView)?.setBackgroundColor(
                    if (hasFocus) 0xFF2D2D2D.toInt() else android.graphics.Color.TRANSPARENT)
        }
    }

    override fun getItemCount() = tabs.size
}

// ─── Category Adapter ─────────────────────────────────────────────────────────

class CategoryAdapter(
    private val onClick: (id: String, name: String) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    private val items = mutableListOf<Pair<String, String>>()

    fun submitList(list: List<Pair<String, String>>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    inner class VH(val view: TextView) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
            setPadding(32, 20, 32, 20)
            textSize = 15f
            isFocusable = true; isFocusableInTouchMode = true
            setTextColor(0xFFCCCCCC.toInt())
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxLines = 1
        }
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (id, name) = items[position]
        holder.view.text = name
        holder.view.setOnClickListener { onClick(id, name) }
        holder.view.setOnFocusChangeListener { v, hasFocus ->
            (v as? TextView)?.setBackgroundColor(
                if (hasFocus) 0xFF1A73E8.toInt() else android.graphics.Color.TRANSPARENT)
        }
    }

    override fun getItemCount() = items.size
}

class BrowseActivity : FragmentActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var tabAdapter: TabAdapter
    private lateinit var categoryAdapter: CategoryAdapter

    private val tabs = listOf(
        Pair(ContentType.LIVE, "📺  AO VIVO"),
        Pair(ContentType.VOD, "🎬  FILMES"),
        Pair(ContentType.SERIES, "📺  SÉRIES")
    )
    private var selectedTab = ContentType.LIVE
    private var categories: List<Category> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browse)

        val api = App.getApi() ?: run {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        val rvTabs = findViewById<RecyclerView>(R.id.rv_tabs)
        val rvCategories = findViewById<RecyclerView>(R.id.rv_categories)
        val tvTitle = findViewById<TextView>(R.id.tv_title)
        val progress = findViewById<ProgressBar>(R.id.progress)
        val tvError = findViewById<TextView>(R.id.tv_error)
        val btnSettings = findViewById<TextView>(R.id.btn_settings)

        // Setup tabs
        tabAdapter = TabAdapter(tabs.map { it.second }) { index ->
            selectedTab = tabs[index].first
            loadCategories(api, progress, tvError)
        }
        rvTabs.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        rvTabs.adapter = tabAdapter

        // Setup categories
        categoryAdapter = CategoryAdapter { category ->
            val intent = Intent(this, ChannelListActivity::class.java).apply {
                putExtra("category_id", category.id)
                putExtra("category_name", category.name)
                putExtra("content_type", selectedTab.name)
            }
            startActivity(intent)
        }
        rvCategories.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        rvCategories.adapter = categoryAdapter

        btnSettings.setOnClickListener {
            App.clearCredentials()
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
        }

        tvTitle.text = "NodeCast TV"
        loadCategories(api, progress, tvError)
    }

    private fun loadCategories(
        api: com.nodecasttv.app.api.XtreamApi,
        progress: ProgressBar,
        tvError: TextView
    ) {
        scope.launch {
            progress.visibility = View.VISIBLE
            tvError.visibility = View.GONE
            try {
                categories = when (selectedTab) {
                    ContentType.LIVE -> api.getLiveCategories()
                    ContentType.VOD -> api.getVodCategories()
                    ContentType.SERIES -> api.getSeriesCategories()
                }
                // Prepend "Todos" category
                val allCategories = listOf(Category("", "🔤 Todos")) + categories
                categoryAdapter.submitList(allCategories)
                progress.visibility = View.GONE
            } catch (e: Exception) {
                progress.visibility = View.GONE
                tvError.visibility = View.VISIBLE
                tvError.text = "Erro ao carregar: ${e.message}"
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            App.clearCredentials()
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

// ─── Tab Adapter ──────────────────────────────────────────────────────────────

class TabAdapter(
    private val tabs: List<String>,
    private val onTabSelected: (Int) -> Unit
) : RecyclerView.Adapter<TabAdapter.VH>() {

    private var selectedIndex = 0

    inner class VH(val view: TextView) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
            setPadding(32, 24, 32, 24)
            textSize = 16f
            isFocusable = true
            isFocusableInTouchMode = true
            setTextColor(android.graphics.Color.WHITE)
            background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        }
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.view.text = tabs[position]
        if (position == selectedIndex) {
            holder.view.setBackgroundColor(0xFF1A73E8.toInt())
            holder.view.setTypeface(holder.view.typeface, android.graphics.Typeface.BOLD)
        } else {
            holder.view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            holder.view.setTypeface(android.graphics.Typeface.DEFAULT)
        }
        holder.view.setOnClickListener {
            val old = selectedIndex
            selectedIndex = position
            notifyItemChanged(old)
            notifyItemChanged(position)
            onTabSelected(position)
        }
        holder.view.setOnFocusChangeListener { v, hasFocus ->
            if (position != selectedIndex) {
                (v as? TextView)?.setBackgroundColor(
                    if (hasFocus) 0xFF2D2D2D.toInt() else android.graphics.Color.TRANSPARENT
                )
            }
        }
    }

    override fun getItemCount() = tabs.size
}

// ─── Category Adapter ─────────────────────────────────────────────────────────

class CategoryAdapter(
    private val onCategoryClick: (Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    private val items = mutableListOf<Category>()

    fun submitList(list: List<Category>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val view: TextView) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
            setPadding(32, 20, 32, 20)
            textSize = 15f
            isFocusable = true
            isFocusableInTouchMode = true
            setTextColor(0xFFCCCCCC.toInt())
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxLines = 1
        }
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val cat = items[position]
        holder.view.text = cat.name
        holder.view.setOnClickListener { onCategoryClick(cat) }
        holder.view.setOnFocusChangeListener { v, hasFocus ->
            (v as? TextView)?.setBackgroundColor(
                if (hasFocus) 0xFF1A73E8.toInt() else android.graphics.Color.TRANSPARENT
            )
        }
    }

    override fun getItemCount() = items.size
}
