package dev.m4s4t0.voidweb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.m4s4t0.voidweb.Urls

@Composable
fun Card(modifier: Modifier = Modifier, padding: Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Void.panel.copy(alpha = 0.82f))
            .border(1.dp, Void.border, RoundedCornerShape(16.dp))
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = Void.dim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = modifier.padding(top = 6.dp))
}

/** The bar across the top of every screen but the browser: back, a title (and optional subtitle), and actions. */
@Composable
fun VoidTopBar(title: String, onBack: (() -> Unit)?, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 4.dp, top = 4.dp).height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Void.text) }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(title, color = Void.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = Void.dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun BarIcon(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = Void.text, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(icon, label, tint = if (enabled) tint else tint.copy(alpha = 0.35f)) }
}

@Composable
fun Toggle(title: String, sub: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onChange(!on) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Void.text, fontSize = 14.sp)
            if (sub != null) Text(sub, color = Void.dim, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedThumbColor = Void.bg, checkedTrackColor = Void.cyan, uncheckedTrackColor = Void.panelAlt, uncheckedBorderColor = Void.border))
    }
}

/** A row of pill choices; the picked one glows in the second accent. */
@Composable
fun <T> Pills(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((value, label) in options) {
            val on = value == selected
            Text(
                label,
                color = if (on) Void.bg else Void.text,
                fontSize = 13.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) Void.cyan else Void.panelAlt)
                    .clickable { onPick(value) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

/** A site's tile: its first letter on a soft gradient, the colour picked from the host so each site keeps its own. */
@Composable
fun SiteTile(url: String, size: Dp, modifier: Modifier = Modifier) {
    val host = Urls.displayHost(url)
    val letter = host.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"
    val hues = listOf(Void.violet, Void.cyan, Void.mix(Void.violet, Void.cyan, 0.5f), Void.mix(Void.violet, Void.bad, 0.4f), Void.mix(Void.cyan, Void.ok, 0.5f), Void.warn)
    val c = hues[(host.hashCode() and 0x7fffffff) % hues.size]
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.linearGradient(listOf(c.copy(alpha = 0.32f), c.copy(alpha = 0.1f))))
            .border(1.dp, c.copy(alpha = 0.4f), RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = Void.mix(c, Void.text, 0.45f), fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A list row for a site (bookmark, history, suggestion). */
@Composable
fun SiteRow(title: String, url: String, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteTile(url, 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title.ifBlank { Urls.displayHost(url) }, color = Void.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(url, color = Void.dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/** A calm empty state: the logo, dimmed, with a line of text. */
@Composable
fun EmptyState(text: String, hint: String? = null) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        WebLogo(Modifier.size(110.dp).padding(4.dp), glow = 0.6f)
        Spacer(Modifier.height(12.dp))
        Text(text, color = Void.text, fontSize = 16.sp, textAlign = TextAlign.Center)
        if (hint != null) {
            Spacer(Modifier.height(4.dp))
            Text(hint, color = Void.dim, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

/** A thin loading bar in the logo's colours. */
@Composable
fun LoadBar(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(2.5.dp)) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                .height(2.5.dp)
                .background(Brush.horizontalGradient(listOf(Void.violet, Void.mix(Void.violet, Void.cyan, 0.45f), Void.cyan))),
        )
    }
}
