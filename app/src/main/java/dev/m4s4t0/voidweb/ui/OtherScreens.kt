package dev.m4s4t0.voidweb.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.m4s4t0.voidweb.Ask
import dev.m4s4t0.voidweb.Browser
import dev.m4s4t0.voidweb.Download
import dev.m4s4t0.voidweb.Downloads
import dev.m4s4t0.voidweb.Engine
import dev.m4s4t0.voidweb.Store
import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.WebExtension
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlin.coroutines.resume

@Composable
private fun ListPage(title: String, subtitle: String? = null, actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Void.bg)) {
        CosmicBackground(animated = false)
        Column(Modifier.fillMaxSize()) {
            VoidTopBar(title, onBack = { Browser.back() }, subtitle = subtitle, actions = actions)
            content()
        }
    }
}

@Composable
fun BookmarksScreen() {
    val marks = remember(Store.changes) { Store.bookmarks() }
    ListPage("Bookmarks", subtitle = if (marks.isEmpty()) null else "${marks.size}") {
        if (marks.isEmpty()) {
            EmptyState("No bookmarks yet", "Tap the bookmark in the ⋮ menu to keep a page here")
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 32.dp)) {
                items(marks, key = { it.url }) { s ->
                    SiteRow(s.title, s.url, onClick = { Browser.go(s.url) }, modifier = Modifier.animateItem()) {
                        IconButton(onClick = { Browser.ask(Ask.EditBookmark(s.url, s.title)) }) { Icon(Icons.Rounded.Edit, "Rename", tint = Void.dim, modifier = Modifier.size(18.dp)) }
                        IconButton(onClick = {
                            Store.removeBookmark(s.url)
                            Browser.toast("Bookmark removed", "Undo") { Store.addBookmark(s.url, s.title) }
                        }) { Icon(Icons.Rounded.Close, "Remove", tint = Void.dim, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
        }
    }
}

private fun dayLabel(t: Long): String {
    val c = Calendar.getInstance()
    val today = c.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    return when {
        t >= today -> "Today"
        t >= today - 86_400_000L -> "Yesterday"
        t >= today - 6 * 86_400_000L -> "This week"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(t))
    }
}

@Composable
fun HistoryScreen() {
    val list = remember(Store.changes) { Store.history() }
    ListPage("History", actions = {
        if (list.isNotEmpty()) BarIcon(Icons.Rounded.DeleteSweep, "Clear history", { Browser.ask(Ask.ClearData()) }, tint = Void.bad)
    }) {
        if (list.isEmpty()) {
            EmptyState("No history", "Sites you visit appear here (private tabs never do)")
        } else {
            val groups = list.groupBy { dayLabel(it.last) }
            LazyColumn(contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 32.dp)) {
                for ((day, sites) in groups) {
                    item(key = "h:$day") { SectionTitle(day, Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp)) }
                    items(sites, key = { it.url }) { s ->
                        SiteRow(s.title, s.url, onClick = { Browser.go(s.url) }, modifier = Modifier.animateItem()) {
                            IconButton(onClick = { Store.removeHistory(s.url) }) { Icon(Icons.Rounded.Close, "Remove", tint = Void.dim, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSize(b: Long): String = when {
    b < 0 -> "?"
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "%.0f KB".format(b / 1024.0)
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / 1048576.0)
    else -> "%.2f GB".format(b / 1073741824.0)
}

@Composable
fun DownloadsScreen() {
    val ctx = LocalContext.current
    ListPage("Downloads", actions = { BarIcon(Icons.Rounded.FolderOpen, "Open the Downloads folder", { Downloads.openFolder() }) }) {
        if (Downloads.items.isEmpty()) {
            EmptyState("No downloads yet", "Files you download go to your phone's Downloads folder")
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Downloads.items, key = { it.id }) { d -> DownloadRow(d, onOpen = { Downloads.open(ctx, d) }) }
            }
        }
    }
}

@Composable
private fun DownloadRow(d: Download, onOpen: () -> Unit) {
    Card(Modifier, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, null, tint = if (d.state == Download.State.FAILED) Void.bad else Void.cyan, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name, color = Void.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when (d.state) {
                        Download.State.RUNNING -> if (d.total > 0) "${formatSize(d.done)} of ${formatSize(d.total)}" else formatSize(d.done)
                        Download.State.DONE -> formatSize(d.done)
                        Download.State.FAILED -> "Failed"
                        Download.State.CANCELLED -> "Cancelled"
                    },
                    color = Void.dim,
                    fontSize = 12.sp,
                )
            }
            when (d.state) {
                Download.State.DONE -> TextButton(onClick = onOpen) { Text("Open", color = Void.cyan) }
                Download.State.RUNNING -> TextButton(onClick = { Downloads.cancel(d) }) { Text("Cancel", color = Void.bad) }
                else -> IconButton(onClick = { Downloads.remove(d) }) { Icon(Icons.Rounded.Close, "Remove", tint = Void.dim) }
            }
        }
        if (d.state == Download.State.RUNNING) {
            val f = d.fraction
            val bar = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50))
            if (f >= 0f) LinearProgressIndicator(progress = { f }, modifier = bar, color = Void.cyan, trackColor = Void.border) else LinearProgressIndicator(modifier = bar, color = Void.cyan, trackColor = Void.border)
        }
    }
}

@Composable
fun AddonsScreen() {
    val list = Engine.addons
    var reinstalling by remember { mutableStateOf(false) }
    ListPage("Add-ons") {
        LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(
                    "Firefox add-ons run in Void Web. Find more at addons.mozilla.org and tap “Add to Firefox”.",
                    color = Void.dim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            items(list, key = { it.id }) { ext -> AddonCard(ext) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    OutlinedButton(onClick = { Browser.go("https://addons.mozilla.org/android/") }) { Text("Browse add-ons", color = Void.cyan) }
                    if (list.none { it.id == Engine.UBLOCK_ID }) {
                        OutlinedButton(enabled = !reinstalling, onClick = {
                            reinstalling = true
                            Engine.reinstallUblock { ok ->
                                reinstalling = false
                                Browser.toast(if (ok) "uBlock Origin installed" else "Couldn't reach addons.mozilla.org")
                            }
                        }) { Text(if (reinstalling) "Installing…" else "Install uBlock Origin", color = Void.cyan) }
                    }
                }
            }
        }
    }
}

/** An add-on's icon, loaded from the engine at the size shown. */
@Composable
private fun addonIcon(ext: WebExtension, px: Int): ImageBitmap? {
    val icon by produceState<ImageBitmap?>(null, ext.id, ext.metaData.version) {
        value = runCatching {
            suspendCancellableCoroutine { cont ->
                ext.metaData.icon.getBitmap(px).accept({ cont.resume(it?.asImageBitmap()) }, { cont.resume(null) })
            }
        }.getOrNull()
    }
    return icon
}

@Composable
private fun AddonCard(ext: WebExtension) {
    val m = ext.metaData
    val icon = addonIcon(ext, 96)
    Card(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Void.panelAlt), contentAlignment = Alignment.Center) {
                if (icon != null) Image(icon, null, Modifier.size(32.dp)) else Icon(Icons.Rounded.Extension, null, tint = Void.violet)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name ?: ext.id, color = Void.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull("v${m.version}", m.creatorName).joinToString("  ·  "), color = Void.dim, fontSize = 12.sp, maxLines = 1)
            }
        }
        m.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = Void.dim, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        Toggle("On", null, m.enabled) { Engine.setEnabled(ext, it) }
        Toggle("Run in private tabs", null, m.allowedInPrivateBrowsing) { Engine.setAllowedInPrivate(ext, it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            m.optionsPageUrl?.let { opts -> OutlinedButton(onClick = { Browser.newTab(opts, private = false) }) { Text("Settings", color = Void.cyan) } }
            TextButton(onClick = { Engine.uninstall(ext) }) { Text("Remove", color = Void.bad) }
        }
    }
}
