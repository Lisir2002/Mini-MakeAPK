plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.paparazzi)
    alias(libs.plugins.ksp)
}

// ── Release 签名（环境变量驱动，密钥不落地）──────────────────────
// 约定：由 CI 或本地环境提供以下变量，仓库/日志/CI 配置中不出现任何口令：
//   MMA_KEYSTORE_FILE      密钥库文件路径（.jks）
//   MMA_KEYSTORE_PASSWORD  密钥库口令
//   MMA_KEY_ALIAS          密钥别名
//   MMA_KEY_PASSWORD       密钥口令
// 四者齐备 → release 自动签名；缺任一 → release 保持未签名（自分发/内测场景）。
// GitHub 端对应 Secret：MMA_KEYSTORE_BASE64 / MMA_KEYSTORE_PASSWORD / MMA_KEY_ALIAS / MMA_KEY_PASSWORD，
// 由 .github/workflows/release.yml 解码后注入环境变量。
// 注意：以下变量名刻意不带 keyAlias/keyPassword/storePassword 等与
// SigningConfig 成员同名的拼写——Kotlin 会把 lambda 内同名裸标识符解析到
// 接收者属性上，导致 `this.keyAlias = keyAlias` 实际是自赋 null（2026-10-24 实测踩坑）。
val mmaKeystorePath: String? = System.getenv("MMA_KEYSTORE_FILE")
val mmaKeystorePassword: String? = System.getenv("MMA_KEYSTORE_PASSWORD")
val mmaKeyAlias: String? = System.getenv("MMA_KEY_ALIAS")
val mmaKeyPassword: String? = System.getenv("MMA_KEY_PASSWORD")
val hasReleaseSigning: Boolean =
    listOf(mmaKeystorePath, mmaKeystorePassword, mmaKeyAlias, mmaKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "com.make.apk"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.make.apk"
        minSdk = 24
        targetSdk = 34
        // ── 版本约束（唯一改动点）────────────────────────────────────
        // versionName：纯四段式 MAJOR.MINOR.PATCH.BUILD，可带 -rcN 后缀（如 0.0.0.1-rc1）。
        //   正式版需用户授权，未授权按顺延下一版发 RC（docs/VERSIONING.md §3）。
        //   版本号不带 +N 后缀（2026-10-25 起，用户确认）。
        // versionCode：Android 安装唯一性要求的内部整数，单调递增，但不进版本串/tag；
        //   发布 tag = v<versionName>。递增规则与发版流程见 docs/VERSIONING.md。
        versionCode = 6
        versionName = "0.0.0.1-diag2"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(mmaKeystorePath!!)
                storePassword = mmaKeystorePassword
                keyAlias = mmaKeyAlias
                keyPassword = mmaKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // ── 诊断版临时配置（rc3 装机仍闪退定位用，验证后回滚）──────────
            // isDebuggable：adb run-as 可读私有目录 + 诊断对话框守卫；
            // 关 minify/shrink：崩溃堆栈保持原始类名/方法名/行号，可读。
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        // AGP 8 默认关闭 BuildConfig 生成；ProfileScreen 版本展示与发版校验需要 VERSION_NAME
        buildConfig = true
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
    // 图标全部走 res/drawable 下的 SVG 矢量（见 ui/icons/MmaIcons.kt），
    // 不引 material-icons-extended——整库数 MB 且本应用零使用（2026-10-25 移除）。

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

    // ── 页面骨架：导航 + ViewModel ───────────────────────────────────
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // ── 磨砂玻璃：Haze（顶栏/底栏跨子树真模糊；API<31 自动降级近似）─────
    implementation(libs.haze)
    implementation(libs.haze.materials)

    // 截图回归测试（Paparazzi，纯 JVM，无需模拟器）
    testImplementation(libs.junit)
    testImplementation(libs.paparazzi)
    // 启动路径集成测试（Robolectric：真跑 Application/Activity 生命周期，覆盖 Paparazzi 盲区）
    testImplementation(libs.robolectric)
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
