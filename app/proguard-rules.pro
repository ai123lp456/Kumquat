# 小金桔：R8 混淆/裁剪规则
-keepattributes *Annotation*, InnerClasses, Signature

# 导入导出的序列化模型（kotlinx.serialization）
-keepclassmembers class com.kumquat.ledger.data.** { *; }
-keepclasseswithmembers class com.kumquat.ledger.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class com.kumquat.ledger.data.** {
    *** Companion;
}
-keep,includedescriptorclasses class com.kumquat.ledger.**$$serializer { *; }
-keepclassmembers class com.kumquat.ledger.** { *** Companion; }
-keepclasseswithmembers class com.kumquat.ledger.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontnote kotlinx.serialization.**
-keepattributes *Annotation*, InnerClasses

# Room 生成的实现类
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# Lottie 自带的 consumer rules 已覆盖大部分；这里保留 JSON 动画解析相关
-dontwarn com.airbnb.lottie.**

# okio 1.17（Lottie 依赖）引用了编译期注解，运行期不需要
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
