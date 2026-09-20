plugins {
    id("com.android.application")
}

android {
    namespace = "com.vhx.chessengine"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.vhx.chessengine"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            minifyEnabled = false
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
