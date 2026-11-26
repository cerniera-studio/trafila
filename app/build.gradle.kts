plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cerniera.trafila"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cerniera.trafila"
        minSdk = 31
        targetSdk = 35
        // Each GitHub build gets a higher number so it installs as an update
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    // One fixed key for every build, so new APKs install over the old one. The key file is made
    // once by the build workflow and encrypted with the cerniera-studio org secret
    // CERNIERA_SIGNING_PASSWORD; the password never leaves GitHub. Without it (a local build)
    // Android's own debug key is used, and that APK will not install over the phone's copy.
    val signingPassword = System.getenv("CERNIERA_SIGNING_PASSWORD")
    val keyFile = rootProject.file("signing/trafila-2026.p12")
    signingConfigs {
        if (!signingPassword.isNullOrBlank() && keyFile.exists()) getByName("debug") {
            storeFile = keyFile
            storeType = "pkcs12"
            storePassword = signingPassword
            keyAlias = "trafila"
            keyPassword = signingPassword
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // Cerniera's Kit: colours, type, controls and crash reports, shared by the family
    implementation(project(":kit"))
    testImplementation("junit:junit:4.13.2")
}
