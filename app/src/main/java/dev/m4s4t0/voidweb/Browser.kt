package dev.m4s4t0.voidweb

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.ContentDelegate
import org.mozilla.geckoview.GeckoSession.HistoryDelegate
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import org.mozilla.geckoview.GeckoSession.ProgressDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate.BasePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse
import java.io.File

enum class Screen { BROWSE, TABS, SETTINGS, BOOKMARKS, HISTORY, DOWNLOADS, ADDONS }

data class Snack(val id: Long, val text: String, val action: String? = null, val onAction: (() -> Unit)? = null)

/** What the browser needs from the activity on screen: Android permission and file pickers, full screen. */
interface Host {
    fun requestPermissions(perms: Array<String>, done: (Boolean) -> Unit)
    fun pickFiles(mimeTypes: Array<String>, multiple: Boolean, capture: Boolean, done: (List<Uri>?) -> Unit)
    fun setFullscreen(on: Boolean)
    fun open(intent: Intent): Boolean
}

/**
 * The browser itself: the tabs, which one is showing, and everything the engine asks of the interface. It
 * lives as long as the app (not the activity), so tabs and downloads carry on through rotation.
 */
object Browser {
    private lateinit var app: Context
    val scope = MainScope()

    val tabs = mutableStateListOf<Tab>()
    var current by mutableStateOf<Tab?>(null)
        private set
    var screen by mutableStateOf(Screen.BROWSE)
    /** The address bar is being typed in. */
    var editing by mutableStateOf(false)
    val asks = mutableStateListOf<Ask>()
    var fullscreen by mutableStateOf(false)
        private set
    var snack by mutableStateOf<Snack?>(null)
    var findOpen by mutableStateOf(false)
    var findResult by mutableStateOf<GeckoSession.FinderResult?>(null)
    var host: Host? = null

    /** The one GeckoView on screen; tabs take turns in it. */
    var view: GeckoView? = null
        set(v) {
            field = v
            current?.let { show(it) }
        }

    private var nextId = 1L
    private var snackId = 1L
    private val tabsFile get() = File(app.filesDir, "tabs.json")

    fun init(ctx: Context) {
        app = ctx.applicationContext
        restore()
        if (tabs.isEmpty()) newTab(null, private = false, sound = false)
    }

    // --- Tabs -------------------------------------------------------------------------------------------

    fun newTab(url: String?, private: Boolean, select: Boolean = true, parent: Tab? = null, openedByEngine: Boolean = false, sound: Boolean = true, state: String? = null): Tab {
        val tab = Tab(nextId++, private, parent?.id, url ?: "", savedState = state)
        tab.openedByEngine = openedByEngine
        attach(tab, tab.session)
        // A tab opened from a page sits right after it (and after the other tabs it already opened).
        val at = parent?.let { p ->
            var i = tabs.indexOf(p) + 1
            while (i < tabs.size && tabs[i].parentId == p.id) i++
            i
        } ?: tabs.size
        tabs.add(at.coerceIn(0, tabs.size), tab)
        if (select) select(tab)
        if (sound) Sounds.play(Sounds.Kind.TAB_OPEN)
        saveSoon()
        return tab
    }

    fun select(tab: Tab) {
        if (current !== tab) captureThumb(current)
        current = tab
        screen = Screen.BROWSE
        if (findOpen) closeFind()
        show(tab)
        saveSoon()
    }

    /** Put [tab]'s session in the GeckoView (opening it first if needed); the others go inactive. */
    private fun show(tab: Tab) {
        val v = view ?: return
        if (v.session === tab.session) return
        if (!tab.openedByEngine) tab.ensureOpen()
        // A window.open() tab is opened by the engine just after we hand its session over; wait for that.
        if (!tab.session.isOpen) {
            v.postDelayed({ if (current === tab) show(tab) }, 16)
            return
        }
        v.session?.setActive(false)
        v.releaseSession()
        v.setSession(tab.session)
        tab.session.setActive(true)
    }

    fun close(tab: Tab, undo: Boolean = true) {
        val i = tabs.indexOf(tab)
        if (i < 0) return
        tabs.removeAt(i)
        if (view?.session === tab.session) view?.releaseSession()
        tab.close()
        Sounds.play(Sounds.Kind.TAB_CLOSE)
        if (current === tab) {
            current = null
            val next = tabs.firstOrNull { it.id == tab.parentId }
                ?: tabs.filter { it.private == tab.private }.let { same -> same.getOrNull(same.indexOfFirst { tabs.indexOf(it) >= i }.takeIf { it >= 0 } ?: same.lastIndex) }
                ?: tabs.lastOrNull()
            if (next != null) select(next) else newTab(null, private = false, sound = false)
        }
        if (undo && !Urls.isBlank(tab.url)) {
            toast(if (tab.private) "Private tab closed" else "Tab closed", "Undo") {
                val back = newTab(null, tab.private, select = true, sound = false, state = tab.state)
                if (tab.state == null) back.load(tab.url)
            }
        }
        saveSoon()
    }

    fun closeAll(private: Boolean) {
        val gone = tabs.filter { it.private == private }
        if (gone.isEmpty()) return
        val keepCurrent = current?.private != private
        for (t in gone) {
            tabs.remove(t)
            if (view?.session === t.session) view?.releaseSession()
            t.close()
        }
        Sounds.play(Sounds.Kind.TAB_CLOSE)
        if (!keepCurrent) {
            current = null
            tabs.lastOrNull()?.let { select(it) } ?: newTab(null, private = false, sound = false)
        }
        saveSoon()
    }

    // --- Navigation -------------------------------------------------------------------------------------

    /** Open what was typed in the address bar: an address, or a search. */
    fun go(input: String) {
        editing = false
        val target = Urls.fromInput(input, Prefs.engine)
        if (!Urls.isWebScheme(target)) {
            openExternal(target, userGesture = true)
            return
        }
        val tab = current ?: newTab(null, private = false)
        tab.load(target)
        show(tab)
    }

    fun reloadOrStop() {
        val t = current ?: return
        if (t.loading) t.session.stop() else t.session.reload()
    }

    /** System back: leave full screen, close panels, go back in the page, or return to the tab that opened this one. */
    fun back(): Boolean {
        val t = current
        when {
            fullscreen -> t?.session?.exitFullScreen()
            editing -> editing = false
            findOpen -> closeFind()
            screen != Screen.BROWSE -> screen = Screen.BROWSE
            t != null && t.canBack -> t.session.goBack()
            t != null && t.parentId != null && tabs.any { it.id == t.parentId } -> close(t, undo = false)
            else -> return false
        }
        return true
    }

    fun find(query: String?, backwards: Boolean = false) {
        val s = current?.session ?: return
        if (query != null && query.isEmpty()) {
            s.finder.clear()
            findResult = null
            return
        }
        s.finder.displayFlags = GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL
        s.finder.find(query, if (backwards) GeckoSession.FINDER_FIND_BACKWARDS else GeckoSession.FINDER_FIND_FORWARD).accept({ findResult = it }, { })
    }

    fun closeFind() {
        findOpen = false
        current?.session?.finder?.clear()
        findResult = null
    }

    /** A small picture of the page for the tab switcher. */
    fun captureThumb(tab: Tab?) {
        val v = view ?: return
        if (tab == null || v.session !== tab.session || Urls.isBlank(tab.url)) return
        v.capturePixels().accept({ bmp: Bitmap? ->
            if (bmp != null && bmp.width > 0) {
                val w = 360
                tab.thumb = bmp.scale(w, (bmp.height * w / bmp.width).coerceAtLeast(1)).asImageBitmap()
                bmp.recycle()
            }
        }, { })
    }

    // --- Messages and dialogs ---------------------------------------------------------------------------

    fun ask(a: Ask) {
        asks += a
    }

    fun answered(a: Ask) {
        asks.remove(a)
    }

    fun toast(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
        snack = Snack(snackId++, text, action, onAction)
    }

    fun copy(text: String, label: String = "Link") {
        app.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun share(text: String, subject: String? = null) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        if (subject != null) send.putExtra(Intent.EXTRA_SUBJECT, subject)
        host?.open(Intent.createChooser(send, null))
    }

    /** "Open in another app": Android's chooser, for the page's address. */
    fun openWith(url: String) {
        host?.open(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE), "Open with"))
    }

    /**
     * A link for another app (mailto:, tel:, intent:, market: …). Only followed after a tap, and only once the
     * user agrees; an intent: link that no app can open falls back to its web address when it has one.
     */
    fun openExternal(uri: String, userGesture: Boolean) {
        val intent = if (uri.startsWith("intent:")) {
            runCatching { Intent.parseUri(uri, Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        }
        // Pages may only start ordinary activities: no explicit components or selectors.
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.component = null
        intent.selector = null
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = intent.getStringExtra("browser_fallback_url")?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        if (!userGesture) {
            if (fallback != null) current?.load(fallback)
            return
        }
        val what = when (Urls.scheme(uri)) {
            "mailto" -> "your email app"
            "tel" -> "the phone app"
            "sms", "smsto" -> "your messages app"
            "geo" -> "your maps app"
            "market" -> "the app store"
            else -> "another app"
        }
        ask(Ask.OpenApp(intent, what) { ok ->
            if (ok && host?.open(intent) != true) {
                if (fallback != null) current?.load(fallback) else toast("No app on this phone can open that link")
            }
        })
    }

    // --- Engine delegates -------------------------------------------------------------------------------

    private fun hostOf(uri: String?) = uri?.let { Urls.displayHost(it) } ?: "This site"

    fun attach(tab: Tab, s: GeckoSession) {
        s.navigationDelegate = object : NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, url: String?, perms: List<ContentPermission>, hasUserGesture: Boolean) {
                tab.url = url ?: ""
                if (tab === current && findOpen) closeFind()
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { tab.canBack = canGoBack }
            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) { tab.canForward = canGoForward }

            override fun onLoadRequest(session: GeckoSession, request: NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> {
                if (Urls.isWebScheme(request.uri)) return GeckoResult.allow()
                openExternal(request.uri, request.hasUserGesture)
                return GeckoResult.deny()
            }

            override fun onSubframeLoadRequest(session: GeckoSession, request: NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> =
                if (Urls.isWebScheme(request.uri)) GeckoResult.allow() else GeckoResult.deny()

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession> {
                val t = newTab(null, tab.private, select = true, parent = tab, openedByEngine = true)
                t.url = uri
                return GeckoResult.fromValue(t.session)
            }

            override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String> =
                GeckoResult.fromValue(ErrorPage.dataUri(uri ?: "", error))
        }

        s.progressDelegate = object : ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                tab.loading = true
                tab.progress = 5
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                tab.loading = false
                tab.progress = 100
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) { tab.progress = progress }

            override fun onSecurityChange(session: GeckoSession, info: ProgressDelegate.SecurityInformation) {
                tab.secure = info.isSecure
            }

            override fun onSessionStateChange(session: GeckoSession, state: GeckoSession.SessionState) {
                tab.state = state.toString()
                saveSoon()
            }
        }

        s.contentDelegate = object : ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                tab.title = title ?: ""
                if (!tab.private && title != null) Store.setTitle(tab.url, title)
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                fullscreen = fullScreen
                host?.setFullscreen(fullScreen)
            }

            override fun onContextMenu(session: GeckoSession, screenX: Int, screenY: Int, element: ContentDelegate.ContextElement) {
                if (element.linkUri == null && element.srcUri == null) return
                ask(Ask.ContextMenu(element, tab.private))
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                Downloads.start(app, response, tab.private)
            }

            override fun onCloseRequest(session: GeckoSession) = close(tab, undo = false)

            override fun onCrash(session: GeckoSession) {
                Log.w("VoidWeb", "Content process crashed: ${tab.url}")
                recover(tab)
                toast("The page crashed and was reloaded")
            }

            // Android stopped the page's process to free memory; bring it back quietly.
            override fun onKill(session: GeckoSession) = recover(tab)
        }

        s.promptDelegate = Prompts(tab)
        s.permissionDelegate = Permissions(tab)

        s.historyDelegate = object : HistoryDelegate {
            override fun onVisited(session: GeckoSession, url: String, lastVisitedURL: String?, flags: Int): GeckoResult<Boolean> {
                val skip = HistoryDelegate.VISIT_REDIRECT_SOURCE or HistoryDelegate.VISIT_REDIRECT_SOURCE_PERMANENT or HistoryDelegate.VISIT_UNRECOVERABLE_ERROR
                if (tab.private || flags and HistoryDelegate.VISIT_TOP_LEVEL == 0 || flags and skip != 0) return GeckoResult.fromValue(false)
                Store.addVisit(url)
                if (tab.title.isNotBlank()) Store.setTitle(url, tab.title)
                return GeckoResult.fromValue(true)
            }

            override fun getVisited(session: GeckoSession, urls: Array<String>): GeckoResult<BooleanArray> =
                GeckoResult.fromValue(if (tab.private) BooleanArray(urls.size) else Store.visited(urls))
        }
    }

    private fun recover(tab: Tab) {
        val showing = view?.session === tab.session
        if (showing) view?.releaseSession()
        tab.revive { attach(tab, it) }
        if (showing || current === tab) show(tab)
    }

    /** Dialogs a page opens. Each becomes an [Ask]; when the engine withdraws one, its dialog goes too. */
    private class Prompts(val tab: Tab) : PromptDelegate {
        private fun <P : BasePrompt> prompt(p: P, make: (GeckoResult<PromptResponse>) -> Ask): GeckoResult<PromptResponse> {
            val result = GeckoResult<PromptResponse>()
            val a = make(result)
            p.delegate = object : PromptDelegate.PromptInstanceDelegate {
                override fun onPromptDismiss(prompt: BasePrompt) = scopeRemove(a)
            }
            ask(a)
            return result
        }

        private fun scopeRemove(a: Ask) {
            scope.launch { answered(a) }
        }

        override fun onAlertPrompt(session: GeckoSession, prompt: PromptDelegate.AlertPrompt) =
            prompt(prompt) { r -> Ask.Alert(prompt.title, prompt.message ?: "") { r.complete(prompt.dismiss()) } }

        override fun onButtonPrompt(session: GeckoSession, prompt: PromptDelegate.ButtonPrompt) =
            prompt(prompt) { r ->
                Ask.Confirm(prompt.title, prompt.message ?: "") { yes ->
                    r.complete(prompt.confirm(if (yes) PromptDelegate.ButtonPrompt.Type.POSITIVE else PromptDelegate.ButtonPrompt.Type.NEGATIVE))
                }
            }

        override fun onTextPrompt(session: GeckoSession, prompt: PromptDelegate.TextPrompt) =
            prompt(prompt) { r -> Ask.TextInput(prompt.title, prompt.message, prompt.defaultValue ?: "") { text -> r.complete(if (text == null) prompt.dismiss() else prompt.confirm(text)) } }

        override fun onChoicePrompt(session: GeckoSession, prompt: PromptDelegate.ChoicePrompt) =
            prompt(prompt) { r ->
                val multiple = prompt.type == PromptDelegate.ChoicePrompt.Type.MULTIPLE
                Ask.Choice(prompt.title, prompt.message, prompt.choices.toList(), multiple) { picked ->
                    r.complete(
                        when {
                            picked == null -> prompt.dismiss()
                            multiple -> prompt.confirm(picked.toTypedArray())
                            picked.isEmpty() -> prompt.dismiss()
                            else -> prompt.confirm(picked.first())
                        },
                    )
                }
            }

        override fun onAuthPrompt(session: GeckoSession, prompt: PromptDelegate.AuthPrompt) =
            prompt(prompt) { r ->
                val o = prompt.authOptions
                val passwordOnly = o.flags and PromptDelegate.AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD != 0
                Ask.Login(hostOf(o.uri), prompt.message, passwordOnly, o.username ?: "") { creds ->
                    r.complete(
                        when {
                            creds == null -> prompt.dismiss()
                            passwordOnly -> prompt.confirm(creds.second)
                            else -> prompt.confirm(creds.first, creds.second)
                        },
                    )
                }
            }

        override fun onDateTimePrompt(session: GeckoSession, prompt: PromptDelegate.DateTimePrompt) =
            prompt(prompt) { r -> Ask.DateTime(prompt.type, prompt.defaultValue ?: "", prompt.minValue, prompt.maxValue) { v -> r.complete(if (v == null) prompt.dismiss() else prompt.confirm(v)) } }

        override fun onColorPrompt(session: GeckoSession, prompt: PromptDelegate.ColorPrompt) =
            prompt(prompt) { r -> Ask.ColorPick(prompt.defaultValue ?: "#000000") { v -> r.complete(if (v == null) prompt.dismiss() else prompt.confirm(v)) } }

        override fun onPopupPrompt(session: GeckoSession, prompt: PromptDelegate.PopupPrompt) =
            prompt(prompt) { r ->
                Ask.Allow("Open a pop-up?", "${hostOf(tab.url)} wants to open ${prompt.targetUri?.let { Urls.displayHost(it) } ?: "a new window"}.", "Open", "Block") { ok ->
                    r.complete(prompt.confirm(if (ok) AllowOrDeny.ALLOW else AllowOrDeny.DENY))
                }
            }

        override fun onBeforeUnloadPrompt(session: GeckoSession, prompt: PromptDelegate.BeforeUnloadPrompt) =
            prompt(prompt) { r ->
                Ask.Allow("Leave this page?", "Changes you made may not be saved.", "Leave", "Stay") { ok -> r.complete(prompt.confirm(if (ok) AllowOrDeny.ALLOW else AllowOrDeny.DENY)) }
            }

        override fun onRepostConfirmPrompt(session: GeckoSession, prompt: PromptDelegate.RepostConfirmPrompt) =
            prompt(prompt) { r ->
                Ask.Allow("Send the form again?", "Reloading sends what you entered again (a purchase or a post could happen twice).", "Send again", "Cancel") { ok ->
                    r.complete(prompt.confirm(if (ok) AllowOrDeny.ALLOW else AllowOrDeny.DENY))
                }
            }

        override fun onFilePrompt(session: GeckoSession, prompt: PromptDelegate.FilePrompt): GeckoResult<PromptResponse> {
            val result = GeckoResult<PromptResponse>()
            val h = host
            if (h == null || prompt.type == PromptDelegate.FilePrompt.Type.FOLDER) {
                result.complete(prompt.dismiss())
                return result
            }
            val multiple = prompt.type == PromptDelegate.FilePrompt.Type.MULTIPLE
            h.pickFiles(prompt.mimeTypes ?: emptyArray(), multiple, prompt.capture != PromptDelegate.FilePrompt.Capture.NONE) { uris ->
                result.complete(
                    when {
                        uris.isNullOrEmpty() -> prompt.dismiss()
                        multiple -> prompt.confirm(app, uris.toTypedArray())
                        else -> prompt.confirm(app, uris[0])
                    },
                )
            }
            return result
        }

        override fun onSharePrompt(session: GeckoSession, prompt: PromptDelegate.SharePrompt): GeckoResult<PromptResponse> {
            val text = listOfNotNull(prompt.text, prompt.uri).joinToString("\n")
            share(text, prompt.title)
            return GeckoResult.fromValue(prompt.confirm(PromptDelegate.SharePrompt.Result.SUCCESS))
        }
    }

    /** Sites asking for location, camera and so on; answers are remembered per site (not in private tabs). */
    private class Permissions(val tab: Tab) : PermissionDelegate {
        override fun onAndroidPermissionsRequest(session: GeckoSession, permissions: Array<String>?, callback: PermissionDelegate.Callback) {
            val h = host
            if (h == null || permissions.isNullOrEmpty()) {
                callback.reject()
                return
            }
            h.requestPermissions(permissions) { ok -> if (ok) callback.grant() else callback.reject() }
        }

        override fun onContentPermissionRequest(session: GeckoSession, perm: ContentPermission): GeckoResult<Int> {
            val what = when (perm.permission) {
                PermissionDelegate.PERMISSION_GEOLOCATION -> "know your location"
                PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> "keep its data on this phone permanently"
                PermissionDelegate.PERMISSION_XR -> "use virtual reality"
                PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> "play protected (DRM) video"
                PermissionDelegate.PERMISSION_STORAGE_ACCESS -> "use its cookies on ${perm.thirdPartyOrigin?.let { Urls.displayHost(it) } ?: "another site"}"
                PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS, PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS -> "connect to devices on your network"
                // Silent video may autoplay; sound waits for a tap. Notifications aren't shown by this app yet.
                PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE -> return GeckoResult.fromValue(ContentPermission.VALUE_ALLOW)
                else -> return GeckoResult.fromValue(ContentPermission.VALUE_DENY)
            }
            val result = GeckoResult<Int>()
            ask(Ask.Permission(hostOf(perm.uri), what, perm.privateMode) { ok ->
                val value = if (ok) ContentPermission.VALUE_ALLOW else ContentPermission.VALUE_DENY
                if (!perm.privateMode) Engine.runtime.storageController.setPermission(perm, value)
                result.complete(value)
            })
            return result
        }

        override fun onMediaPermissionRequest(session: GeckoSession, uri: String, video: Array<PermissionDelegate.MediaSource>?, audio: Array<PermissionDelegate.MediaSource>?, callback: PermissionDelegate.MediaCallback) {
            ask(Ask.Media(hostOf(uri), video?.toList() ?: emptyList(), audio?.toList() ?: emptyList()) { v, a, ok ->
                if (ok && (v != null || a != null)) callback.grant(v, a) else callback.reject()
            })
        }
    }

    // --- Saving tabs ------------------------------------------------------------------------------------

    private var saveJob: Job? = null

    fun saveSoon() {
        if (!::app.isInitialized) return
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(800)
            saveNow()
        }
    }

    /** Ordinary tabs (never private ones) with their history, so they come back after a restart. */
    suspend fun saveNow() {
        if (!Prefs.restoreTabs) {
            withContext(Dispatchers.IO) { tabsFile.delete() }
            return
        }
        val keep = tabs.filter { !it.private }
        val json = JSONObject()
            .put("selected", keep.indexOf(current).coerceAtLeast(0))
            .put("tabs", JSONArray(keep.map { t -> JSONObject().put("url", t.url).put("title", t.title).put("state", t.state ?: "").put("desktop", t.desktop) }))
            .toString()
        withContext(Dispatchers.IO) {
            val tmp = File(tabsFile.path + ".tmp")
            tmp.writeText(json)
            tmp.renameTo(tabsFile)
        }
    }

    private fun restore() {
        if (!Prefs.restoreTabs || !tabsFile.exists()) return
        try {
            val j = JSONObject(tabsFile.readText())
            val list = j.getJSONArray("tabs")
            for (i in 0 until list.length()) {
                val o = list.getJSONObject(i)
                val t = Tab(nextId++, false, null, o.optString("url"), o.optString("title"), o.optString("state").ifEmpty { null }, o.optBoolean("desktop"))
                attach(t, t.session)
                tabs += t
            }
            current = tabs.getOrNull(j.optInt("selected")) ?: tabs.lastOrNull()
        } catch (e: Exception) {
            Log.w("VoidWeb", "Could not restore tabs", e)
        }
    }

    fun clearSavedTabs() {
        tabsFile.delete()
    }

    fun startActivitySafely(intent: Intent): Boolean = try {
        app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
