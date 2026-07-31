# ============================================================
# 智能课表 R8 规则
#
# 说明：Activity / Service / BroadcastReceiver / Provider 由 AGP
# 根据 AndroidManifest 自动 keep，不需要手写。
# 本项目不使用反射序列化（数据全部用 org.json 手写读写），
# 因此不需要 keep 数据类，可以让 R8 充分裁剪。
# ============================================================

# 崩溃日志会被 App.kt 写入 last_crash.txt，保留行号方便定位
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Compose / Kotlin 需要的注解信息
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# Kotlin 协程：内部使用 ServiceLoader 加载主线程调度器
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# 编译期注解，运行时不存在
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**

# Compose 自身携带 consumer rules，无需额外配置。
# material / appcompat 里对高版本 SDK 的可选引用
-dontwarn com.google.android.material.**

