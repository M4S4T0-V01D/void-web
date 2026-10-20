package dev.m4s4t0.voidweb

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.m4s4t0.voidweb.ui.AddonsScreen
import dev.m4s4t0.voidweb.ui.AddressEditor
import dev.m4s4t0.voidweb.ui.BookmarksScreen
import dev.m4s4t0.voidweb.ui.BrowserScreen
import dev.m4s4t0.voidweb.ui.Dialogs
import dev.m4s4t0.voidweb.ui.DownloadsScreen
import dev.m4s4t0.voidweb.ui.HistoryScreen
import dev.m4s4t0.voidweb.ui.Intro
import dev.m4s4t0.voidweb.ui.SettingsScreen
import dev.m4s4t0.voidweb.ui.TabsScreen
import dev.m4s4t0.voidweb.ui.Void
import dev.m4s4t0.voidweb.ui.WebTheme
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoView

class MainActivity : ComponentActivity(), Host {
    private lateinit var gecko: GeckoView

    private var permissionsDone: ((Boolean) -> Unit)? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionsDone?.invoke(result.isNotEmpty() && result.values.all { it })
        permissionsDone = null
    }

    private var filesDone: ((List<Uri>?) -> Unit)? = null
    private val fileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val uris = if (result.resultCode != RESULT_OK || data == null) {
            null
        } else {
            data.clipData?.let { clip -> List(clip.itemCount) { clip.getItemAt(it).uri } } ?: listOfNotNull(data.data)
        }
        filesDone?.invoke(uris)
        filesDone = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gecko = GeckoView(this).apply { coverUntilFirstPaint(Void.bg.toArgb()) }
        Browser.host = this
        Browser.view = gecko
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            // Status and navigation bar icons follow the app's theme, not the phone's light/dark setting.
            val light = Void.palette.light
            LaunchedEffect(light) {
                val style = if (light) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT) else SystemBarStyle.dark(Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            WebTheme { App(gecko, firstLaunch = savedInstanceState == null, onExit = { finish() }) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            Themes.syncLauncherIcon()
            Browser.scope.launch { Browser.saveNow() }
        }
    }

    override fun onDestroy() {
        if (Browser.view === gecko) {
            gecko.releaseSession()
            Browser.view = null
        }
        if (Browser.host === this) Browser.host = null
        super.onDestroy()
    }

    /** Links and searches from other apps (Void Web can be the default browser), and text shared to it. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val target = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_WEB_SEARCH, Intent.ACTION_SEARCH -> intent.getStringExtra(SearchManager.QUERY)
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text -> Regex("https?://\\S+").find(text)?.value ?: text }
            else -> null
        }?.takeIf { it.isNotBlank() } ?: return
        val t = Browser.current
        // Reuse an empty tab instead of piling up new ones.
        if (t == null || !Urls.isBlank(t.url) || t.private) Browser.newTab(null, private = false)
        Browser.go(target)
    }

    override fun requestPermissions(perms: Array<String>, done: (Boolean) -> Unit) {
        permissionsDone?.invoke(false)
        permissionsDone = done
        permissionLauncher.launch(perms)
    }

    override fun pickFiles(mimeTypes: Array<String>, multiple: Boolean, capture: Boolean, done: (List<Uri>?) -> Unit) {
        filesDone?.invoke(null)
        filesDone = done
        val types = mimeTypes.filter { it.contains('/') }
        val pick = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
        when (types.size) {
            0 -> pick.type = "*/*"
            1 -> pick.type = types[0]
            else -> pick.setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
        }
        try {
            fileLauncher.launch(Intent.createChooser(pick, null))
        } catch (e: ActivityNotFoundException) {
            filesDone = null
            done(null)
        }
    }

    override fun setFullscreen(on: Boolean) {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun open(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}

@Composable
private fun App(gecko: GeckoView, firstLaunch: Boolean, onExit: () -> Unit) {
    var intro by rememberSaveable { mutableStateOf(firstLaunch && Prefs.intro) }
    val snackHost = remember { SnackbarHostState() }
    BackHandler(enabled = true) { if (!Browser.back()) onExit() }
    // The last screen shown over the browser, kept while it slides away.
    var shown by remember { mutableStateOf(Screen.TABS) }
    if (Browser.screen != Screen.BROWSE) shown = Browser.screen
    Box(Modifier.fillMaxSize()) {
        BrowserScreen(gecko)
        AnimatedVisibility(
            Browser.screen != Screen.BROWSE,
            enter = slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(160)),
        ) {
            when (shown) {
                Screen.TABS -> TabsScreen()
                Screen.SETTINGS -> SettingsScreen()
                Screen.BOOKMARKS -> BookmarksScreen()
                Screen.HISTORY -> HistoryScreen()
                Screen.DOWNLOADS -> DownloadsScreen()
                Screen.ADDONS -> AddonsScreen()
                Screen.BROWSE -> Unit
            }
        }
        if (Browser.editing) AddressEditor()
        Dialogs()
        SnackbarHost(snackHost, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (Prefs.toolbarBottom && Browser.screen == Screen.BROWSE) 64.dp else 12.dp)) { data ->
            Snackbar(data, containerColor = Void.panelAlt, contentColor = Void.text, actionColor = Void.cyan, shape = RoundedCornerShape(16.dp))
        }
        if (intro) Intro { intro = false }
    }
    val snack = Browser.snack
    LaunchedEffect(snack?.id) {
        if (snack == null) return@LaunchedEffect
        snackHost.currentSnackbarData?.dismiss()
        val r = snackHost.showSnackbar(snack.text, snack.action, duration = if (snack.action != null) SnackbarDuration.Long else SnackbarDuration.Short)
        if (r == SnackbarResult.ActionPerformed) snack.onAction?.invoke()
        if (Browser.snack?.id == snack.id) Browser.snack = null
    }
}
