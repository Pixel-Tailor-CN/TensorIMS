plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val packageName = "app.mystery0.ims.tensor"
val gitVersionCode: Int = providers.exec {
    commandLine(
        "git",
        "rev-list",
        "HEAD",
        "--count"
    )
}.standardOutput.asText.get().trim().toInt()
val gitVersionName: String =
    providers.exec {
        commandLine(
            "git",
            "rev-parse",
            "--short=8",
            "HEAD"
        )
    }.standardOutput.asText.get().trim()
val appVersionName: String = libs.versions.app.version.get()
val nativeStarterEmulator = providers.gradleProperty("tensorimsNativeStarterEmulator")
    .map(String::toBoolean).getOrElse(false)
val requestedNativeBuildTasks = gradle.startParameter.taskNames
val emulatorSafeNativeBuildTasks = setOf(
    ":app:assembleTensorDebug",
    ":app:assembleLegacyDebug",
)
if (nativeStarterEmulator &&
    (requestedNativeBuildTasks.isEmpty() || requestedNativeBuildTasks.any { it !in emulatorSafeNativeBuildTasks })
) {
    throw GradleException(
        "tensorimsNativeStarterEmulator requires explicit :app:assembleTensorDebug and/or :app:assembleLegacyDebug tasks"
    )
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}

android {
    namespace = packageName
    compileSdk {
        version = release(libs.versions.android.compileSdk.get().toInt())
    }
    defaultConfig {
        applicationId = packageName
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = gitVersionCode
        versionName = appVersionName
        ndk {
            abiFilters.add("arm64-v8a")
            if (nativeStarterEmulator) abiFilters.add("x86_64")
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DANDROID_STL=c++_static"
            }
        }
    }
    ndkVersion = libs.versions.android.ndk.get()
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = libs.versions.android.cmake.get()
        }
    }
    // 仅区分安装身份；两个 APK 共用完整的官方/内置双模式实现。
    flavorDimensions += "identity"
    productFlavors {
        create("tensor") {
            dimension = "identity"
            applicationId = packageName
        }
        create("legacy") {
            dimension = "identity"
            applicationId = "io.github.vvb2060.ims"
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Bouncy Castle 多个模块携带同名许可文件，合并保留全部声明。
            merges += "META-INF/LICENSE.md"
        }
    }
    signingConfigs {
        create("sign")
    }
    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            @Suppress("UnstableApiUsage")
            vcsInfo.include = false
            versionNameSuffix = ".d$gitVersionCode.$gitVersionName"
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            @Suppress("UnstableApiUsage")
            vcsInfo.include = false
            proguardFiles("proguard-rules.pro")
            versionNameSuffix = ".r$gitVersionCode.$gitVersionName"
            signingConfig = signingConfigs.getByName("sign")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        aidl = true
        buildConfig = true
        compose = true
    }
    lint {
        checkReleaseBuilds = false
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    @Suppress("UnstableApiUsage")
    androidResources {
        localeFilters.add("en")
        localeFilters.add("zh-rCN")
    }
}

dependencies {
    compileOnly(project(":stub"))
    implementation(libs.shizuku.provider)
    implementation(libs.shizuku.api)
    implementation(libs.hiddenapibypass)
    implementation(libs.bcpkix)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.material.icons.core)
    implementation(libs.material.icons.extended)
    implementation(libs.androidx.splashscreen)
    implementation(libs.lifecycle.viewmodel.ktx)

    testImplementation(libs.junit)
}

apply(from = rootProject.file("signing.gradle"))
