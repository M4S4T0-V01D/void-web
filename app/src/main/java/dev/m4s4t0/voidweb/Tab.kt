package dev.m4s4t0.voidweb

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

/**
 * One tab: its engine session and what the interface shows about it. The session is created at once but only
 * opened (which costs a content process and memory) when the tab is first shown, so restored tabs are cheap.
 */
class Tab(
    val id: Long,
    val private: Boolean,
    /** The tab that opened this one; going back past its first page returns there. */
    val parentId: Long? = null,
    url: String = "",
    title: String = "",
    private var savedState: String? = null,
    desktop: Boolean = false,
) {
    var url by mutableStateOf(url)
    var title by mutableStateOf(title)
    var progress by mutableIntStateOf(100)
    var loading by mutableStateOf(false)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var secure by mutableStateOf(true)
    var desktop by mutableStateOf(desktop)
        private set
    var thumb by mutableStateOf<ImageBitmap?>(null)
    var crashed by mutableStateOf(false)

    /** The engine's saved history for this tab (back/forward list, scroll positions), kept for restoring. */
    var state: String? = savedState
        internal set

    /** Set when the engine opens the session itself (a page's window.open): nothing should be loaded into it. */
    var openedByEngine = false

    private var pendingUrl: String? = url.takeIf { it.isNotEmpty() }

    var session: GeckoSession = newSession()
        private set

    private fun newSession() = GeckoSession(
        GeckoSessionSettings.Builder()
            .usePrivateMode(private)
            .useTrackingProtection(true)
            .suspendMediaWhenInactive(false)
            .userAgentMode(if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .viewportMode(if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .build(),
    )

    /** Open the session if it isn't yet, then restore its history or load its first page. */
    fun ensureOpen(): GeckoSession {
        if (!session.isOpen) {
            session.open(Engine.runtime)
            val saved = savedState?.let { runCatching { GeckoSession.SessionState.fromString(it) }.getOrNull() }
            when {
                saved != null && saved.size > 0 -> session.restoreState(saved)
                pendingUrl != null -> session.loadUri(pendingUrl!!)
            }
            savedState = null
            pendingUrl = null
        }
        return session
    }

    fun load(target: String) {
        if (session.isOpen) session.loadUri(target) else pendingUrl = target.also { savedState = null }
        url = target
    }

    fun toggleDesktop() {
        desktop = !desktop
        session.settings.setUserAgentMode(if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
        session.settings.setViewportMode(if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
        if (session.isOpen && !Urls.isBlank(url)) session.reload()
    }

    /** After a content-process crash the session is dead: start a fresh one on the same page. */
    fun revive(attach: (GeckoSession) -> Unit) {
        runCatching { session.close() }
        session = newSession()
        attach(session)
        crashed = false
        pendingUrl = url.takeIf { !Urls.isBlank(it) }
        savedState = state
    }

    fun close() {
        runCatching { if (session.isOpen) session.close() }
    }
}
