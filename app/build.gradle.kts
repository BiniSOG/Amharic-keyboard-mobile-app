import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.binisog.amharickeyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.binisog.amharickeyboard"
        minSdk = 24
        targetSdk = 35
        // CI stamps each build with its run number so installed builds are identifiable.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.1.$build"
        // Size: ship only English and Amharic resources (drops ~80 locales from AndroidX).
        resourceConfigurations += listOf("en", "am")
        vectorDrawables.useSupportLibrary = false
    }

    signingConfigs {
        // Production: your own upload key from CI secrets (see signing/README.md).
        // Otherwise: the committed test key, so test builds always install over each other.
        create("release") {
            val ks = System.getenv("RELEASE_KEYSTORE_FILE")
            if (ks != null && file(ks).isFile) {
                storeFile = file(ks)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            } else {
                storeFile = file("signing/test-release.keystore")
                storePassword = "amharic-keyboard-test"
                keyAlias = "amharic-keyboard-test"
                keyPassword = "amharic-keyboard-test"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // Size: no dependency metadata block, no Kotlin/Java metadata files in the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    packaging {
        resources.excludes += listOf(
            "META-INF/*.version", "META-INF/*.kotlin_module", "META-INF/LICENSE*", "META-INF/NOTICE*",
            "META-INF/AL2.0", "META-INF/LGPL2.1", "kotlin/**", "DebugProbesKt.bin", "**/*.properties",
        )
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = true
        textReport = true
        textOutput = file("stdout")
        disable += listOf("MissingTranslation", "ExtraTranslation")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3") // enableEdgeToEdge(), photo picker
    implementation("androidx.preference:preference-ktx:1.2.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}
