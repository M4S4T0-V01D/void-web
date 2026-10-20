package dev.m4s4t0.voidweb.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.m4s4t0.voidweb.Browser
import dev.m4s4t0.voidweb.BuildConfig
import dev.m4s4t0.voidweb.Updater
import kotlinx.coroutines.launch

/** "158.0.20261019185856" → "158.0" (the build date is noise to a reader). */
fun engineVersion(v: String) = v.split('.').take(2).joinToString(".")

private fun releaseOf(st: Updater.State) = when (st) {
    is Updater.State.Available -> st.release
    is Updater.State.Downloading -> st.release
    is Updater.State.Installing -> st.release
    is Updater.State.Failed -> st.release
    else -> null
}

private fun headline(r: Updater.Release) =
    if (r.gecko.isNotBlank() && r.gecko != BuildConfig.GECKO_VERSION) "Void Web ${r.versionName} · Firefox engine ${engineVersion(r.gecko)}" else "Void Web ${r.versionName}"

/** New-tab page notice while an update is ready, downloading or waiting for the installer. */
@Composable
fun UpdateBanner() {
    val st by Updater.state.collectAsState()
    val release = releaseOf(st) ?: return
    Card(Modifier.padding(bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.SystemUpdate, null, tint = Void.cyan)
            Text("Update ready", color = Void.text, fontWeight = FontWeight.SemiBold)
        }
        Text(headline(release), color = Void.dim, fontSize = 13.sp)
        UpdateAction(st, release)
    }
}

@Composable
private fun UpdateAction(st: Updater.State, release: Updater.Release) {
    val ctx = LocalContext.current
    when (st) {
        is Updater.State.Downloading -> {
            LinearProgressIndicator(progress = { st.progress }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)), color = Void.cyan, trackColor = Void.border)
            Text("Downloading… ${(st.progress * 100).toInt()}%", color = Void.dim, fontSize = 12.sp)
        }
        is Updater.State.Installing -> Text("Installing… (confirm in Android's installer if it asks)", color = Void.dim, fontSize = 13.sp)
        else -> {
            if (st is Updater.State.Failed) Text(st.message, color = Void.warn, fontSize = 13.sp)
            Button(onClick = { Browser.scope.launch { Updater.install(ctx, release) } }, colors = ButtonDefaults.buttonColors(containerColor = Void.cyan, contentColor = Void.bg)) { Text("Update") }
        }
    }
}

/** Settings card: the versions, a check button, and the update flow. */
@Composable
fun UpdatesCard() {
    val ctx = LocalContext.current
    val st by Updater.state.collectAsState()
    Card {
        Text("Void Web ${BuildConfig.VERSION_NAME}", color = Void.text, fontWeight = FontWeight.Medium)
        Text("Firefox engine ${engineVersion(BuildConfig.GECKO_VERSION)}", color = Void.dim, fontSize = 13.sp)
        val release = releaseOf(st)
        if (release != null) {
            Text("Ready: ${headline(release)}", color = Void.cyan, fontSize = 13.sp)
            if (release.notes.isNotBlank()) Text(release.notes, color = Void.dim, fontSize = 13.sp)
            UpdateAction(st, release)
        } else {
            Text(
                when (st) {
                    Updater.State.Checking -> "Checking…"
                    Updater.State.UpToDate -> "You have the latest version."
                    is Updater.State.Failed -> (st as Updater.State.Failed).message
                    else -> "New versions are built automatically when Mozilla releases a new Firefox engine."
                },
                color = if (st is Updater.State.Failed) Void.warn else Void.dim,
                fontSize = 13.sp,
            )
            OutlinedButton(onClick = { Browser.scope.launch { Updater.check(ctx) } }, enabled = st != Updater.State.Checking) { Text("Check for updates", color = Void.cyan) }
        }
    }
}
