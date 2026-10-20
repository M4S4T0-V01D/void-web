plugins {
    id("com.android.application") version "9.4.1" apply false
    // AGP 9 compiles Kotlin itself; declaring the Kotlin plugin (unapplied) picks the Kotlin version. GeckoView's
    // library is built with Kotlin 2.4, so the compiler has to be at least that new.
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
