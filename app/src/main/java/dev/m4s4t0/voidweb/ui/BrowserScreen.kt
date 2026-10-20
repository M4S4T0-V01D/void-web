@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.m4s4t0.voidweb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.m4s4t0.voidweb.Ask
import dev.m4s4t0.voidweb.Browser
import dev.m4s4t0.voidweb.Prefs
import dev.m4s4t0.voidweb.Screen
import dev.m4s4t0.voidweb.Store
import dev.m4s4t0.voidweb.Tab
import dev.m4s4t0.voidweb.Urls
import org.mozilla.geckoview.GeckoView

/** The browser: the page (or the new-tab page), the toolbar, and the find bar. */
@Composable
fun BrowserScreen(gecko: GeckoView) {
    val tab = Browser.current
    val full = Browser.fullscreen
    val home = tab == null || (Urls.isBlank(tab.url) && !tab.openedByEngine)
    val bottom = Prefs.toolbarBottom
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().then(if (full) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing))) {
        if (!full && !bottom) Toolbar(tab, onMenu = { menu = true })
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // The page itself. Always composed, so the engine's view never leaves the window.
            AndroidView(factory = { gecko }, modifier = Modifier.fillMaxSize())
            if (home) HomePage(tab)
            if (tab != null && tab.loading && !home) LoadBar(tab.progress / 100f, Modifier.align(if (bottom) Alignment.BottomCenter else Alignment.TopCenter))
            if (Browser.findOpen) FindBar(Modifier.align(if (bottom) Alignment.BottomCenter else Alignment.TopCenter))
        }
        if (!full && bottom) Toolbar(tab, onMenu = { menu = true })
    }
    if (menu && tab != null) BrowserMenu(tab) { menu = false }
}

@Composable
private fun Toolbar(tab: Tab?, onMenu: () -> Unit) {
    val private = tab?.private == true
    val bg = if (private) Void.mix(Void.panel, Void.violet, 0.22f) else Void.panel
    Row(
        Modifier.fillMaxWidth().background(bg).padding(horizontal = 6.dp, vertical = 6.dp).height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AddressPill(tab, Modifier.weight(1f))
        Spacer(Modifier.width(2.dp))
        TabsButton(Browser.tabs.count { it.private == private }, private) {
            Browser.captureThumb(Browser.current)
            Browser.screen = Screen.TABS
        }
        IconButton(onClick = onMenu) { Icon(Icons.Rounded.MoreVert, "Menu", tint = Void.text) }
    }
}

@Composable
private fun AddressPill(tab: Tab?, modifier: Modifier) {
    val url = tab?.url ?: ""
    val blank = Urls.isBlank(url)
    val terms = if (blank) null else Urls.searchTerms(url, Prefs.engine)
    val icon: ImageVector = when {
        tab?.private == true -> Icons.Rounded.VisibilityOff
        blank || terms != null -> Icons.Rounded.Search
        url.startsWith("https://") && tab?.secure != false -> Icons.Rounded.Lock
        url.startsWith("http://") -> Icons.Rounded.LockOpen
        else -> Icons.Rounded.Public
    }
    val iconTint = when {
        tab?.private == true -> Void.violet
        url.startsWith("http://") && !blank -> Void.warn
        else -> Void.dim
    }
    Row(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(50))
            .background(Void.panelAlt)
            .border(1.dp, Void.mix(Void.border, Void.violet, 0.25f), RoundedCornerShape(50))
            .clickable { Browser.editing = true }
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                blank -> "Search or type an address"
                terms != null -> terms
                else -> Urls.displayHost(url)
            },
            color = if (blank) Void.dim else Void.text,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!blank && tab != null) {
            IconButton(onClick = { Browser.reloadOrStop() }, modifier = Modifier.size(36.dp)) {
                Icon(if (tab.loading) Icons.Rounded.Close else Icons.Rounded.Refresh, if (tab.loading) "Stop" else "Reload", tint = Void.dim, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun TabsButton(count: Int, private: Boolean, onClick: () -> Unit) {
    val c = if (private) Void.violet else Void.text
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(24.dp).border(2.dp, c, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
            Text(if (count > 99) "99+" else count.toString(), color = c, fontSize = if (count > 99) 9.sp else 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Typing in the address bar: the text field, then a search row and matching bookmarks and history. */
@Composable
fun AddressEditor() {
    val tab = Browser.current
    val start = tab?.url?.takeUnless { Urls.isBlank(it) }?.let { Urls.searchTerms(it, Prefs.engine) ?: it } ?: ""
    var field by remember { mutableStateOf(TextFieldValue(start, TextRange(0, start.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val text = field.text.trim()
    val suggestions = remember(text, Store.changes) { if (text.isEmpty()) Store.history(8) else Store.suggest(text) }
    Column(Modifier.fillMaxSize().background(Void.bg).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Void.panelAlt)
                    .border(1.5.dp, Void.mix(Void.violet, Void.cyan, 0.5f), RoundedCornerShape(50))
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (tab?.private == true) Icons.Rounded.VisibilityOff else Icons.Rounded.Search, null, tint = if (tab?.private == true) Void.violet else Void.dim, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    field,
                    onValueChange = { field = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Void.text, fontSize = 16.sp),
                    cursorBrush = SolidColor(Void.cyan),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onGo = { if (text.isNotEmpty()) Browser.go(text) }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    decorationBox = { inner ->
                        Box {
                            if (field.text.isEmpty()) Text("Search ${Prefs.engine.label} or type an address", color = Void.dim, fontSize = 16.sp, maxLines = 1)
                            inner()
                        }
                    },
                )
                if (field.text.isNotEmpty()) {
                    IconButton(onClick = { field = TextFieldValue("") }, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.Close, "Clear", tint = Void.dim, modifier = Modifier.size(20.dp)) }
                }
            }
            Text("Cancel", color = Void.cyan, fontSize = 15.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { Browser.editing = false }.padding(horizontal = 12.dp, vertical = 10.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (text.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { Browser.go(text) }.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Search, null, tint = Void.cyan, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("“$text”", color = Void.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(if (Urls.fromInput(text, Prefs.engine).startsWith(Prefs.engine.template.substringBefore("%s"))) Prefs.engine.label else "Go", color = Void.dim, fontSize = 12.sp)
                    }
                }
            } else if (suggestions.isNotEmpty()) {
                item { SectionTitle("Recent", Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)) }
            }
            items(suggestions, key = { it.url }) { s ->
                SiteRow(s.title, s.url, onClick = { Browser.go(s.url) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    IconButton(onClick = { field = TextFieldValue(s.url, TextRange(s.url.length)) }) { Icon(Icons.Rounded.NorthWest, "Edit this address", tint = Void.dim, modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}

/** The new-tab page: the logo over the living sky, a search pill, an update notice, and the most visited sites. */
@Composable
private fun HomePage(tab: Tab?) {
    val top = remember(Store.changes) { Store.topSites(8) }
    val marks = remember(Store.changes) { Store.bookmarks().take(8) }
    Box(Modifier.fillMaxSize().background(Void.bg)) {
        CosmicBackground(animated = Prefs.livingBackground)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            WebLogo(Modifier.size(132.dp))
            Spacer(Modifier.height(6.dp))
            Text(if (tab?.private == true) "Private tab" else "Void Web", color = Void.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Void.panel.copy(alpha = 0.9f))
                    .border(1.dp, Void.mix(Void.border, Void.violet, 0.4f), RoundedCornerShape(50))
                    .clickable { Browser.editing = true }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = Void.cyan, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Search ${Prefs.engine.label} or type an address", color = Void.dim, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(16.dp))
            if (tab?.private == true) {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.VisibilityOff, null, tint = Void.violet)
                        Spacer(Modifier.width(10.dp))
                        Text("Nothing from private tabs is kept", color = Void.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                    Text("Pages you visit here stay out of your history, and their cookies and site data vanish when you close your last private tab. Downloads and bookmarks you make are kept.", color = Void.dim, fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
            }
            UpdateBanner()
            if (top.isNotEmpty()) {
                SectionTitle("Your sites", Modifier.align(Alignment.Start).padding(top = 12.dp, bottom = 8.dp))
                SiteGrid(top.map { it.title to it.url })
            }
            if (marks.isNotEmpty()) {
                SectionTitle("Bookmarks", Modifier.align(Alignment.Start).padding(top = 16.dp, bottom = 8.dp))
                SiteGrid(marks.map { it.title to it.url })
            }
            if (top.isEmpty() && marks.isEmpty() && tab?.private != true) {
                Spacer(Modifier.height(24.dp))
                Text("Sites you visit often will gather here.", color = Void.dim, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SiteGrid(sites: List<Pair<String, String>>) {
    FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 4, horizontalArrangement = Arrangement.SpaceBetween) {
        for ((title, url) in sites) {
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { Browser.go(url) }.padding(vertical = 8.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SiteTile(url, 52.dp)
                Spacer(Modifier.height(6.dp))
                Text(title.ifBlank { Urls.displayHost(url) }, color = Void.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
        repeat((4 - sites.size % 4) % 4) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun FindBar(modifier: Modifier) {
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val r = Browser.findResult
    Row(
        modifier.fillMaxWidth().padding(8.dp).clip(RoundedCornerShape(16.dp)).background(Void.panelAlt).border(1.dp, Void.mix(Void.border, Void.cyan, 0.4f), RoundedCornerShape(16.dp)).padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            q,
            onValueChange = {
                q = it
                Browser.find(it)
            },
            singleLine = true,
            textStyle = TextStyle(color = Void.text, fontSize = 15.sp),
            cursorBrush = SolidColor(Void.cyan),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { Browser.find(null) }),
            modifier = Modifier.weight(1f).focusRequester(focus),
            decorationBox = { inner -> Box { if (q.isEmpty()) Text("Find in page", color = Void.dim, fontSize = 15.sp); inner() } },
        )
        if (r != null && q.isNotEmpty()) Text(if (r.found) "${r.current}/${r.total}" else "0/0", color = if (r.found) Void.dim else Void.bad, fontSize = 13.sp)
        IconButton(onClick = { Browser.find(null, backwards = true) }, enabled = q.isNotEmpty()) { Icon(Icons.Rounded.KeyboardArrowUp, "Previous", tint = Void.text) }
        IconButton(onClick = { Browser.find(null) }, enabled = q.isNotEmpty()) { Icon(Icons.Rounded.KeyboardArrowDown, "Next", tint = Void.text) }
        IconButton(onClick = { Browser.closeFind() }) { Icon(Icons.Rounded.Close, "Close", tint = Void.text) }
    }
}

/** The ⋮ menu: page controls along the top, then everything else. */
@Composable
private fun BrowserMenu(tab: Tab, onClose: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val page = !Urls.isBlank(tab.url)
    val marked = remember(tab.url, Store.changes) { page && Store.isBookmarked(tab.url) }
    fun act(block: () -> Unit) {
        onClose()
        block()
    }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = Void.panel, contentColor = Void.text, dragHandle = null) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                BarIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", { act { tab.session.goBack() } }, enabled = tab.canBack)
                BarIcon(Icons.AutoMirrored.Rounded.ArrowForward, "Forward", { act { tab.session.goForward() } }, enabled = tab.canForward)
                BarIcon(if (tab.loading) Icons.Rounded.Close else Icons.Rounded.Refresh, if (tab.loading) "Stop" else "Reload", { act { Browser.reloadOrStop() } }, enabled = page)
                BarIcon(if (marked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, if (marked) "Remove bookmark" else "Bookmark", {
                    act {
                        if (marked) {
                            Store.removeBookmark(tab.url)
                            Browser.toast("Bookmark removed")
                        } else {
                            Store.addBookmark(tab.url, tab.title)
                            Browser.toast("Bookmarked", "Edit") { Browser.ask(Ask.EditBookmark(tab.url, tab.title)) }
                        }
                    }
                }, tint = if (marked) Void.cyan else Void.text, enabled = page)
                BarIcon(Icons.Rounded.Share, "Share", { act { Browser.share(tab.url, tab.title) } }, enabled = page)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Void.border))
            MenuItem(Icons.Rounded.Add, "New tab") { act { Browser.newTab(null, private = false); Browser.editing = true } }
            MenuItem(Icons.Rounded.VisibilityOff, "New private tab", tint = Void.violet) { act { Browser.newTab(null, private = true); Browser.editing = true } }
            MenuItem(Icons.Rounded.Bookmarks, "Bookmarks") { act { Browser.screen = Screen.BOOKMARKS } }
            MenuItem(Icons.Rounded.History, "History") { act { Browser.screen = Screen.HISTORY } }
            MenuItem(Icons.Rounded.Download, "Downloads") { act { Browser.screen = Screen.DOWNLOADS } }
            MenuItem(Icons.Rounded.Extension, "Add-ons") { act { Browser.screen = Screen.ADDONS } }
            if (page) {
                MenuItem(Icons.Rounded.Search, "Find in page") { act { Browser.findOpen = true } }
                Row(Modifier.fillMaxWidth().clickable { act { tab.toggleDesktop() } }.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DesktopWindows, null, tint = Void.text, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(18.dp))
                    Box(Modifier.weight(1f)) { Toggle("Desktop site", null, tab.desktop) { act { tab.toggleDesktop() } } }
                }
                MenuItem(Icons.AutoMirrored.Rounded.OpenInNew, "Open in another app") {
                    act { Browser.openWith(tab.url) }
                }
            }
            MenuItem(Icons.Rounded.Settings, "Settings") { act { Browser.screen = Screen.SETTINGS } }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, tint: androidx.compose.ui.graphics.Color = Void.text, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, color = Void.text, fontSize = 15.sp)
    }
}
