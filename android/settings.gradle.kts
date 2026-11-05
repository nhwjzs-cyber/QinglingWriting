// 轻灵写作 Android 工程 — settings(docs/03 §2 / §5)
// 仓库固定:依赖解析只用 google() + Maven Central;插件解析另加 Gradle Plugin Portal。
// 禁止动态版本:所有依赖版本固定在 gradle/libs.versions.toml,依据见 docs/toolchain.md。
//
// 【镜像替换(2026-10-31 构建修复;2026-10-31 M1 扩展)】本机网络到 repo.maven.apache.org 的 TLS
// 握手被远端直接终止(curl 实测 exit 35),Maven Central 与 Plugin Portal 直连不可用;M1 首轮门禁又
// 实测 dl.google.com 对 Gradle 客户端的 TLS 握手间歇性被终止("Remote host terminated the
// handshake",docs/m1/build-logs/gate-round-1.log)。故镜像升为第一顺位:阿里云 google/public/
// gradle-plugin 为主,官方 google()/gradlePluginPortal() 保留为镜像缺构件时的兜底(404 会继续
// 查下一仓库)。坐标与解析结果不变;依据见 docs/toolchain.md §4 与 M1 验收记录。

pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google") // == google() 的镜像(第一顺位,M1)
        google()
        maven("https://maven.aliyun.com/repository/public") // == mavenCentral() 的镜像
        maven("https://maven.aliyun.com/repository/gradle-plugin") // == gradlePluginPortal() 的镜像
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google") // == google() 的镜像(第一顺位,M1)
        google()
        maven("https://maven.aliyun.com/repository/public") // == mavenCentral() 的镜像
    }
}

rootProject.name = "qingling-writing"
include(":app")
