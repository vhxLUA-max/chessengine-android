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
        ndkVersion = "27.3.13750724"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.5"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
