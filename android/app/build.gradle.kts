// :app 模块(docs/03 §2:首版单模块,包内分层)
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose) // Compose 编译器插件,版本与 Kotlin 同为 2.4.21(libs.versions.toml)
    alias(libs.plugins.ksp) // M1:Room 编译处理(ksp 2.3.13,与 Kotlin 2.4.21 配对以实际构建验证)
    // 注意:AGP 9 内置 Kotlin,不应用 org.jetbrains.kotlin.android(应用即报错,见顶层 build.gradle.kts 注释)
}

android {
    namespace = "com.qingling.writing"
    compileSdk = 37 // AGP 9.5 官方支持的最大 API 级别(见 docs/toolchain.md)

    defaultConfig {
        // 正式 applicationId 待负责人确认后统一调整(GLM-START.md 第 1 条)。
        // debug 与 release 共用同一 ID,debug 不加后缀。
        applicationId = "com.qingling.writing"
        minSdk = 26
        targetSdk = 37
        versionCode = 57
        versionName = "0.48.0"
        testInstrumentationRunner = when(providers.gradleProperty("upgradeSeed").orNull) {
            "true" -> "com.qingling.writing.Upgrade034Instrumentation"
            "035" -> "com.qingling.writing.Upgrade035Instrumentation"
            else -> "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    buildTypes {
        create("preview") {
            initWith(getByName("release"))
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug") // Internal upgrade-compatible preview only.
            matchingFallbacks += listOf("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            // M1 仍不启用压缩混淆;启用时需补 proguard-rules.pro
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true // BuildConfig.DEBUG 用于演示数据开关(仅 debug,docs/05 §5)
    }

    // Room 迁移测试:把导出的 schema JSON 作为 androidTest 资产(MigrationTestHelper 读取)
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

// AGP 9 内置 Kotlin:kotlin.compilerOptions.jvmTarget 默认即跟随 compileOptions.targetCompatibility
// (官方迁移文档);此处按官方写法显式声明,与 compileOptions 保持一致(JDK 17 字节码)。
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

// Room schema 导出(docs/03 §2:app/schemas/ 提交 Git;docs/05 §3:保留 schema 导出)
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// G05 依赖校验:采用 gradle/verification-metadata.xml(全构件 SHA256,随源码提交,
// 后续任何换源/换版本都会校验失败)。曾尝试 dependencyLocking { lockAllConfigurations() }
// 生成 gradle.lockfile:在本组合(Gradle 9.8.2 + AGP 9.5 内置 Kotlin + configuration cache)
// 下强制解析(--no-configuration-cache 下 :app:dependencies)也不产出 lockfile,如实记录于
// docs/m0-reviews/M0.1-review-response-2026-10-31.md 的 G05 段,不再保留无效配置。

dependencies {
    implementation(platform(libs.androidx.compose.bom)) // Compose BOM(docs/03 §5)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // M1:本地数据库(docs/03 §3:UI 不直连 DAO,经 domain 仓储)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // 显式锁定 coroutines-core:与 androidTest 的 coroutines-test 保持同一版本,
    // 避免仪器测试加载 app 内旧版 runBlocking 造成 NoSuchMethodError(M1 实测)。
    implementation(libs.kotlinx.coroutines.core)
    // M2:serialization 运行时对齐 room-migration 传递链(1.8.1)——迁移测试 bundle 反序列化在
    // 设备端加载 app 内版本,低于 1.8.1 会 AbstractMethodError(实测)。
    implementation(libs.kotlinx.serialization.core)
    // G04:主题三态偏好持久化(DataStore 只放偏好,docs/03 §6)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    // 迁移测试的 bundle 反序列化依赖 serialization 运行时;版本错配会 AbstractMethodError(M2 实测)
    androidTestImplementation(libs.kotlinx.serialization.core)
}
