package dev.m4s4t0.voidweb

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import dev.m4s4t0.voidweb.ui.Void
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * Firefox's engine (GeckoView): one runtime for the whole app, set up with the user's privacy and look
 * settings, plus the add-ons it runs. uBlock Origin is installed from addons.mozilla.org on first launch.
 */
object Engine {
    lateinit var runtime: GeckoRuntime
        private set

    /** Installed add-ons, refreshed whenever one is installed, removed, enabled or disabled. */
    val addons = mutableStateListOf<WebExtension>()

    const val UBLOCK_ID = "uBlock0@raymondhill.net"
    private const val UBLOCK_XPI = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi"
    private const val ADDON_UPDATE_EVERY_MS = 24 * 60 * 60 * 1000L

    /** Add-ons the app itself asked for; they install without a prompt. */
    private val trusted = setOf(UBLOCK_ID)

    fun init(ctx: Context) {
        val settings = GeckoRuntimeSettings.Builder()
            .aboutConfigEnabled(true)
            .consoleOutput(BuildConfig.DEBUG)
            .remoteDebuggingEnabled(false)
            .loginAutofillEnabled(false)
            .globalPrivacyControlEnabled(true)
            // Lets addons.mozilla.org's "Add to Firefox" buttons install add-ons (through the prompt below).
            .extensionsWebAPIEnabled(true)
            .preferredColorScheme(colorScheme())
            .allowInsecureConnections(if (Prefs.httpsOnly) GeckoRuntimeSettings.HTTPS_ONLY else GeckoRuntimeSettings.HTTPS_ONLY_PRIVATE)
            .contentBlocking(
                ContentBlocking.Settings.Builder()
                    .antiTracking(if (Prefs.strictTracking) ContentBlocking.AntiTracking.STRICT else ContentBlocking.AntiTracking.DEFAULT)
                    .enhancedTrackingProtectionLevel(if (Prefs.strictTracking) ContentBlocking.EtpLevel.STRICT else ContentBlocking.EtpLevel.DEFAULT)
                    .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
                    .cookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
                    .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                    .queryParameterStrippingEnabled(true)
                    .queryParameterStrippingPrivateBrowsingEnabled(true)
                    .build(),
            )
            .build()
        runtime = GeckoRuntime.create(ctx, settings)
        setUpAddons()
    }

    private fun colorScheme(): Int = when (Prefs.siteLook) {
        SiteLook.THEME -> if (Void.palette.light) GeckoRuntimeSettings.COLOR_SCHEME_LIGHT else GeckoRuntimeSettings.COLOR_SCHEME_DARK
        SiteLook.LIGHT -> GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
        SiteLook.DARK -> GeckoRuntimeSettings.COLOR_SCHEME_DARK
    }

    /** Sites that offer a dark and a light design show the one matching the theme (or the user's choice). */
    fun applyLook() {
        if (::runtime.isInitialized) runtime.settings.setPreferredColorScheme(colorScheme())
    }

    fun applyPrivacy() {
        if (!::runtime.isInitialized) return
        runtime.settings.setAllowInsecureConnections(if (Prefs.httpsOnly) GeckoRuntimeSettings.HTTPS_ONLY else GeckoRuntimeSettings.HTTPS_ONLY_PRIVATE)
        runtime.settings.contentBlocking.apply {
            setAntiTracking(if (Prefs.strictTracking) ContentBlocking.AntiTracking.STRICT else ContentBlocking.AntiTracking.DEFAULT)
            setEnhancedTrackingProtectionLevel(if (Prefs.strictTracking) ContentBlocking.EtpLevel.STRICT else ContentBlocking.EtpLevel.DEFAULT)
        }
    }

    private val controller get() = runtime.webExtensionController

    private fun setUpAddons() {
        controller.setPromptDelegate(object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(extension: WebExtension, permissions: Array<String>, origins: Array<String>, dataCollection: Array<String>): GeckoResult<WebExtension.PermissionPromptResponse> {
                if (extension.id in trusted) return GeckoResult.fromValue(WebExtension.PermissionPromptResponse(true, true, false))
                // Installing from addons.mozilla.org ("Add to Firefox"): ask the user, listing what it may do.
                val result = GeckoResult<WebExtension.PermissionPromptResponse>()
                Browser.ask(Ask.InstallAddon(extension, permissions.toList() + origins.toList()) { ok ->
                    result.complete(WebExtension.PermissionPromptResponse(ok, ok, false))
                })
                return result
            }

            override fun onUpdatePrompt(extension: WebExtension, permissions: Array<String>, origins: Array<String>, dataCollection: Array<String>): GeckoResult<AllowOrDeny> {
                if (extension.id in trusted) return GeckoResult.allow()
                val result = GeckoResult<AllowOrDeny>()
                Browser.ask(Ask.UpdateAddon(extension, permissions.toList() + origins.toList()) { ok -> result.complete(if (ok) AllowOrDeny.ALLOW else AllowOrDeny.DENY) })
                return result
            }

            override fun onOptionalPrompt(extension: WebExtension, permissions: Array<String>, origins: Array<String>, dataCollection: Array<String>): GeckoResult<AllowOrDeny> {
                val result = GeckoResult<AllowOrDeny>()
                Browser.ask(Ask.UpdateAddon(extension, permissions.toList() + origins.toList(), optional = true) { ok -> result.complete(if (ok) AllowOrDeny.ALLOW else AllowOrDeny.DENY) })
                return result
            }
        })
        controller.setAddonManagerDelegate(object : WebExtensionController.AddonManagerDelegate {
            override fun onInstalled(extension: WebExtension) = refreshAddons()
            override fun onUninstalled(extension: WebExtension) = refreshAddons()
            override fun onEnabled(extension: WebExtension) = refreshAddons()
            override fun onDisabled(extension: WebExtension) = refreshAddons()
            override fun onReady(extension: WebExtension) = attach(extension)
        })
        controller.list().accept({ list ->
            replaceAddons(list ?: emptyList())
            list?.forEach(::attach)
            if (!Prefs.ublockInstalled && list?.none { it.id == UBLOCK_ID } == true) installUblock()
            if (list?.any { it.id == UBLOCK_ID } == true) Prefs.ublockInstalled = true
            updateAddonsIfDue(list ?: emptyList())
        }, { e -> Log.w("VoidWeb", "Could not list add-ons", e) })
    }

    /** Add-ons can open tabs (their settings pages, "open in new tab" links); give them real tabs. */
    private fun attach(ext: WebExtension) {
        ext.tabDelegate = object : WebExtension.TabDelegate {
            override fun onNewTab(source: WebExtension, details: WebExtension.CreateTabDetails): GeckoResult<org.mozilla.geckoview.GeckoSession> {
                // The engine opens the session it gets back, so the tab must not open it first.
                val tab = Browser.newTab(null, private = false, select = details.active != false, openedByEngine = true)
                details.url?.let { tab.url = it }
                return GeckoResult.fromValue(tab.session)
            }

            override fun onOpenOptionsPage(source: WebExtension) {
                source.metaData.optionsPageUrl?.let { Browser.newTab(it, private = false) }
            }
        }
    }

    private fun installUblock() {
        controller.install(UBLOCK_XPI, WebExtensionController.INSTALLATION_METHOD_MANAGER).accept({ ext ->
            Prefs.ublockInstalled = true
            Log.i("VoidWeb", "uBlock Origin ${ext?.metaData?.version} installed")
            refreshAddons()
        }, { e ->
            // Offline on first launch: try again next time.
            Log.w("VoidWeb", "Could not install uBlock Origin yet", e)
        })
    }

    /** Add-ons from addons.mozilla.org get updates there; check once a day. */
    private fun updateAddonsIfDue(list: List<WebExtension>) {
        val now = System.currentTimeMillis()
        if (now - Prefs.lastAddonUpdate < ADDON_UPDATE_EVERY_MS) return
        Prefs.lastAddonUpdate = now
        for (ext in list) if (!ext.isBuiltIn) controller.update(ext).accept({ if (it != null) refreshAddons() }, { e -> Log.w("VoidWeb", "Add-on update failed: ${ext.id}", e) })
    }

    fun refreshAddons() {
        controller.list().accept({ replaceAddons(it ?: emptyList()) }, { })
    }

    private fun replaceAddons(list: List<WebExtension>) {
        addons.clear()
        addons.addAll(list.filter { !it.isBuiltIn }.sortedBy { it.metaData.name ?: it.id })
    }

    fun setEnabled(ext: WebExtension, on: Boolean) {
        val r = if (on) controller.enable(ext, WebExtensionController.EnableSource.USER) else controller.disable(ext, WebExtensionController.EnableSource.USER)
        r.accept({ refreshAddons() }, { })
    }

    fun setAllowedInPrivate(ext: WebExtension, on: Boolean) {
        controller.setAllowedInPrivateBrowsing(ext, on).accept({ refreshAddons() }, { })
    }

    fun uninstall(ext: WebExtension) {
        controller.uninstall(ext).accept({ refreshAddons() }, { })
    }

    /** Install an add-on from an .xpi link (used by the Add-ons screen's "Reinstall uBlock Origin"). */
    fun install(url: String, onDone: (Boolean) -> Unit) {
        controller.install(url, WebExtensionController.INSTALLATION_METHOD_MANAGER).accept({ refreshAddons(); onDone(true) }, { onDone(false) })
    }

    fun reinstallUblock(onDone: (Boolean) -> Unit) = install(UBLOCK_XPI) { ok -> if (ok) Prefs.ublockInstalled = true; onDone(ok) }
}
