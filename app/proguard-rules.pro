# BabyCare ProGuard Rules
# 默认规则：不混淆，保留所有代码（isMinifyEnabled = false 时未使用，但文件必须存在）

-keep class com.babycare.** { *; }
-keepclassmembers class com.babycare.** { *; }
-dontwarn com.babycare.**
