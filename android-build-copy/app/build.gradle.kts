plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.thisko.qringprint"
    compileSdk = 35

    defaultConfig {
        applicationId = "huanxongkuaiyin.com"
        minSdk = 26
        targetSdk = 34
        versionCode = 41
        versionName = "4.1.0"
    }

    // Release 签名 —— 从 gradle.properties 读取,避免密码硬编码进 git
    signingConfigs {
        create("release") {
            val storeFileProp = providers.gradleProperty("QRINGPRINT_RELEASE_STORE_FILE")
            val storePassProp = providers.gradleProperty("QRINGPRINT_RELEASE_STORE_PASSWORD")
            val keyAliasProp = providers.gradleProperty("QRINGPRINT_RELEASE_KEY_ALIAS")
            val keyPassProp = providers.gradleProperty("QRINGPRINT_RELEASE_KEY_PASSWORD")
            // 缺一个就跳过签名配置,允许不签名打 release(方便本机调试)
            if (storeFileProp.isPresent && storePassProp.isPresent && keyAliasProp.isPresent && keyPassProp.isPresent) {
                storeFile = file(storeFileProp.get())
                storePassword = storePassProp.get()
                keyAlias = keyAliasProp.get()
                keyPassword = keyPassProp.get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // release 走专属 signingConfig,没配时退回无签名(assembleRelease 仍能跑,但会生成 unsigned apk)
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // 保留 ONNX Runtime / OpenCV 的 .so 原样打入 APK，不压缩（4KB 对齐要求）
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.opencsv:opencsv:5.9")
    implementation("com.github.mik3y:usb-serial-for-android:3.7.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.1.0")

    // === PaddleOCR (本地 OCR，可与百度二选一) ===
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.18.0")
    implementation("org.opencv:opencv:4.10.0")


    debugImplementation("androidx.compose.ui:ui-tooling")
}

