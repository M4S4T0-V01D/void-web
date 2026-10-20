package dev.m4s4t0.voidweb

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** How websites are asked to look: following the app's theme, or always light or dark. */
enum class SiteLook(val label: String) { THEME("Match my theme"), LIGHT("Light"), DARK("Dark") }

/**
 * The user's settings, as Compose state (so screens update the moment a setting changes) backed by
 * SharedPreferences.
 */
object Prefs {
    private lateinit var sp: SharedPreferences

    var engine by mutableStateOf(SearchEngine.DUCKDUCKGO)
        private set
    var toolbarBottom by mutableStateOf(true)
        private set
    var restoreTabs by mutableStateOf(true)
        private set
    var strictTracking by mutableStateOf(true)
        private set
    var httpsOnly by mutableStateOf(false)
        private set
    var siteLook by mutableStateOf(SiteLook.THEME)
        private set
    var sounds by mutableStateOf(true)
        private set
    var livingBackground by mutableStateOf(true)
        private set
    var intro by mutableStateOf(true)
        private set

    /** Set once uBlock Origin has been installed, so it is not installed again after the user removes it. */
    var ublockInstalled: Boolean
        get() = sp.getBoolean("ublockInstalled", false)
        set(v) = sp.edit().putBoolean("ublockInstalled", v).apply()

    var lastAddonUpdate: Long
        get() = sp.getLong("lastAddonUpdate", 0)
        set(v) = sp.edit().putLong("lastAddonUpdate", v).apply()

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("web", Context.MODE_PRIVATE)
        engine = runCatching { SearchEngine.valueOf(sp.getString("engine", "")!!) }.getOrDefault(SearchEngine.DUCKDUCKGO)
        toolbarBottom = sp.getBoolean("toolbarBottom", true)
        restoreTabs = sp.getBoolean("restoreTabs", true)
        strictTracking = sp.getBoolean("strictTracking", true)
        httpsOnly = sp.getBoolean("httpsOnly", false)
        siteLook = runCatching { SiteLook.valueOf(sp.getString("siteLook", "")!!) }.getOrDefault(SiteLook.THEME)
        sounds = sp.getBoolean("sounds", true)
        livingBackground = sp.getBoolean("livingBackground", true)
        intro = sp.getBoolean("intro", true)
    }

    fun saveEngine(e: SearchEngine) { engine = e; sp.edit().putString("engine", e.name).apply() }
    fun saveToolbarBottom(on: Boolean) { toolbarBottom = on; sp.edit().putBoolean("toolbarBottom", on).apply() }
    fun saveRestoreTabs(on: Boolean) { restoreTabs = on; sp.edit().putBoolean("restoreTabs", on).apply() }
    fun saveSounds(on: Boolean) { sounds = on; sp.edit().putBoolean("sounds", on).apply() }
    fun saveLivingBackground(on: Boolean) { livingBackground = on; sp.edit().putBoolean("livingBackground", on).apply() }
    fun saveIntro(on: Boolean) { intro = on; sp.edit().putBoolean("intro", on).apply() }

    fun saveStrictTracking(on: Boolean) {
        strictTracking = on
        sp.edit().putBoolean("strictTracking", on).apply()
        Engine.applyPrivacy()
    }

    fun saveHttpsOnly(on: Boolean) {
        httpsOnly = on
        sp.edit().putBoolean("httpsOnly", on).apply()
        Engine.applyPrivacy()
    }

    fun saveSiteLook(l: SiteLook) {
        siteLook = l
        sp.edit().putString("siteLook", l.name).apply()
        Engine.applyLook()
    }
}
