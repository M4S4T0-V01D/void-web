package dev.m4s4t0.voidweb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlsTest {
    private val ddg = SearchEngine.DUCKDUCKGO

    @Test fun addressesOpenDirectly() {
        assertEquals("https://example.com", Urls.fromInput("example.com", ddg))
        assertEquals("https://example.com/a?b=1", Urls.fromInput("  example.com/a?b=1 ", ddg))
        assertEquals("https://sub.example.co.uk", Urls.fromInput("sub.example.co.uk", ddg))
        assertEquals("http://example.com", Urls.fromInput("http://example.com", ddg))
        assertEquals("https://x.dev/path", Urls.fromInput("https://x.dev/path", ddg))
        assertEquals("about:config", Urls.fromInput("about:config", ddg))
    }

    @Test fun localAddressesUsePlainHttp() {
        assertEquals("http://localhost:8080", Urls.fromInput("localhost:8080", ddg))
        assertEquals("http://192.168.1.1/admin", Urls.fromInput("192.168.1.1/admin", ddg))
        assertEquals("https://8.8.8.8", Urls.fromInput("8.8.8.8", ddg))
    }

    @Test fun everythingElseIsASearch() {
        assertEquals("https://duckduckgo.com/?q=cats", Urls.fromInput("cats", ddg))
        assertEquals("https://duckduckgo.com/?q=how+do+magnets+work", Urls.fromInput("how do magnets work", ddg))
        assertEquals("https://duckduckgo.com/?q=note%3A+buy+milk", Urls.fromInput("note: buy milk", ddg))
        assertEquals("https://duckduckgo.com/?q=example.com+reviews", Urls.fromInput("example.com reviews", ddg))
        assertEquals("https://www.google.com/search?q=c%2B%2B", Urls.fromInput("c++", SearchEngine.GOOGLE))
        assertEquals("https://duckduckgo.com/?q=v1.2", Urls.fromInput("v1.2", ddg))
    }

    @Test fun otherAppsLinksPassThrough() {
        assertEquals("mailto:me@example.com", Urls.fromInput("mailto:me@example.com", ddg))
        assertFalse(Urls.isWebScheme("mailto:me@example.com"))
        assertFalse(Urls.isWebScheme("intent://scan/#Intent;scheme=zxing;end"))
        assertTrue(Urls.isWebScheme("https://example.com"))
        assertTrue(Urls.isWebScheme("moz-extension://abc/popup.html"))
    }

    @Test fun displayHostDropsWww() {
        assertEquals("example.com", Urls.displayHost("https://www.example.com/a/b?c"))
        assertEquals("localhost:8080", Urls.displayHost("http://localhost:8080/"))
        assertEquals("about:blank", Urls.displayHost("about:blank"))
    }

    @Test fun searchTermsComeBackFromResultsPages() {
        assertEquals("how do magnets work", Urls.searchTerms("https://duckduckgo.com/?q=how+do+magnets+work&t=h_", ddg))
        assertNull(Urls.searchTerms("https://example.com/?q=x", ddg))
    }

    @Test fun downloadNames() {
        assertEquals("report.pdf", Urls.fileName("https://x.com/files/report.pdf?dl=1", null, "application/pdf"))
        assertEquals("Q3 plan.pdf", Urls.fileName("https://x.com/get", "attachment; filename=\"Q3 plan.pdf\"", null))
        assertEquals("naïve.txt", Urls.fileName("https://x.com/get", "attachment; filename*=UTF-8''na%C3%AFve.txt", null))
        assertEquals("a_b.txt", Urls.fileName("https://x.com/a%2Fb.txt", null, null))
        assertEquals("download", Urls.fileName("blob:https://x.com/123", null, null))
    }
}
