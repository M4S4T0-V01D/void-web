package dev.m4s4t0.voidweb

import android.app.Application
import kotlinx.coroutines.launch

class WebApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The engine runs pages in its own child processes, which start this class too; only the main
        // process runs the browser.
        if (getProcessName() != packageName) return
        Prefs.init(this)
        Themes.init(this)
        Sounds.init(this)
        Store.init(this)
        Engine.init(this)
        Browser.init(this)
        Browser.scope.launch { Updater.check(this@WebApp, quiet = true) }
    }
}
