plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "org.linuxdo.android"
    compileSdk = 35

    // 密钥库是否存在，决定 debug / release 是否绑定签名
    val hasKeystore = System.getenv("KEYSTORE_PATH")?.let { file(it).exists() }
        ?: rootProject.file("keystore/linuxdo-release.jks").exists()

    defaultConfig {
        applicationId = "org.linuxdo.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // 站点地址：默认 linux.do，可在「我的 → 服务器设置」里改，支持自建 Discourse
        buildConfigField("String", "DEFAULT_BASE_URL", "\"https://linux.do\"")
    }

    // 固定签名：CI 从 GitHub Secrets 读，本地从 keystore.properties 读
    // 都没有时 release 走未签名（便于本地 assembleRelease 不报错）
    signingConfigs {
        create("release") {
            // 优先级：环境变量（CI）> gradle property（本地）> 默认路径
            val storeFilePath = System.getenv("KEYSTORE_PATH")
                ?: rootProject.file("keystore/linuxdo-release.jks").absolutePath
            val storeFileObj = file(storeFilePath)
            if (storeFileObj.exists()) {
                storeFile = storeFileObj
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                    ?: (findProperty("keystore.password") as String?)
                keyAlias = System.getenv("KEY_ALIAS")
                    ?: (findProperty("key.alias") as String?)
                    ?: "linuxdo"
                keyPassword = System.getenv("KEY_PASSWORD")
                    ?: (findProperty("key.password") as String?)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasKeystore) signingConfigs.getByName("release") else null
        }
        debug {
            // 不加 applicationIdSuffix：debug 与 release 同包名，
            // 装新的直接覆盖，签名一致才不会冲突
            isMinifyEnabled = false
            // debug 也用同一个签名，方便覆盖安装
            signingConfig = if (hasKeystore) signingConfigs.getByName("release") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/INDEX.LIST"
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")

    implementation("androidx.navigation:navigation-compose:2.8.4")

    // 网络
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // 图片
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // 持久化
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // HTML 渲染（Discourse cooked 内容是 HTML）
    implementation("com.google.android.material:material:1.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
