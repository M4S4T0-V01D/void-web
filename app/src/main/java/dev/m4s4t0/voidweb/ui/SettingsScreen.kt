@file:OptIn(ExperimentalLayoutApi::class)

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.m4s4t0.voidweb.Ask
import dev.m4s4t0.voidweb.Browser
import dev.m4s4t0.voidweb.Prefs
import dev.m4s4t0.voidweb.SearchEngine
import dev.m4s4t0.voidweb.SiteLook
import dev.m4s4t0.voidweb.Themes

@Composable
fun SettingsScreen() {
    val theme by Themes.choice.collectAsState()
    val iconFollows by Themes.iconFollows.collectAsState()
    Box(Modifier.fillMaxSize().background(Void.bg)) {
        CosmicBackground(animated = false)
        Column(Modifier.fillMaxSize()) {
            VoidTopBar("Settings", onBack = { Browser.back() })
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Updates")
                UpdatesCard()

                SectionTitle("Theme")
                Card {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 4) {
                        for (p in BUILTIN_THEMES) {
                            val on = p.id == theme
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(p.bg)
                                    .border(if (on) 2.dp else 1.dp, if (on) p.accent2 else p.border, RoundedCornerShape(14.dp))
                                    .clickable { Themes.choose(p.id) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                WebLogo(Modifier.size(48.dp), palette = p)
                                Text(if (p.id == "high-contrast") "Contrast" else p.name.removeSuffix(" (default)"), color = p.text, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1)
                            }
                        }
                        repeat((4 - BUILTIN_THEMES.size % 4) % 4) { Spacer(Modifier.weight(1f)) }
                    }
                    Toggle("App icon matches the theme", "The launcher icon changes colour with the theme", iconFollows) { Themes.setIconFollows(it) }
                    Toggle("Living background", "Drifting stars and nebula on the new-tab page", Prefs.livingBackground) { Prefs.saveLivingBackground(it) }
                    Toggle("Opening animation", "The fox circles the globe each time the app starts", Prefs.intro) { Prefs.saveIntro(it) }
                    Toggle("Sounds", "Soft sounds for tabs and finished downloads", Prefs.sounds) { Prefs.saveSounds(it) }
                    Text("Websites", color = Void.text, fontSize = 14.sp)
                    Text("Sites with a light and a dark design show this one", color = Void.dim, fontSize = 12.sp)
                    Pills(SiteLook.entries.map { it to it.label }, Prefs.siteLook) { Prefs.saveSiteLook(it) }
                }

                SectionTitle("Browsing")
                Card {
                    Text("Search engine", color = Void.text, fontSize = 14.sp)
                    Pills(SearchEngine.entries.map { it to it.label }, Prefs.engine) { Prefs.saveEngine(it) }
                    Toggle("Toolbar at the bottom", "Easier to reach with one hand", Prefs.toolbarBottom) { Prefs.saveToolbarBottom(it) }
                    Toggle("Reopen tabs", "Bring back your tabs (not private ones) when the app starts", Prefs.restoreTabs) {
                        Prefs.saveRestoreTabs(it)
                        Browser.saveSoon()
                    }
                }

                SectionTitle("Privacy")
                Card {
                    Toggle("Strict tracking protection", "Blocks trackers, fingerprinting and cryptominers, as Firefox's Strict mode does. A few sites may break", Prefs.strictTracking) { Prefs.saveStrictTracking(it) }
                    Toggle("HTTPS-only mode", "Only connect to sites securely; unencrypted sites show a warning page", Prefs.httpsOnly) { Prefs.saveHttpsOnly(it) }
                    Text("uBlock Origin blocks ads (see Add-ons). Sites are told not to sell or share your data (Global Privacy Control).", color = Void.dim, fontSize = 12.sp)
                    OutlinedButton(onClick = { Browser.ask(Ask.ClearData()) }) { Text("Clear browsing data…", color = Void.bad) }
                }

                SectionTitle("About")
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WebLogo(Modifier.size(56.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Void Web", color = Void.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("A web browser from the VOID family, by M4S4T0", color = Void.dim, fontSize = 12.sp)
                            Text("Built on Mozilla's Firefox engine (GeckoView)", color = Void.dim, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
