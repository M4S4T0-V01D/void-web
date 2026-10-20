package dev.m4s4t0.voidweb

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import org.mozilla.geckoview.WebResponse

class Download(val id: Long, val name: String, val mime: String, val total: Long, val from: String) {
    enum class State { RUNNING, DONE, FAILED, CANCELLED }

    var done by mutableLongStateOf(0L)
    var state by mutableStateOf(State.RUNNING)
    var uri: Uri? = null
    internal var job: Job? = null

    val fraction get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f
}

/**
 * Files the engine hands over (links it can't show, "Save image"), written to the phone's Downloads folder.
 * The list lasts while the app runs; the files stay in Downloads.
 */
object Downloads {
    val items = mutableStateListOf<Download>()
    private var nextId = 1L
    private var executor: GeckoWebExecutor? = null

    private fun header(r: WebResponse, name: String) = r.headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    fun start(ctx: Context, response: WebResponse, private: Boolean) {
        val mime = header(response, "Content-Type")?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val body = response.body
        if (mime == "application/x-xpinstall") {
            // An add-on file (from a site other than addons.mozilla.org): install it, with the usual prompt.
            body?.close()
            Engine.install(response.uri) { ok -> if (!ok) Browser.toast("That add-on couldn't be installed") }
            return
        }
        if (body == null) {
            Browser.toast("There was nothing to download")
            return
        }
        val name = Urls.fileName(response.uri, header(response, "Content-Disposition"), mime)
        val total = header(response, "Content-Length")?.toLongOrNull() ?: -1L
        val d = Download(nextId++, name, mime ?: "application/octet-stream", total, response.uri)
        items.add(0, d)
        Browser.toast("Downloading $name", "Show") { Browser.screen = Screen.DOWNLOADS }
        val resolver = ctx.applicationContext.contentResolver
        d.job = Browser.scope.launch {
            val ok = withContext(Dispatchers.IO) {
                var uri: Uri? = null
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, d.mime)
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("Downloads folder unavailable")
                    resolver.openOutputStream(uri)!!.use { out ->
                        body.use { input ->
                            val buf = ByteArray(64 * 1024)
                            var count = 0L
                            var shown = 0L
                            while (isActive) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                count += n
                                if (count - shown > 256 * 1024) {
                                    shown = count
                                    withContext(Dispatchers.Main) { d.done = count }
                                }
                            }
                            withContext(Dispatchers.Main) { d.done = count }
                        }
                    }
                    if (!isActive) throw InterruptedException()
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                    d.uri = uri
                    true
                } catch (e: Exception) {
                    Log.w("VoidWeb", "Download failed: $name", e)
                    uri?.let { runCatching { resolver.delete(it, null, null) } }
                    false
                }
            }
            if (d.state == Download.State.CANCELLED) return@launch
            d.state = if (ok) Download.State.DONE else Download.State.FAILED
            if (ok) {
                Sounds.play(Sounds.Kind.DONE)
                Browser.toast("$name downloaded", "Open") { open(ctx, d) }
            } else {
                Browser.toast("$name couldn't be downloaded")
            }
        }
    }

    /** Download a link or image the page shows (long-press "Download"), with the page's cookies unless private. */
    fun fetch(ctx: Context, url: String, private: Boolean, referrer: String?) {
        val ex = executor ?: GeckoWebExecutor(Engine.runtime).also { executor = it }
        val req = WebRequest.Builder(url).apply { if (referrer != null && !Urls.isBlank(referrer)) referrer(referrer) }.build()
        ex.fetch(req, if (private) GeckoWebExecutor.FETCH_FLAGS_PRIVATE else GeckoWebExecutor.FETCH_FLAGS_NONE)
            .accept({ r -> if (r != null) start(ctx, r, private) }, { Browser.toast("That couldn't be downloaded") })
    }

    fun cancel(d: Download) {
        d.state = Download.State.CANCELLED
        d.job?.cancel()
    }

    fun remove(d: Download) {
        if (d.state == Download.State.RUNNING) cancel(d)
        items.remove(d)
    }

    fun open(ctx: Context, d: Download) {
        val uri = d.uri ?: return
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, d.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (!Browser.startActivitySafely(Intent.createChooser(view, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))) Browser.toast("No app on this phone opens ${d.name}")
    }

    fun openFolder() {
        if (!Browser.startActivitySafely(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS))) Browser.toast("No app shows the Downloads folder")
    }
}
