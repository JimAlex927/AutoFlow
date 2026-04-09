import org.gradle.kotlin.dsl.implementation

plugins {
    alias(libs.plugins.android.application)

}

android {
    namespace = "com.auto.master"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.auto.master"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Internal testing release: keep release optimizations but sign with the local debug key
            // so the apk can be installed directly on devices.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    androidResources {
        localeFilters += listOf("zh", "en")
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/DEPENDENCIES",
                "META-INF/*.version",
                "META-INF/androidx/**/LICENSE.txt"
            )
        }
    }
}
// 在该模块的 build.gradle 顶
dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("androidx.cardview:cardview:1.0.0")

    implementation("androidx.core:core:1.9.0")
    // 关键这一行：Kotlin DSL 写法
    implementation("org.opencv:opencv:4.13.0")

    implementation("com.github.princekin-f:EasyFloat:1.3.4")

    // String utilities (used by ResponseHandlers)
    implementation("org.apache.commons:commons-lang3:3.12.0")


    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.mozilla:rhino:1.7.15")

//    implementation("io.undertow:undertow-core:2.3.14.Final")
//    implementation("com.github.princekin-f:EasyFloat:2.0.4")

}
