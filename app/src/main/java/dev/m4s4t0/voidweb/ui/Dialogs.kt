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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.m4s4t0.voidweb.Ask
import dev.m4s4t0.voidweb.Browser
import dev.m4s4t0.voidweb.Downloads
import dev.m4s4t0.voidweb.Engine
import dev.m4s4t0.voidweb.Store
import dev.m4s4t0.voidweb.Urls
import org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaSource
import org.mozilla.geckoview.GeckoSession.PromptDelegate.ChoicePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.DateTimePrompt
import org.mozilla.geckoview.StorageController
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields

/** Shows the first unanswered [Ask]; answering (or dismissing) it brings up the next. */
@Composable
fun Dialogs() {
    val a = Browser.asks.firstOrNull() ?: return
    fun close() = Browser.answered(a)
    when (a) {
        is Ask.Alert -> VoidDialog(a.title?.takeIf { it.isNotBlank() } ?: "This page says", onDismiss = { a.dismiss(); close() }, confirm = "OK" to { a.ok(); close() }) {
            Message(a.message)
        }
        is Ask.Confirm -> VoidDialog(a.title?.takeIf { it.isNotBlank() } ?: "This page asks", onDismiss = { a.dismiss(); close() }, confirm = "OK" to { a.answer(true); close() }, dismiss = "Cancel" to { a.answer(false); close() }) {
            Message(a.message)
        }
        is Ask.TextInput -> TextInputDialog(a, ::close)
        is Ask.Choice -> ChoiceDialog(a, ::close)
        is Ask.Login -> LoginDialog(a, ::close)
        is Ask.DateTime -> DateTimeDialog(a, ::close)
        is Ask.ColorPick -> ColorDialog(a, ::close)
        is Ask.Allow -> VoidDialog(a.title, onDismiss = { a.dismiss(); close() }, confirm = a.yes to { a.answer(true); close() }, dismiss = a.no to { a.answer(false); close() }) {
            Message(a.message)
        }
        is Ask.Permission -> VoidDialog(a.host, onDismiss = { a.dismiss(); close() }, confirm = "Allow" to { a.answer(true); close() }, dismiss = "Block" to { a.answer(false); close() }) {
            Message("wants to ${a.what}." + if (a.private) " (Only for this private tab.)" else "")
        }
        is Ask.Media -> MediaDialog(a, ::close)
        is Ask.ContextMenu -> ContextMenuDialog(a, ::close)
        is Ask.OpenApp -> VoidDialog("Leave Void Web?", onDismiss = { a.dismiss(); close() }, confirm = "Open" to { a.answer(true); close() }, dismiss = "Stay" to { a.answer(false); close() }) {
            Message("This link wants to open ${a.app}.")
        }
        is Ask.InstallAddon -> VoidDialog("Add ${a.ext.metaData.name ?: "this add-on"}?", onDismiss = { a.dismiss(); close() }, confirm = "Add" to { a.answer(true); close() }, dismiss = "Cancel" to { a.answer(false); close() }) {
            AddonPermissions(a.permissions, "It will be able to:")
        }
        is Ask.UpdateAddon -> VoidDialog(
            "${a.ext.metaData.name ?: "An add-on"} ${if (a.optional) "asks for more" else "needs new permissions"}",
            onDismiss = { a.dismiss(); close() },
            confirm = "Allow" to { a.answer(true); close() },
            dismiss = "Not now" to { a.answer(false); close() },
        ) {
            AddonPermissions(a.permissions, "It wants to:")
        }
        is Ask.EditBookmark -> EditBookmarkDialog(a, ::close)
        is Ask.ClearData -> ClearDataDialog(a, ::close)
    }
}

@Composable
private fun VoidDialog(
    title: String,
    onDismiss: () -> Unit,
    confirm: Pair<String, () -> Unit>? = null,
    dismiss: Pair<String, () -> Unit>? = null,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Void.panel,
        titleContentColor = Void.text,
        textContentColor = Void.text,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = content,
        confirmButton = { confirm?.let { (label, act) -> TextButton(onClick = act) { Text(label, color = Void.cyan, fontWeight = FontWeight.SemiBold) } } },
        dismissButton = { dismiss?.let { (label, act) -> TextButton(onClick = act) { Text(label, color = Void.dim) } } },
    )
}

@Composable
private fun Message(text: String) {
    Box(Modifier.heightIn(max = 360.dp)) {
        LazyColumn { item { Text(text, color = Void.text, fontSize = 15.sp) } }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Void.text,
    unfocusedTextColor = Void.text,
    focusedBorderColor = Void.cyan,
    unfocusedBorderColor = Void.border,
    cursorColor = Void.cyan,
    focusedLabelColor = Void.cyan,
    unfocusedLabelColor = Void.dim,
)

@Composable
private fun TextInputDialog(a: Ask.TextInput, close: () -> Unit) {
    var text by remember(a) { mutableStateOf(a.initial) }
    VoidDialog(a.title?.takeIf { it.isNotBlank() } ?: "This page asks", onDismiss = { a.dismiss(); close() }, confirm = "OK" to { a.answer(text); close() }, dismiss = "Cancel" to { a.dismiss(); close() }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            a.message?.takeIf { it.isNotBlank() }?.let { Text(it, color = Void.text, fontSize = 15.sp) }
            OutlinedTextField(text, { text = it }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
        }
    }
}

/** A <select>: tap an option to pick it, or tick several and press Done when the list allows that. */
@Composable
private fun ChoiceDialog(a: Ask.Choice, close: () -> Unit) {
    val picked = remember(a) { mutableStateListOf<ChoicePrompt.Choice>().apply { addAll(flatten(a.choices).filter { it.selected }) } }
    VoidDialog(
        a.title?.takeIf { it.isNotBlank() } ?: a.message?.takeIf { it.isNotBlank() } ?: "Choose",
        onDismiss = { a.dismiss(); close() },
        confirm = if (a.multiple) "Done" to { a.answer(picked.toList()); close() } else null,
        dismiss = "Cancel" to { a.dismiss(); close() },
    ) {
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(rows(a.choices)) { (choice, group) ->
                when {
                    choice.separator -> Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(Void.border))
                    group -> Text(choice.label, color = Void.dim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                    else -> {
                        val on = picked.any { it.id == choice.id }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (on && !a.multiple) Void.cyan.copy(alpha = 0.14f) else Color.Transparent)
                                .clickable(enabled = !choice.disabled) {
                                    if (a.multiple) {
                                        if (on) picked.removeAll { it.id == choice.id } else picked += choice
                                    } else {
                                        a.answer(listOf(choice))
                                        close()
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (a.multiple) {
                                Checkbox(on, null, colors = CheckboxDefaults.colors(checkedColor = Void.cyan, uncheckedColor = Void.dim, checkmarkColor = Void.bg), modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                            }
                            Text(choice.label, color = if (choice.disabled) Void.dim.copy(alpha = 0.5f) else if (on) Void.cyan else Void.text, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun flatten(list: List<ChoicePrompt.Choice>): List<ChoicePrompt.Choice> = list.flatMap { c -> c.items?.let { flatten(it.toList()) } ?: listOf(c) }

/** Options with their group headings (<optgroup>) in order: (choice, isHeading). */
private fun rows(list: List<ChoicePrompt.Choice>): List<Pair<ChoicePrompt.Choice, Boolean>> = list.flatMap { c ->
    val sub = c.items
    if (sub != null) listOf(c to true) + rows(sub.toList()) else listOf(c to false)
}

@Composable
private fun LoginDialog(a: Ask.Login, close: () -> Unit) {
    var user by remember(a) { mutableStateOf(a.user) }
    var pass by remember(a) { mutableStateOf("") }
    VoidDialog("Sign in to ${a.host}", onDismiss = { a.dismiss(); close() }, confirm = "Sign in" to { a.answer(user, pass); close() }, dismiss = "Cancel" to { a.dismiss(); close() }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            a.message?.takeIf { it.isNotBlank() }?.let { Text(it, color = Void.dim, fontSize = 13.sp) }
            if (!a.passwordOnly) OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                pass,
                { pass = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** HTML date and time inputs, with Material pickers. Values go back in the HTML formats. */
@Composable
private fun DateTimeDialog(a: Ask.DateTime, close: () -> Unit) {
    val initialDate: LocalDate? = remember(a) {
        runCatching {
            when (a.kind) {
                DateTimePrompt.Type.DATE -> LocalDate.parse(a.initial)
                DateTimePrompt.Type.MONTH -> LocalDate.parse(a.initial + "-01")
                // "2026-W43" is ISO week-date without the weekday; take its Monday.
                DateTimePrompt.Type.WEEK -> LocalDate.parse(a.initial + "-1", DateTimeFormatter.ISO_WEEK_DATE)
                DateTimePrompt.Type.DATETIME_LOCAL -> LocalDateTime.parse(a.initial).toLocalDate()
                else -> null
            }
        }.getOrNull()
    }
    val initialTime: LocalTime? = remember(a) {
        runCatching {
            when (a.kind) {
                DateTimePrompt.Type.TIME -> LocalTime.parse(a.initial)
                DateTimePrompt.Type.DATETIME_LOCAL -> LocalDateTime.parse(a.initial).toLocalTime()
                else -> null
            }
        }.getOrNull()
    }
    // A date-and-time input asks for the date first, then the time.
    var date by remember(a) { mutableStateOf<LocalDate?>(null) }
    val needDate = a.kind != DateTimePrompt.Type.TIME && date == null
    val pickerColors = DatePickerDefaults.colors(
        containerColor = Void.panel,
        titleContentColor = Void.dim,
        headlineContentColor = Void.text,
        weekdayContentColor = Void.dim,
        dayContentColor = Void.text,
        selectedDayContainerColor = Void.cyan,
        selectedDayContentColor = Void.bg,
        todayDateBorderColor = Void.violet,
        todayContentColor = Void.violet,
        yearContentColor = Void.text,
        selectedYearContainerColor = Void.cyan,
        selectedYearContentColor = Void.bg,
        navigationContentColor = Void.text,
        subheadContentColor = Void.dim,
    )
    if (needDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = initialDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { a.dismiss(); close() },
            colors = pickerColors,
            confirmButton = {
                TextButton(onClick = {
                    val ms = state.selectedDateMillis ?: return@TextButton
                    val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                    when (a.kind) {
                        DateTimePrompt.Type.DATE -> { a.answer(d.toString()); close() }
                        DateTimePrompt.Type.MONTH -> { a.answer("%04d-%02d".format(d.year, d.monthValue)); close() }
                        DateTimePrompt.Type.WEEK -> { a.answer("%04d-W%02d".format(d.get(IsoFields.WEEK_BASED_YEAR), d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))); close() }
                        else -> date = d
                    }
                }) { Text(if (a.kind == DateTimePrompt.Type.DATETIME_LOCAL) "Next" else "OK", color = Void.cyan) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { a.clear(); close() }) { Text("Clear", color = Void.dim) }
                    TextButton(onClick = { a.dismiss(); close() }) { Text("Cancel", color = Void.dim) }
                }
            },
        ) { DatePicker(state, colors = pickerColors) }
    } else {
        val state = rememberTimePickerState(initialHour = initialTime?.hour ?: 12, initialMinute = initialTime?.minute ?: 0, is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current))
        VoidDialog("Pick a time", onDismiss = { a.dismiss(); close() }, confirm = "OK" to {
            val t = "%02d:%02d".format(state.hour, state.minute)
            a.answer(if (a.kind == DateTimePrompt.Type.DATETIME_LOCAL) "${date}T$t" else t)
            close()
        }, dismiss = "Cancel" to { a.dismiss(); close() }) {
            TimePicker(
                state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = Void.panelAlt,
                    selectorColor = Void.cyan,
                    containerColor = Void.panel,
                    clockDialSelectedContentColor = Void.bg,
                    clockDialUnselectedContentColor = Void.text,
                    periodSelectorSelectedContainerColor = Void.cyan.copy(alpha = 0.25f),
                    periodSelectorUnselectedContentColor = Void.dim,
                    periodSelectorSelectedContentColor = Void.text,
                    timeSelectorSelectedContainerColor = Void.cyan.copy(alpha = 0.25f),
                    timeSelectorUnselectedContainerColor = Void.panelAlt,
                    timeSelectorSelectedContentColor = Void.text,
                    timeSelectorUnselectedContentColor = Void.dim,
                ),
            )
        }
    }
}

private fun hex(c: Color) = "#%06x".format(c.toArgb() and 0xFFFFFF)

@Composable
private fun ColorDialog(a: Ask.ColorPick, close: () -> Unit) {
    var text by remember(a) { mutableStateOf(a.initial) }
    val swatches = listOf(
        Void.violet, Void.cyan, Void.ok, Void.warn, Void.bad, Void.text, Void.bg,
        Color(0xFFFFFFFF), Color(0xFF000000), Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFF6D4C41), Color(0xFF757575),
    )
    val valid = Regex("^#[0-9a-fA-F]{6}$").matches(text)
    VoidDialog("Pick a colour", onDismiss = { a.dismiss(); close() }, confirm = "OK" to { if (valid) { a.answer(text.lowercase()); close() } }, dismiss = "Cancel" to { a.dismiss(); close() }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in swatches) {
                    val h = hex(c)
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(if (h.equals(text, true)) 3.dp else 1.dp, if (h.equals(text, true)) Void.cyan else Void.border, CircleShape)
                            .clickable { text = h },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (valid) Color(android.graphics.Color.parseColor(text)) else Void.panelAlt).border(1.dp, Void.border, RoundedCornerShape(10.dp)))
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(text, { text = it.trim() }, singleLine = true, isError = !valid, colors = fieldColors(), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MediaDialog(a: Ask.Media, close: () -> Unit) {
    var video by remember(a) { mutableStateOf(a.video.firstOrNull { it.source == MediaSource.SOURCE_CAMERA } ?: a.video.firstOrNull()) }
    val audio = a.audio.firstOrNull()
    val what = listOfNotNull(if (a.video.isNotEmpty()) "camera" else null, if (audio != null) "microphone" else null).joinToString(" and ")
    VoidDialog(a.host, onDismiss = { a.dismiss(); close() }, confirm = "Allow" to { a.allow(video, audio); close() }, dismiss = "Block" to { a.dismiss(); close() }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("wants to use your $what.", color = Void.text, fontSize = 15.sp)
            if (a.video.size > 1) Pills(a.video.map { it to (it.name ?: "Camera") }, video) { video = it }
        }
    }
}

/** Long-press on a link or image. */
@Composable
private fun ContextMenuDialog(a: Ask.ContextMenu, close: () -> Unit) {
    val ctx = LocalContext.current
    val e: ContextElement = a.element
    val link = e.linkUri
    val src = e.srcUri?.takeIf { e.type == ContextElement.TYPE_IMAGE || e.type == ContextElement.TYPE_VIDEO || e.type == ContextElement.TYPE_AUDIO }
    fun act(block: () -> Unit) {
        a.dismiss()
        close()
        block()
    }
    VoidDialog(e.title?.takeIf { it.isNotBlank() } ?: e.linkText?.takeIf { it.isNotBlank() } ?: link?.let { Urls.displayHost(it) } ?: "Image", onDismiss = { a.dismiss(); close() }) {
        Column {
            Text(link ?: src ?: "", color = Void.dim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 8.dp))
            if (link != null) {
                if (!a.private) MenuRow(Icons.AutoMirrored.Rounded.OpenInNew, "Open in new tab") { act { Browser.newTab(link, private = false, select = false, parent = Browser.current); Browser.toast("Opened in a new tab", "Switch") { Browser.tabs.lastOrNull { it.url == link }?.let(Browser::select) } } }
                MenuRow(Icons.Rounded.VisibilityOff, "Open in private tab") { act { Browser.newTab(link, private = true, select = false, parent = Browser.current.takeIf { a.private }); Browser.toast("Opened in a private tab") } }
                MenuRow(Icons.Rounded.ContentCopy, "Copy link") { act { Browser.copy(link) } }
                MenuRow(Icons.Rounded.Share, "Share link") { act { Browser.share(link) } }
                MenuRow(Icons.Rounded.Download, "Download link") { act { Downloads.fetch(ctx, link, a.private, Browser.current?.url) } }
            }
            if (src != null) {
                if (e.type == ContextElement.TYPE_IMAGE) MenuRow(Icons.Rounded.Image, "Open image in new tab") { act { Browser.newTab(src, a.private, parent = Browser.current) } }
                MenuRow(Icons.Rounded.Download, if (e.type == ContextElement.TYPE_IMAGE) "Save image" else "Save media") { act { Downloads.fetch(ctx, src, a.private, Browser.current?.url) } }
                MenuRow(Icons.Rounded.ContentCopy, if (e.type == ContextElement.TYPE_IMAGE) "Copy image address" else "Copy media address") { act { Browser.copy(src) } }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Void.cyan, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = Void.text, fontSize = 15.sp)
    }
}

/** What an add-on's permissions mean, in plain words. */
private fun describePermission(p: String): String? = when {
    p == "<all_urls>" || p == "*://*/*" || p == "http://*/*" || p == "https://*/*" -> "Read and change what you see on all websites"
    p.contains("://") -> "Read and change what you see on ${p.substringAfter("://").substringBefore('/').removePrefix("*.")}"
    p == "tabs" -> "See your open tabs"
    p == "webRequest" || p == "webRequestBlocking" || p == "declarativeNetRequest" -> "Block or change what sites load"
    p == "downloads" -> "Download files"
    p == "notifications" -> "Show notifications"
    p == "privacy" -> "Read and change privacy settings"
    p == "history" -> "See and delete your history"
    p == "bookmarks" -> "See and change bookmarks"
    p == "cookies" -> "See and change cookies"
    p == "clipboardWrite" -> "Copy to the clipboard"
    p == "clipboardRead" -> "Read the clipboard"
    p == "geolocation" -> "Know your location"
    p == "nativeMessaging" -> "Talk to other apps"
    p in setOf("storage", "unlimitedStorage", "alarms", "contextMenus", "menus", "activeTab", "scripting", "webNavigation", "dns") -> null
    else -> p
}

@Composable
private fun AddonPermissions(perms: List<String>, intro: String) {
    val lines = perms.mapNotNull(::describePermission).distinct()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (lines.isEmpty()) {
            Text("It doesn't ask for any special permissions.", color = Void.text, fontSize = 14.sp)
        } else {
            Text(intro, color = Void.dim, fontSize = 13.sp)
            for (l in lines) Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Extension, null, tint = Void.violet, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                Spacer(Modifier.width(8.dp))
                Text(l, color = Void.text, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun EditBookmarkDialog(a: Ask.EditBookmark, close: () -> Unit) {
    var title by remember(a) { mutableStateOf(a.title) }
    VoidDialog("Bookmark", onDismiss = { a.dismiss(); close() }, confirm = "Save" to {
        if (Store.isBookmarked(a.url)) Store.renameBookmark(a.url, title.trim()) else Store.addBookmark(a.url, title.trim())
        a.dismiss()
        close()
    }, dismiss = "Remove" to {
        Store.removeBookmark(a.url)
        a.dismiss()
        close()
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Name") }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
            Text(a.url, color = Void.dim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ClearDataDialog(a: Ask.ClearData, close: () -> Unit) {
    var history by remember { mutableStateOf(true) }
    var cookies by remember { mutableStateOf(true) }
    var cache by remember { mutableStateOf(true) }
    var perms by remember { mutableStateOf(false) }
    var tabs by remember { mutableStateOf(false) }
    VoidDialog("Clear browsing data", onDismiss = { a.dismiss(); close() }, confirm = "Clear" to {
        var flags = 0L
        if (cookies) flags = flags or StorageController.ClearFlags.COOKIES or StorageController.ClearFlags.DOM_STORAGES or StorageController.ClearFlags.AUTH_SESSIONS
        if (cache) flags = flags or StorageController.ClearFlags.ALL_CACHES
        if (perms) flags = flags or StorageController.ClearFlags.SITE_SETTINGS
        if (flags != 0L) Engine.runtime.storageController.clearData(flags)
        if (history) Store.clearHistory()
        if (tabs) Browser.closeAll(private = false)
        Browser.toast("Cleared")
        a.dismiss()
        close()
    }, dismiss = "Cancel" to { a.dismiss(); close() }) {
        Column {
            Check("History", history) { history = it }
            Check("Cookies and site data", cookies, "Signs you out of most sites") { cookies = it }
            Check("Cached images and files", cache) { cache = it }
            Check("Site permissions", perms, "Location, camera and so on") { perms = it }
            Check("Open tabs", tabs) { tabs = it }
        }
    }
}

@Composable
private fun Check(label: String, on: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onChange(!on) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(on, onChange, colors = CheckboxDefaults.colors(checkedColor = Void.cyan, uncheckedColor = Void.dim, checkmarkColor = Void.bg))
        Spacer(Modifier.width(4.dp))
        Column {
            Text(label, color = Void.text, fontSize = 15.sp)
            if (sub != null) Text(sub, color = Void.dim, fontSize = 12.sp)
        }
    }
}
