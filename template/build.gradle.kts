plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.paparazzi)
}

// ── 子模板（插件式注入）落地模块 ─────────────────────────────────────
// 本模块**不复制**任何壳源码：ui/shell 与 data/model 的壳契约/渲染器
// 通过 sourceSets 直接指向 app 的同一份物理文件（杜绝副本漂移）。
// 因此「预览（app）== 导出（template）⟺ 同一份 ShellSpec + 同一份 ShellHost」
// 在真实 APK 产物上可被同一编译器、同一份源码验证。
//
// 关键约束（见主理人方案）：
// - namespace 与 app 相同（com.make.apk），否则壳源码里写死的 `import com.make.apk.R`
//   在模板模块编译不过；
// - applicationId 与 app 不同（com.make.apk.template），避免安装冲突；
// - 不得引入 app 业务层（Room / SQLCipher / 导航 / ViewModel）。

android {
    // namespace 必须与 app 相同：壳源码 import com.make.apk.R 依赖此包名生成的 R 类。
    namespace = "com.make.apk"
    compileSdk = 34

    defaultConfig {
        // applicationId 与 app 区分，避免同一设备安装冲突。
        applicationId = "com.make.apk.template"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.0.0.1"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    // 截图回归测试（Paparazzi，纯 JVM）需要包含 Android 资源（R.raw.shell_spec 等）。
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        // `android.sourceSets.getByName("main")` 返回 AGP DSL 接口类型，Kotlin 插件的
        // `kotlin` 扩展（AndroidSourceSet.kotlin 返回 KotlinSourceSet）对该接口类型不可见，
        // 会被 Gradle 的 `kotlin {}` Project 扩展函数遮蔽；且 Kotlin 编译器只按 KotlinSourceSet
        // 的 srcDirs/exclude 过滤（java 源集的 exclude 不被采纳）。故统一用 Kotlin 插件的
        // `kotlin.sourceSets["main"].kotlin`（内嵌 SourceDirectorySet，实现 PatternFilterable）来
        // 加目录与剔除文件。res 仍走 android 的 main.res。
        val main = getByName("main")
        val kotlinMain = kotlin.sourceSets.getByName("main")
        // ── 壳渲染器 + 壳契约/模型：与 app 同一份物理源码（杜绝副本漂移）──
        kotlinMain.kotlin.srcDirs(
            "../app/src/main/java/com/make/apk/ui/shell",
            "../app/src/main/java/com/make/apk/data/model",
            // WebViewScreen 单文件（不整目录引入其它 screen，避免导航/主题依赖）
            "../app/src/main/java/com/make/apk/ui/screens",
        )

        // ── 剔除非壳类与无关 screen ──
        // 内嵌 SourceDirectorySet 实现了 Gradle PatternFilterable（含 exclude）。
        // glob 以各 srcDir 根为基准（**/ 前缀兼容零层与多层，且对绝对化路径同样生效）。
        // 注意：Project.kt（内含 ProjectDefaults，ShellHost 必需）与
        // VersionScheme.kt（WebRuntimeConfig 必需字段类型）均为纯数据、
        // 零 Room/SQLCipher 依赖，故**保留**编译（从排除列表移除）。
        (kotlinMain.kotlin as org.gradle.api.tasks.util.PatternFilterable).exclude(
            listOf(
                // data/model 中 App 业务层（Room/SQLCipher 重依赖）——剔除：
                "**/LogEntry.kt",
                "**/ExportRun.kt",
                // ui/screens 里除 WebViewScreen 外的 screen 全部剔除
                //（它们依赖导航/ViewModel/app 主题，模板不引入）：
                "**/AddProjectScreen.kt",
                "**/DevelopingScreen.kt",
                "**/LogViewerScreen.kt",
                "**/ProfileItem.kt",
                "**/ProfileScreen.kt",
                "**/ProjectsScreen.kt",
                "**/ProjectEditScreen.kt",
                "**/ShellPreviewScreen.kt",
                "**/*Activity*.kt",
            ),
        )

        // 资源：复用 app 的 res（含 ic_menu / ic_arrow_back / ic_launcher 等向量图标与启动图标）。
        // 模板只需补充 raw/shell_spec.json（编译期注入的壳契约）。
        main.res.srcDirs("../app/src/main/res")
    }
}

dependencies {
    // Compose BOM 统一管理 ui/material3 等版本（禁止零散写版本号）
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.material3)

    // 顶栏 tab 滑动切页（HorizontalPager 落在 foundation 内）
    implementation(libs.androidx.foundation)
    // ComponentActivity 承载 Compose 内容
    implementation(libs.androidx.activity.compose)

    // ShellTopTabsPager 使用 snapshotFlow/collect（滑动切页双向同步）
    implementation(libs.kotlinx.coroutines.android)

    // 统一内核契约：解析 shell_spec.json 与 runtime-config（本地注入架构）。
    implementation(libs.kotlinx.serialization.json)

    // ── 截图回归测试（Paparazzi，纯 JVM，无需模拟器）──
    testImplementation(libs.junit) {
        // 同 app：去掉 hamcrest-core 1.3，避免与 espresso 的 hamcrest 2.2 冲突。
        exclude(group = "org.hamcrest", module = "hamcrest-core")
    }
    testImplementation(libs.hamcrest)
    testImplementation(libs.paparazzi)
    testImplementation(libs.kotlinx.serialization.json)
}
