# ProGuard rules for NAS Music TV

# Keep data classes
-keep class com.lnh.nasmusictv.data.model.** { *; }
-keep class com.lnh.nasmusictv.data.prefs.** { *; }
-keep class com.lnh.nasmusictv.data.stats.** { *; }
-keep class com.lnh.nasmusictv.backend.** { *; }

# 百度网盘 DTO 类（显式 keep，防御 Gson 类型擦除/R8 收缩——v2.5.1 曾因此崩溃；
# 与上方 backend.** 宽规则冗余但明确，新增序列化模型时勿删）
-keep class com.lnh.nasmusictv.backend.network.baidu.** { *; }

# Gson
-keep class com.google.gson.** { *; }

# ⛔ 泛型签名（修复）：`object : TypeToken<Map<String, X>>() {}.type` 依赖
# class 文件里的 Signature 属性。R8 默认会剥掉它 ⇒ release 包运行时报
# `IllegalStateException: TypeToken must be created with a type argument`。
# 影响面：LyricsPersistentCache / CoverUrlPersistentCache / MvPersistentCache /
# BaiduFileIndexCache / AppPreferences 的历史与统计反序列化等**全部 TypeToken 调用点**。
# 该问题只在 release 出现，debug 与单测都测不到 —— 由「日志」页在真机上抓到。
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses,EnclosingMethod
# TypeToken 的匿名子类必须连签名一起保留（只保留 Gson 自身类不够）
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ExoPlayer
-keep class androidx.media3.** { *; }

# ZXing (二维码生成)
-keep class com.google.zxing.** { *; }

# NanoHTTPD (本地 HTTP server，实际 Java 包为 fi.iki.elonen)
-keep class fi.iki.elonen.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ONNX Runtime (Spleeter ONNX 人声分离)
-keep class ai.onnxruntime.** { *; }
-keep class com.microsoft.onnxruntime.** { *; }

# Strip debug logs in release builds (ProGuard removes the entire Log.d/v call,
# including string computation — more efficient than runtime if(BuildConfig.DEBUG) checks)
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# jaudiotagger — R8 下反射访问字段需要保留
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# SLF4J（jcifs-ng 传递引入 slf4j-api:1.7.36）—— ⛔ 2026-11-01 修复 release 构建失败。
# 症状：`assembleRelease` 在 `:app:minifyReleaseWithR8` 报错
#   `ERROR: R8: Missing class org.slf4j.impl.StaticLoggerBinder`
#   （referenced from: void org.slf4j.LoggerFactory.bind() and 3 other contexts）
# 根因：slf4j-api 1.7.36 通过反射查找 `org.slf4j.impl.StaticLoggerBinder` 绑定实现类，
# 而 Android 端没有 slf4j binding 实现（也不需要——业务代码零直接使用 slf4j，
# 仅 jcifs-ng 内部用于日志），R8 视其为缺失类而中止构建。
# 该缺陷为**预存**（与 2026-11-01 的 TV-only 改造无关）：在原始提交 06f1c76 上
# 复现同样报错，此前 CI 的 build job 因签名 fail-fast 提前退出而从未暴露。
# 处置：按 R8 自身生成的 missing_rules.txt 建议忽略该缺失类。
# ⚠️ 若将来引入真正的 slf4j binding（如 logback-android），需删掉此条并改用对应 keep 规则。
-dontwarn org.slf4j.**

# 音乐可视化（visualizer 包）——20 套渲染器经 VisualizerRendererFactory 按枚举名创建，
# 且 AudioFrame/CoverPalette 参与跨层引用；枚举名会持久化到 DataStore（visualizer_theme /
# visualizer_quality）。R8 收缩枚举常量或重命名后会导致主题解析失败 / 渲染器创建失败。
# 与 v2.5.1 的 Gson 类型擦除崩溃同类，新增效果时勿删。
-keep class com.lnh.nasmusictv.visualizer.** { *; }
-keepclassmembers enum com.lnh.nasmusictv.data.model.VisualizerTheme { *; }
-keepclassmembers enum com.lnh.nasmusictv.data.model.VisualQuality { *; }
-keepclassmembers enum com.lnh.nasmusictv.data.model.VisualizerTheme$Tier { *; }
# Android 上不存在 java.awt / javax.imageio，忽略引用
-dontwarn java.awt.**
-dontwarn javax.imageio.ImageIO
-dontwarn javax.imageio.stream.ImageInputStream
