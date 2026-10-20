package dev.m4s4t0.voidweb

import android.content.Intent
import org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaSource
import org.mozilla.geckoview.GeckoSession.PromptDelegate.ChoicePrompt
import org.mozilla.geckoview.WebExtension

/**
 * Something the browser needs the user to answer: a page's alert or form control, a permission, a
 * long-press menu. Each one is answered exactly once: by its dialog, or by [dismiss] when the dialog is
 * closed (or the page that asked goes away).
 */
sealed class Ask {
    private var answered = false

    /** Run [block] unless this was already answered. */
    protected fun once(block: () -> Unit) {
        if (answered) return
        answered = true
        block()
    }

    abstract fun dismiss()

    class Alert(val title: String?, val message: String, private val done: () -> Unit) : Ask() {
        fun ok() = once(done)
        override fun dismiss() = once(done)
    }

    class Confirm(val title: String?, val message: String, private val done: (Boolean) -> Unit) : Ask() {
        fun answer(yes: Boolean) = once { done(yes) }
        override fun dismiss() = once { done(false) }
    }

    class TextInput(val title: String?, val message: String?, val initial: String, private val done: (String?) -> Unit) : Ask() {
        fun answer(text: String) = once { done(text) }
        override fun dismiss() = once { done(null) }
    }

    /** A <select> list (or a page menu): [multiple] lets several be picked. */
    class Choice(val title: String?, val message: String?, val choices: List<ChoicePrompt.Choice>, val multiple: Boolean, private val done: (List<ChoicePrompt.Choice>?) -> Unit) : Ask() {
        fun answer(picked: List<ChoicePrompt.Choice>) = once { done(picked) }
        override fun dismiss() = once { done(null) }
    }

    class Login(val host: String, val message: String?, val passwordOnly: Boolean, val user: String, private val done: (Pair<String, String>?) -> Unit) : Ask() {
        fun answer(user: String, pass: String) = once { done(user to pass) }
        override fun dismiss() = once { done(null) }
    }

    /** A date/time input. [kind] is one of DateTimePrompt.Type; values use the HTML formats. */
    class DateTime(val kind: Int, val initial: String, val min: String?, val max: String?, private val done: (String?) -> Unit) : Ask() {
        fun answer(value: String) = once { done(value) }
        fun clear() = once { done("") }
        override fun dismiss() = once { done(null) }
    }

    class ColorPick(val initial: String, private val done: (String?) -> Unit) : Ask() {
        fun answer(hex: String) = once { done(hex) }
        override fun dismiss() = once { done(null) }
    }

    /** A page asks to open a pop-up window, to leave with unsaved changes, or to send a form again. */
    class Allow(val title: String, val message: String, val yes: String, val no: String, private val done: (Boolean) -> Unit) : Ask() {
        fun answer(ok: Boolean) = once { done(ok) }
        override fun dismiss() = once { done(false) }
    }

    /** A site asks for location, notifications, camera, microphone and so on. */
    class Permission(val host: String, val what: String, val private: Boolean, private val done: (Boolean) -> Unit) : Ask() {
        fun answer(ok: Boolean) = once { done(ok) }
        override fun dismiss() = once { done(false) }
    }

    class Media(val host: String, val video: List<MediaSource>, val audio: List<MediaSource>, private val done: (MediaSource?, MediaSource?, Boolean) -> Unit) : Ask() {
        fun allow(v: MediaSource?, a: MediaSource?) = once { done(v, a, true) }
        override fun dismiss() = once { done(null, null, false) }
    }

    class ContextMenu(val element: ContextElement, val private: Boolean) : Ask() {
        override fun dismiss() = once { }
    }

    class OpenApp(val intent: Intent, val app: String, private val done: (Boolean) -> Unit) : Ask() {
        fun answer(ok: Boolean) = once { done(ok) }
        override fun dismiss() = once { done(false) }
    }

    class InstallAddon(val ext: WebExtension, val permissions: List<String>, private val done: (Boolean) -> Unit) : Ask() {
        fun answer(ok: Boolean) = once { done(ok) }
        override fun dismiss() = once { done(false) }
    }

    class UpdateAddon(val ext: WebExtension, val permissions: List<String>, val optional: Boolean = false, private val done: (Boolean) -> Unit) : Ask() {
        fun answer(ok: Boolean) = once { done(ok) }
        override fun dismiss() = once { done(false) }
    }

    /** "Add to bookmarks" / rename. */
    class EditBookmark(val url: String, val title: String) : Ask() {
        override fun dismiss() = once { }
    }

    class ClearData : Ask() {
        override fun dismiss() = once { }
    }
}
