// 顶层构建脚本(docs/03 §2、§5)
//
// 【AGP 9 内置 Kotlin,重要】AGP 9.0 起内置 Kotlin 支持(android.builtInKotlin 默认 true):
//   * 不再、也不能应用 org.jetbrains.kotlin.android —— 否则报
//     "The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0."
//     (官方迁移指南:developer.android.com/build/migrate-to-built-in-kotlin)
//   * AGP 9.0 发布说明「Runtime dependency on Kotlin Gradle plugin」一节给出的官方途径:
//     要使用高于内置默认(AGP 9.0 为 KGP 2.2.10)的 Kotlin,在顶层 buildscript 中声明
//     classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:<version>")。
//   * 本工程按锁定的工具链矩阵(2026-10-31,见 docs/toolchain.md)把 Kotlin 固定到 2.4.21,
//     与 org.jetbrains.kotlin.plugin.compose 2.4.21 同版本(Compose 编译器插件必须与 Kotlin 同版本)。
// 版本目录(version catalog)在 buildscript 块内不可用,故此处显式写死:
// 若调整 Kotlin 版本,必须同步修改 gradle/libs.versions.toml 的 [versions].kotlin 与 docs/toolchain.md。
// buildscript 仓库与 settings 同策略:central 用阿里云镜像(直连 TLS 握手被终止,
// 2026-10-31 curl 实测,见 settings.gradle.kts 顶部说明)。
buildscript {
    repositories {
        google()
        maven("https://maven.aliyun.com/repository/public") // == mavenCentral() 的镜像
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // M1 接入 Room:KSP 2.3.13(libs.versions.toml 锁定;与 Kotlin 2.4.21 的配对以本轮实际构建验证,
    // 结果回填 docs/toolchain.md §4)
    alias(libs.plugins.ksp) apply false
}

// Wrapper 分发地址(2026-10-31 构建修复):本机 JVM 对 services.gradle.org 的
// wrapper URL 校验/下载不稳定(上游 curl 下载实测 exit 52;wrapper 任务 HEAD 校验同样失败),
// 故 distributionUrl 固定为腾讯镜像。镜像发行版与官方逐字节一致:
// SHA256 21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607
// (certutil 实测,与官方 services.gradle.org/distributions/gradle-9.8.2-bin.zip.sha256 一致;
// 该 sha 经命令行 --gradle-distribution-sha256-sum 传入,由 wrapper 任务写入
// gradle-wrapper.properties 的 distributionSha256Sum,下载时仍做完整性校验)。
tasks.named<org.gradle.api.tasks.wrapper.Wrapper>("wrapper") {
    distributionUrl = "https://mirrors.cloud.tencent.com/gradle/gradle-9.8.2-bin.zip"
}
