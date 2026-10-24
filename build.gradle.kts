// Web2APK 根构建脚本（仅声明插件版本，具体配置在 :app 模块）
// 设计语言与产品事实来源见 PROJECT_BRIEF.md / DESIGN_LANGUAGE.md
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.paparazzi) apply false
}
