plugins {
    id("com.android.application")
}

android {
    namespace = "com.jongjun.trip"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jongjun.trip"
        minSdk = 26
        targetSdk = 34
        val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = runNumber
        versionName = "1.$runNumber"
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "hangeoreum1"
            keyAlias = "hangeoreum"
            keyPassword = "hangeoreum1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    androidResources {
        noCompress += listOf("otf")
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
