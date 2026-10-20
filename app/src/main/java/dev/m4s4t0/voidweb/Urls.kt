package dev.m4s4t0.voidweb

import java.net.URLEncoder

/** A search engine: [template] has %s where the encoded query goes. */
enum class SearchEngine(val label: String, val template: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    BRAVE("Brave Search", "https://search.brave.com/search?q=%s"),
    STARTPAGE("Startpage", "https://www.startpage.com/do/search?query=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s"),
    ECOSIA("Ecosia", "https://www.ecosia.org/search?q=%s"),
    ;

    fun url(query: String): String = template.replace("%s", URLEncoder.encode(query, "UTF-8"))
}

object Urls {
    /** Schemes the engine loads itself; anything else (mailto:, tel:, intent:, market: …) is handed to Android. */
    private val WEB_SCHEMES = setOf("http", "https", "about", "data", "blob", "javascript", "moz-extension", "resource", "file", "view-source")

    fun isWebScheme(url: String): Boolean = scheme(url)?.let { it in WEB_SCHEMES } ?: true

    fun scheme(url: String): String? = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):").find(url)?.groupValues?.get(1)?.lowercase()

    /**
     * What typing [input] into the address bar should open: the address itself when it looks like one
     * ("example.com", "localhost:8080", "192.168.1.1/admin", "https://…"), otherwise a search.
     */
    fun fromInput(input: String, engine: SearchEngine): String {
        val text = input.trim()
        if (text.isEmpty()) return "about:blank"
        val scheme = scheme(text)
        // "about:config", "https://…", "mailto:…": but not "note: buy milk" (a scheme has no space after the colon).
        if (scheme != null && !text.contains(' ') && (scheme in WEB_SCHEMES || text.startsWith("$scheme://") || scheme in setOf("mailto", "tel", "sms", "geo", "intent", "market"))) {
            return text
        }
        if (text.contains(' ')) return engine.url(text)
        val hostPart = text.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = hostPart.substringBeforeLast(':').takeIf { hostPart.contains(':') && hostPart.substringAfterLast(':').all(Char::isDigit) } ?: hostPart
        val looksLikeHost = host.equals("localhost", ignoreCase = true) ||
            Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host) ||
            Regex("^\\[[0-9a-fA-F:]+]$").matches(host) ||
            Regex("^([a-zA-Z0-9-]+\\.)+[a-zA-Z][a-zA-Z0-9-]{1,62}$").matches(host)
        if (!looksLikeHost) return engine.url(text)
        val local = host.equals("localhost", ignoreCase = true) || host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("127.")
        return (if (local) "http://" else "https://") + text
    }

    /** The host to show in the address bar ("www." dropped), or the whole address when there is none. */
    fun displayHost(url: String): String {
        val m = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://([^/?#]*)").find(url) ?: return url
        val host = m.groupValues[1].substringAfterLast('@')
        return host.removePrefix("www.")
    }

    /** The search terms when [url] is a results page of [engine], so the address bar can show them instead. */
    fun searchTerms(url: String, engine: SearchEngine): String? {
        val prefix = engine.template.substringBefore("%s")
        if (!url.startsWith(prefix)) return null
        val q = url.removePrefix(prefix).substringBefore('&').substringBefore('#')
        return runCatching { java.net.URLDecoder.decode(q, "UTF-8") }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun isBlank(url: String?): Boolean = url.isNullOrEmpty() || url == "about:blank"

    /** A file name for a download: from Content-Disposition when given, else the last part of the address. */
    fun fileName(url: String, contentDisposition: String?, mime: String?): String {
        val fromHeader = contentDisposition?.let { cd ->
            Regex("filename\\*\\s*=\\s*UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(cd)?.groupValues?.get(1)?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }
                ?: Regex("filename\\s*=\\s*\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(cd)?.groupValues?.get(1)
        }
        var name = (fromHeader ?: url.substringBefore('?').substringBefore('#').substringAfterLast('/').let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) })
            .replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1f]"), "_").trim().trimStart('.')
        if (name.isEmpty() || url.startsWith("data:") || url.startsWith("blob:")) name = "download"
        if (!name.contains('.') && mime != null) {
            android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { name += ".$it" }
        }
        return name.take(120)
    }
}
