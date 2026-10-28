plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.paparazzi)
    alias(libs.plugins.ksp)
    // JSON 序列化编译器插件：为 WebRuntimeConfig / ProjectEntry / WebSettings 生成 @Serializable 编解码
    alias(libs.plugins.kotlin.serialization)
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
        versionCode = 11
        versionName = "0.0.0.1-rc8"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(mmaKeystorePath!!)
                storePassword = mmaKeystorePassword
                keyAlias = mmaKeyAlias
                keyPassword = mmaKeyPassword
                // 签名方案 v1 + v2 + v3 全开：v1 兼容老设备、v2 防篡改、
                // v3 支持密钥轮换；三者齐备覆盖最广安装场景（用户明确要求）。
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // 正式发布配置：关闭 debuggable、开启 R8 压缩与资源压缩。
            // （诊断版曾临时开启 debuggable + 关闭 minify 以定位 rc3 闪退，验证后已回滚。）
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
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

    // SQLCipher 原生库打包：AGP 8 默认不提取原生库，会导致运行时
    // UnsatisfiedLinkError；按 Zetetic 官方建议启用 legacy 打包。
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // MigrationTestHelper 依赖 android.app.Instrumentation 与 assets 中的 schema JSON，
    // 二者在 Robolectric 单元测试环境下均不具备，故迁移测试改用「Room 自身执行迁移 +
    // 运行期 onValidateSchema 强校验 + 与 app/schemas 下 KSP 导出 JSON 逐字比对」的等价路径
    // （见 app/src/test/java/com/make/apk/data/Migration_2_3_Test.kt）。
    // 迁移所需依赖：room-runtime + androidx.sqlite（已由 implementation 提供）。
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    // 顶栏 tab 滑动切页（阶段 6.4）：HorizontalPager 落在 foundation 内（Compose 1.6+）
    implementation(libs.androidx.foundation)
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

    // ── 统一内核契约：WebRuntimeConfig JSON 序列化 ─────────────────────
    // 本地渲染器与导出模板壳共用同一份 runtime-config.json（本地注入架构，无云端）。
    implementation(libs.kotlinx.serialization.json)

    // ── 页面骨架：导航 + ViewModel ───────────────────────────────────
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // ── 磨砂玻璃：Haze（顶栏/底栏跨子树真模糊；API<31 自动降级近似）─────
    implementation(libs.haze)
    implementation(libs.haze.materials)

    // 截图回归测试（Paparazzi，纯 JVM，无需模拟器）
    testImplementation(libs.junit) {
        // 去掉 junit 传递的 hamcrest-core 1.3：它与 espresso 依赖的 hamcrest 2.2
        // 提供同名类 org.hamcrest.core.AllOf 且签名不同，会让 Compose UI 测试在
        // 静态初始化阶段抛 NoSuchMethodError。hamcrest 2.2 是 1.3 的超集，去掉无损失。
        exclude(group = "org.hamcrest", module = "hamcrest-core")
    }
    testImplementation(libs.hamcrest)
    testImplementation(libs.paparazzi)
    // 交互回归测试：Compose UI 测试（跑在 Robolectric 上，覆盖 Paparazzi 够不到的点击路径，
    // 如列表页删除的二次确认「确认才删 / 取消不删」）。
    testImplementation(libs.androidx.ui.test.junit4)
    // compose-rule 需要一个空壳 Activity 承载组合，故 debug 变体追加测试用 manifest
    debugImplementation(libs.androidx.ui.test.manifest)
    // `ui-test-android` 的 EspressoLink 静态初始化会碰到 org.hamcrest.Matchers / Espresso，
    // 故单测 classpath 也必须有 espresso-core（只补依赖，不引入任何 Espresso 用法）。
    testImplementation(libs.androidx.test.espresso.core)
    // 启动路径集成测试（Robolectric：真跑 Application/Activity 生命周期，覆盖 Paparazzi 盲区）
    testImplementation(libs.robolectric)
    // Room 迁移测试：让 Room 真实执行 v2→v3 迁移并做运行期 schema 强校验（§8.4）
    // 注：未使用官方 MigrationTestHelper——它依赖 Instrumentation 与 assets 中的 schema JSON，
    //     在 Robolectric 单元测试环境下均不可用（详见 Migration_2_3_Test.kt 头注释）。
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.androidx.test.rules)
    // ViewModel 单测：runTest + Dispatchers.setMain（viewModelScope 需要主线程调度器）
    testImplementation(libs.kotlinx.coroutines.test)
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
