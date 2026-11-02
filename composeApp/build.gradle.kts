import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

// Release signing comes from local.properties (never committed); without it, release builds stay unsigned.
val localProps = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.material3)
            implementation(compose.ui)
            // Compose 1.8 stopped bundling Material icons; core set only (small).
            implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
            implementation(compose.components.resources)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
            implementation("io.coil-kt.coil3:coil-compose:3.2.0")
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.10.1")
            // Document-start scripts for the trailer WebView (codec preference on TVs).
            implementation("androidx.webkit:webkit:1.12.1")
            implementation("io.coil-kt.coil3:coil-network-okhttp:3.2.0")
        }
        // Code shared by the two JVM targets (Android + desktop), e.g. the IMDb ratings file reader.
        val jvmShared by creating { dependsOn(commonMain.get()) }
        androidMain.get().dependsOn(jvmShared)
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
        }
        val desktopMain by getting {
            dependsOn(jvmShared)
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("io.coil-kt.coil3:coil-network-okhttp:3.2.0")
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(compose.desktop.uiTestJUnit4)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

android {
    namespace = "com.movieshelf.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.movieshelf.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.0"
    }
    signingConfigs {
        localProps.getProperty("release.storeFile")?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = localProps.getProperty("release.storePassword")
                keyAlias = localProps.getProperty("release.keyAlias")
                keyPassword = localProps.getProperty("release.keyPassword")
            }
        }
    }
    buildTypes {
        getByName("release") {
            // R8 stays off: kotlinx.serialization + Compose need keep rules that haven't been verified on a TV yet.
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

compose.resources {
    packageOfResClass = "com.movieshelf.app.resources"
}

compose.desktop {
    application {
        mainClass = "com.movieshelf.app.DesktopMainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Padampetti"
            // MSI requires MAJOR > 0.
            packageVersion = "1.0.0"
            // java.prefs: saved settings; jdk.crypto.ec: modern TLS to TMDB in the bundled runtime.
            modules("java.prefs", "java.net.http", "jdk.crypto.ec", "jdk.unsupported")
            windows {
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true
                menuGroup = "Padampetti"
                iconFile.set(project.file("icons/Padampetti.ico"))
                upgradeUuid = "5B7C9E2A-4F1D-4C8B-9A3E-6D2F1B8C7A41"
            }
            description = "Malayalam movie discovery for desktop and Google TV"
            vendor = "Personal Project"
        }
    }
}
