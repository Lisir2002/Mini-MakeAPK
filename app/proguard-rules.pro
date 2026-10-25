# MMA Release 构建规则（R8 full mode，AGP 8.5 默认 fullMode）
# 设计原则：库方（Compose/Room/协程/Tink）均自带 consumer rules，此处只补
# 本项目自有边界——崩溃可读性 + 反射/序列化边界 + JNI 边界。

# ── 崩溃栈可读性：保留文件名与行号，栈名统一为 SourceFile ──────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Room：实体/DAO/数据库的反射与生成代码边界 ─────────────────────
-keep class com.make.apk.data.local.entity.** { *; }
-keep class com.make.apk.data.local.dao.** { *; }
-keep class com.make.apk.data.local.AppDatabase { *; }
-dontwarn androidx.room.**

# ── SQLCipher（net.zetetic）：JNI 边界，防原生方法被改名 ──────────
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# ── Tink（SQLCipher 加密栈传递依赖）：编译期注解类运行时不存在 ────
# com.google.crypto.tink 引用 errorprone/javax.annotation.concurrent 的
# 注解做静态检查，APK 里没有这些类，R8 full mode 会报 missing class。
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.concurrent.**

# ── ViewModel 手工 DI 工厂（直接引用，保守保留） ──────────────────
-keep class com.make.apk.ui.viewmodel.** { *; }

# ── kotlinx.serialization（security-crypto/Tink 传递依赖） ────────
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.make.apk.** {
    *** Companion;
}
-keepclasseswithmembers class com.make.apk.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── 协程内部调度器（库方 consumer rules 已覆盖，双保险） ──────────
-keep class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-dontwarn kotlinx.coroutines.**

# ── 日志/工具层无反射，无需保留；枚举由转换器直接引用，R8 自动处理 ──
