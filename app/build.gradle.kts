// app build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    kotlin("kapt")
}

android {
    namespace = "com.babycare"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.babycare"
        minSdk = 26
        targetSdk = 34
        versionCode = 1005
        versionName = "1.0.0.5"
    }

    buildFeatures {
        viewBinding = true
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
}

// ─── 自定义APP图标：把 custom_icon/ 下的图片自动设置为启动图标 ───
android.applicationVariants.all { variant ->
    val copyCustomIcon by tasks.registering(Copy::class) {
        description = "如果 custom_icon/ 下有图片，则用它替换默认启动图标"
        val customIconDir = rootProject.projectDir.resolve("custom_icon")
        val pngFiles = fileTree(customIconDir).matching { include("*.png", "*.jpg", "*.webp") }
        from(customIconDir)
        include("*.png", "*.jpg", "*.webp")
        into(projectDir.resolve("src/main/res/drawable-nodpi"))
        rename { _ -> "custom_app_icon.png" }
        doLast {
            if (pngFiles.isEmpty()) {
                logger.info("ℹ️ 未在 custom_icon/ 目录中找到图片，使用默认图标")
            } else {
                logger.info("✅ 已使用 ${pngFiles.singleFile} 作为自定义APP图标")
            }
        }
    }
    // 在 mergeResources 之前执行
    variant.mergeResourcesProvider?.let { provider ->
        provider.configure { dependsOn(copyCustomIcon) }
    }
    true // applicationVariants.all 要求返回 Boolean
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.room:room-runtime:2.6.0")
    kapt("androidx.room:room-compiler:2.6.0")
    implementation("androidx.room:room-ktx:2.6.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}