package com.nodecasttv.app.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.fragment.app.FragmentActivity
import com.nodecasttv.app.App
import com.nodecasttv.app.R
import com.nodecasttv.app.api.StalkerApi
import kotlinx.coroutines.*

class SetupActivity : FragmentActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentMode = "xtream"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (App.hasCredentials()) { launchBrowse(); return }

        setContentView(R.layout.activity_setup)

        val btnXtream    = findViewById<Button>(R.id.btn_mode_xtream)
        val btnStalker   = findViewById<Button>(R.id.btn_mode_stalker)
        val groupXtream  = findViewById<LinearLayout>(R.id.group_xtream)
        val groupStalker = findViewById<LinearLayout>(R.id.group_stalker)
        val etServer     = findViewById<EditText>(R.id.et_server)
        val etUser       = findViewById<EditText>(R.id.et_username)
        val etPass       = findViewById<EditText>(R.id.et_password)
        val etPortal     = findViewById<EditText>(R.id.et_portal_url)
        val etMac        = findViewById<EditText>(R.id.et_mac)
        val btnConnect   = findViewById<Button>(R.id.btn_connect)
        val tvStatus     = findViewById<TextView>(R.id.tv_status)
        val progress     = findViewById<ProgressBar>(R.id.progress)

        App.prefs().also { p ->
            etServer.setText(p.getString("server_url", ""))
            etUser.setText(p.getString("username", ""))
            etPass.setText(p.getString("password", ""))
            etPortal.setText(p.getString("stalker_url", ""))
            etMac.setText(p.getString("stalker_mac", ""))
        }

        fun selectMode(mode: String) {
            currentMode = mode
            val blue = ColorStateList.valueOf(0xFF1A73E8.toInt())
            val grey = ColorStateList.valueOf(0xFF2D2D2D.toInt())
            if (mode == "xtream") {
                groupXtream.visibility  = View.VISIBLE
                groupStalker.visibility = View.GONE
                btnXtream.backgroundTintList  = blue
                btnStalker.backgroundTintList = grey
            } else {
                groupXtream.visibility  = View.GONE
                groupStalker.visibility = View.VISIBLE
                btnStalker.backgroundTintList = blue
                btnXtream.backgroundTintList  = grey
            }
        }

        btnXtream.setOnClickListener  { selectMode("xtream")  }
        btnStalker.setOnClickListener { selectMode("stalker") }
        selectMode("xtream")

        val connectAction = View.OnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                btnConnect.performClick(); true
            } else false
        }
        etPass.setOnKeyListener(connectAction)
        etMac.setOnKeyListener(connectAction)

        btnConnect.setOnClickListener {
            tvStatus.visibility = View.VISIBLE
            progress.visibility = View.VISIBLE
            btnConnect.isEnabled = false
            if (currentMode == "xtream")
                connectXtream(etServer, etUser, etPass, progress, tvStatus, btnConnect)
            else
                connectStalker(etPortal, etMac, progress, tvStatus, btnConnect)
        }
    }

    private fun connectXtream(
        etServer: EditText, etUser: EditText, etPass: EditText,
        progress: ProgressBar, tvStatus: TextView, btnConnect: Button
    ) {
        val server = etServer.text.toString().trim()
        val user   = etUser.text.toString().trim()
        val pass   = etPass.text.toString().trim()

        if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            tvStatus.text = "⚠ Preencha todos os campos"
            progress.visibility = View.GONE; btnConnect.isEnabled = true; return
        }
        val serverUrl = if (!server.startsWith("http")) "http://$server" else server
        tvStatus.text = "Verificando credenciais Xtream…"

        scope.launch {
            try {
                val api  = com.nodecasttv.app.api.XtreamApi(serverUrl, user, pass)
                val info = api.authenticate()
                if (info.userInfo != null) {
                    App.saveCredentials(serverUrl, user, pass); launchBrowse()
                } else {
                    tvStatus.text = "✗ Credenciais inválidas"
                    progress.visibility = View.GONE; btnConnect.isEnabled = true
                }
            } catch (e: Exception) {
                tvStatus.text = "✗ Erro: ${e.message}"
                progress.visibility = View.GONE; btnConnect.isEnabled = true
            }
        }
    }

    private fun connectStalker(
        etPortal: EditText, etMac: EditText,
        progress: ProgressBar, tvStatus: TextView, btnConnect: Button
    ) {
        val portalUrl = etPortal.text.toString().trim()
        val mac       = normalizeMac(etMac.text.toString().trim())

        if (portalUrl.isEmpty()) {
            tvStatus.text = "⚠ Preencha a URL do portal"
            progress.visibility = View.GONE; btnConnect.isEnabled = true; return
        }
        if (mac == "invalid") {
            tvStatus.text = "⚠ MAC inválido (ex: 00:1A:79:XX:XX:XX)"
            progress.visibility = View.GONE; btnConnect.isEnabled = true; return
        }
        tvStatus.text = "Conectando ao portal MAG/Stalker…"

        scope.launch {
            try {
                val api = StalkerApi(portalUrl, mac)
                val ok  = api.authenticate()
                if (ok) {
                    App.saveStalkerCredentials(portalUrl, mac); launchBrowse()
                } else {
                    tvStatus.text = "✗ Portal recusou a conexão"
                    progress.visibility = View.GONE; btnConnect.isEnabled = true
                }
            } catch (e: Exception) {
                tvStatus.text = "✗ Erro: ${e.message}"
                progress.visibility = View.GONE; btnConnect.isEnabled = true
            }
        }
    }

    private fun normalizeMac(raw: String): String {
        val hex = raw.replace(Regex("[^a-fA-F0-9]"), "")
        if (hex.length != 12) return "invalid"
        return hex.chunked(2).joinToString(":").uppercase()
    }

    private fun launchBrowse() {
        startActivity(Intent(this, BrowseActivity::class.java)); finish()
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
