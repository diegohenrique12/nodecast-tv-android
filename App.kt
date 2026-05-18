package com.nodecasttv.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.nodecasttv.app.api.StalkerApi
import com.nodecasttv.app.api.XtreamApi

class App : Application() {

    companion object {
        private lateinit var instance: App

        fun prefs(): SharedPreferences =
            instance.getSharedPreferences("nodecast_prefs", Context.MODE_PRIVATE)

        // ─── Xtream ───────────────────────────────────────────────────────────

        fun getApi(): XtreamApi? {
            if (getPortalMode() != "xtream") return null
            val p = prefs()
            val server = p.getString("server_url", "") ?: ""
            val user   = p.getString("username", "")   ?: ""
            val pass   = p.getString("password", "")   ?: ""
            if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) return null
            return XtreamApi(server, user, pass)
        }

        fun saveCredentials(server: String, username: String, password: String) {
            prefs().edit()
                .putString("portal_mode", "xtream")
                .putString("server_url", server)
                .putString("username", username)
                .putString("password", password)
                .apply()
        }

        // ─── Stalker / MAG ────────────────────────────────────────────────────

        fun getStalkerApi(): StalkerApi? {
            if (getPortalMode() != "stalker") return null
            val p   = prefs()
            val url = p.getString("stalker_url", "") ?: ""
            val mac = p.getString("stalker_mac", "") ?: ""
            if (url.isEmpty() || mac.isEmpty()) return null
            return StalkerApi(url, mac)
        }

        fun saveStalkerCredentials(portalUrl: String, mac: String) {
            prefs().edit()
                .putString("portal_mode", "stalker")
                .putString("stalker_url", portalUrl)
                .putString("stalker_mac", mac)
                .apply()
        }

        // ─── Genérico ─────────────────────────────────────────────────────────

        fun getPortalMode(): String = prefs().getString("portal_mode", "xtream") ?: "xtream"

        fun clearCredentials() = prefs().edit().clear().apply()

        fun hasCredentials(): Boolean {
            val p = prefs()
            return when (p.getString("portal_mode", "")) {
                "xtream"  -> listOf("server_url", "username", "password")
                    .all { p.getString(it, "")?.isNotEmpty() == true }
                "stalker" -> listOf("stalker_url", "stalker_mac")
                    .all { p.getString(it, "")?.isNotEmpty() == true }
                else -> false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
