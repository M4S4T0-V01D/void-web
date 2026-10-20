package dev.m4s4t0.voidweb.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.m4s4t0.voidweb.Browser
import dev.m4s4t0.voidweb.Tab
import dev.m4s4t0.voidweb.Urls

/** The tab switcher: ordinary and private tabs, as cards with a picture of each page. */
@Composable
fun TabsScreen() {
    var private by remember { mutableStateOf(Browser.current?.private == true) }
    val list = Browser.tabs.filter { it.private == private }
    Box(Modifier.fillMaxSize().background(Void.bg)) {
        CosmicBackground(animated = false)
        Column(Modifier.fillMaxSize()) {
            VoidTopBar("Tabs", onBack = { Browser.back() }) {
                if (list.isNotEmpty()) BarIcon(Icons.Rounded.DeleteSweep, if (private) "Close all private tabs" else "Close all tabs", { Browser.closeAll(private) }, tint = Void.bad)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Segment("Tabs  ${Browser.tabs.count { !it.private }}", !private, Void.cyan, Modifier.weight(1f)) { private = false }
                Segment("Private  ${Browser.tabs.count { it.private }}", private, Void.violet, Modifier.weight(1f)) { private = true }
            }
            if (list.isEmpty()) {
                Box(Modifier.weight(1f)) {
                    EmptyState(if (private) "No private tabs" else "No tabs", if (private) "Private tabs keep nothing once they're closed" else null)
                }
            } else {
                LazyVerticalGrid(
                    GridCells.Adaptive(160.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list, key = { it.id }) { t -> TabCard(t, Modifier.animateItem()) }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { Browser.newTab(null, private); Browser.editing = true },
            icon = { Icon(if (private) Icons.Rounded.VisibilityOff else Icons.Rounded.Add, null) },
            text = { Text(if (private) "Private tab" else "New tab") },
            containerColor = if (private) Void.violet else Void.cyan,
            contentColor = Void.bg,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
        )
    }
}

@Composable
private fun Segment(label: String, on: Boolean, color: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) color.copy(alpha = 0.2f) else Void.panel.copy(alpha = 0.8f))
            .border(1.dp, if (on) color else Void.border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (on) color else Void.dim, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun TabCard(t: Tab, modifier: Modifier) {
    val selected = t === Browser.current
    val accent = if (t.private) Void.violet else Void.cyan
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Void.panel)
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else Void.border, RoundedCornerShape(16.dp))
            .clickable { Browser.select(t) },
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            SiteTile(t.url.ifBlank { "void" }, 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                t.title.ifBlank { if (Urls.isBlank(t.url)) "New tab" else Urls.displayHost(t.url) },
                color = Void.text,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.size(32.dp).clip(CircleShape).clickable { Browser.close(t) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, "Close tab", tint = Void.dim, modifier = Modifier.size(18.dp))
            }
        }
        Box(Modifier.fillMaxWidth().aspectRatio(0.82f).background(Void.panelAlt), contentAlignment = Alignment.Center) {
            val thumb = t.thumb
            if (thumb != null) {
                Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
            } else if (Urls.isBlank(t.url)) {
                WebLogo(Modifier.size(72.dp), glow = 0.7f)
            } else {
                SiteTile(t.url, 56.dp)
            }
        }
    }
}
