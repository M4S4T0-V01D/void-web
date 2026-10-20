package dev.m4s4t0.voidweb

import android.util.Base64
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.m4s4t0.voidweb.ui.Void
import org.mozilla.geckoview.WebRequestError

/** The page shown when a site can't load, in the current theme's colours. */
object ErrorPage {
    private fun css(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** A title and a hint for the error. */
    fun describe(code: Int): Pair<String, String> = when (code) {
        WebRequestError.ERROR_UNKNOWN_HOST -> "Can't find this site" to "Check the address for typos, and that you're connected to the internet."
        WebRequestError.ERROR_OFFLINE -> "You're offline" to "Connect to Wi-Fi or mobile data, then try again."
        WebRequestError.ERROR_CONNECTION_REFUSED -> "The site refused to connect" to "It may be down for a moment, or not accept visitors at this address."
        WebRequestError.ERROR_NET_TIMEOUT -> "The site took too long to answer" to "It may be busy or down. Try again in a little while."
        WebRequestError.ERROR_NET_RESET, WebRequestError.ERROR_NET_INTERRUPT -> "The connection was interrupted" to "The network dropped while the page was loading. Try again."
        WebRequestError.ERROR_SECURITY_BAD_CERT, WebRequestError.ERROR_SECURITY_SSL, WebRequestError.ERROR_BAD_HSTS_CERT ->
            "This connection isn't safe" to "The site's security certificate can't be trusted. Someone may be pretending to be this site, so Void Web stopped before sending anything."
        WebRequestError.ERROR_HTTPS_ONLY -> "No secure version of this site" to "HTTPS-only mode is on (Settings › Privacy) and this site only offers an unencrypted connection."
        WebRequestError.ERROR_SAFEBROWSING_PHISHING_URI -> "Deceptive site blocked" to "This site has been reported for tricking people into giving away passwords or card details."
        WebRequestError.ERROR_SAFEBROWSING_MALWARE_URI, WebRequestError.ERROR_SAFEBROWSING_HARMFUL_URI, WebRequestError.ERROR_SAFEBROWSING_UNWANTED_URI ->
            "Dangerous site blocked" to "This site has been reported for harmful or unwanted software."
        WebRequestError.ERROR_MALFORMED_URI, WebRequestError.ERROR_UNKNOWN_PROTOCOL -> "That address doesn't look right" to "Check it for typos."
        WebRequestError.ERROR_FILE_NOT_FOUND -> "File not found" to "It may have been moved or deleted."
        WebRequestError.ERROR_FILE_ACCESS_DENIED -> "Can't open this file" to "Void Web isn't allowed to read it."
        WebRequestError.ERROR_REDIRECT_LOOP -> "This page keeps redirecting" to "Clearing the site's cookies sometimes fixes it."
        WebRequestError.ERROR_CONTENT_CRASHED -> "This page crashed" to "Try loading it again."
        WebRequestError.ERROR_PORT_BLOCKED -> "This address is blocked" to "The engine doesn't allow connections to that port."
        WebRequestError.ERROR_CORRUPTED_CONTENT, WebRequestError.ERROR_INVALID_CONTENT_ENCODING -> "The page arrived damaged" to "Try again; if it keeps happening, the site has a problem."
        else -> "This page couldn't load" to "Something went wrong between your phone and the site. Try again."
    }

    fun dataUri(uri: String, error: WebRequestError): String {
        val (title, hint) = describe(error.code)
        val p = Void.palette
        val unsafe = error.category == WebRequestError.ERROR_CATEGORY_SECURITY || error.category == WebRequestError.ERROR_CATEGORY_SAFEBROWSING
        val glow = if (unsafe) p.bad else p.accent2
        val html = """
            <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <meta name="color-scheme" content="${if (p.light) "light" else "dark"}"><title>${esc(title)}</title>
            <style>
            html,body{margin:0;height:100%;background:radial-gradient(circle at 50% 30%,${css(Void.mix(p.bg, p.accent, 0.14f))},${css(p.bg)} 70%);color:${css(p.text)};font:16px/1.5 system-ui,sans-serif}
            main{max-width:30rem;margin:0 auto;padding:18vh 1.5rem 2rem;text-align:center}
            .orb{width:96px;height:96px;margin:0 auto 1.5rem;border-radius:50%;background:radial-gradient(circle at 50% 50%,${css(Void.mix(p.bg, p.accent, 0.3f))},${css(p.bg)} 70%);
              border:2px solid ${css(Void.mix(p.accent, glow, 0.5f))};box-shadow:0 0 32px ${css(glow)}55,inset 0 0 18px ${css(glow)}33;position:relative}
            .orb:after{content:"";position:absolute;inset:34px;background:${css(Void.mix(glow, Color.White, 0.6f))};clip-path:polygon(50% 0,60% 40%,100% 50%,60% 60%,50% 100%,40% 60%,0 50%,40% 40%);opacity:.9}
            h1{font-size:1.5rem;margin:0 0 .5rem;font-weight:600}
            p{color:${css(p.dim)};margin:0 0 1rem}
            .addr{font:13px/1.4 ui-monospace,monospace;color:${css(p.dim)};word-break:break-all;opacity:.8}
            button{margin-top:1.5rem;padding:.75rem 1.75rem;border:0;border-radius:999px;font:inherit;font-weight:600;color:${if (p.light) "#fff" else css(p.bg)};
              background:linear-gradient(90deg,${css(p.accent)},${css(p.accent2)})}
            </style></head><body><main>
            <div class="orb"></div>
            <h1>${esc(title)}</h1><p>${esc(hint)}</p>
            <div class="addr">${esc(uri)}</div>
            ${if (unsafe) "" else "<button onclick=\"location.reload()\">Try again</button>"}
            </main></body></html>
        """.trimIndent()
        return "data:text/html;charset=utf-8;base64," + Base64.encodeToString(html.toByteArray(), Base64.NO_WRAP)
    }
}
