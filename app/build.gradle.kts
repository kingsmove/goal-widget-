plugins { id("com.android.application") }
android {
    namespace = "com.jongjun.voca"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.jongjun.voca"
        minSdk = 29
        targetSdk = 34
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = run
        versionName = "1.$run"
    }
    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "vocavoca1"
            keyAlias = "voca"
            keyPassword = "vocavoca1"
        }
    }
    buildTypes { release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("release") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    lint { checkReleaseBuilds = false; abortOnError = false }
}
