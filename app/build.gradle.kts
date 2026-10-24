plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.paparazzi)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.web2apk.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.web2apk.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    // Paparazzi 在 JVM 上渲染 Android 资源/主题，需要包含 Android 资源
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    // Compose BOM 统一管理 ui/material3 等版本（禁止零散写版本号）
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // ── 数据层：Room（SQLite ORM）────────────────────────────────────
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    // room-paging：让 DAO 直接返回 PagingSource（构建历史分页加载）
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    // ── 加密 SQLite 引擎：SQLCipher ───────────────────────────────────
    // 自带 SupportOpenHelperFactory（implements androidx.sqlite.db.SupportSQLiteOpenHelper.Factory），
    // 直连 AndroidX Room 2.x，无需手写桥接。
    // 排除其传递的 kotlin-stdlib 2.2.x：与本项目 Kotlin 1.9.24 元数据不兼容，
    // 由 Kotlin 插件自带的 1.9.24 stdlib 提供即可。
    implementation(libs.sqlcipher.android) {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }

    // 安全存储：SQLCipher 口令由 Android Keystore 加密后落盘
    implementation(libs.androidx.security.crypto)

    // 协程 / 分页：Room suspend + Flow 响应式查询
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.paging.runtime)

    // 截图回归测试（Paparazzi，纯 JVM，无需模拟器）
    testImplementation(libs.junit)
    testImplementation(libs.paparazzi)
}

// Room 编译期配置：导出 schema 便于版本迁移审计（输出到 app/schemas/）
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

// Kotlin 1.9.24 编译器**无法读取** Kotlin 2.x 的元数据（binary metadata 2.1.0 vs 期望 1.9.0）。
// 任一传递依赖把 stdlib 抬到 2.x 都会让 KSP/Kotlin 编译直接失败，故强制对齐到插件版本。
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
        force("org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24")
    }
}
