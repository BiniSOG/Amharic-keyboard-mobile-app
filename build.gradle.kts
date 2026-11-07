// Plugins are put on the root build classpath so that the Kotlin and Android plugins
// share one classloader. The Android Gradle Plugin is only needed (and only resolvable,
// via Google Maven) when the :app module is included - see settings.gradle.kts.
buildscript {
    val kotlinVersion = "2.0.21"
    val agpVersion = "8.7.3"
    val hasApp = file("app/build.gradle.kts").exists() &&
        (System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null ||
            (file("local.properties").takeIf { it.exists() }?.readText()?.contains("sdk.dir") == true))
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        if (hasApp) classpath("com.android.tools.build:gradle:$agpVersion")
    }
}
