# 轻灵写作

当前交付：**0.45.0 · 异常退出草稿保护版**，versionCode 54、数据库 v10、备份 v7。增加正文、标题、备注与设定的本地原子保护草稿、带版本校验的恢复及冲突副本。三轮基线和复测结果见 [报告](docs/loss045/测试报告.md)。

上一交付：0.44.0 · 使用体验整改版，见 [说明](docs/ux044/CHANGELOG.md)。

上一交付：0.43.0 · 伏笔引用整改版，见 [说明](docs/notes043/CHANGELOG.md)。

上一交付：0.42.1 标题居中与建章优化，见 [说明](docs/title0421/CHANGELOG.md)。

上一交付：0.42.0 标题正文分区，见 [说明](docs/title042/CHANGELOG.md)。

上一交付：0.41.0 共用组件优化，见 [说明](docs/components041/CHANGELOG.md)。

上一交付：0.40.0 评审整改，见 [说明](docs/review040/CHANGELOG.md)。

上一交付：0.39.1 顶栏重叠修复，见 [说明](docs/fix0391/CHANGELOG.md)。

上一交付：0.39.0 · 1104 设计实施版，见 [说明](docs/design039/CHANGELOG.md)。

上一交付：0.38.0 编辑页再分配，见 [说明](docs/design038/CHANGELOG.md)。

上一交付：0.37.0 隐私政策版，见 [说明](docs/design037/CHANGELOG.md)。

上一交付：0.36.0 增补设计优化，见 [说明](docs/design036/CHANGELOG.md)。


上一交付：**0.35.0 设计包优化**（versionCode 39，数据库 v8，备份 v5）。基于用户指定的 0.34.0 源码独立开发。变化、兼容边界和验收见 [本轮说明](docs/design035/CHANGELOG.md) 及 [验证记录](docs/design035/VERIFICATION.md)。下方阶段记录为原项目历史。

Android 手机写作应用：Kotlin、Jetpack Compose、Room，本地优先。

## 当前工作副本

2026-11-01，Codex 从 GLM 工程提交 `4c3e5e9` 接手，保留完整 Git 历史。
当前目录是后续开发主副本；GLM 原目录作为交接基线保留。
分支：`codex/takeover-ux`。当前版本：`0.23.0`（versionCode 27、数据库 v8、备份 v5），新增自动历史保留数量、旧自动版本清理预览与本机存储概览，见 [本轮说明](docs/storage-management/README.md)；延续 0.22 的 [作品管理与字体](docs/shelf-fonts/README.md)、0.21 的 [部分功能补齐](docs/partial-completion/README.md)、0.20 的 [统计与整书目标](docs/writing-dashboard/README.md)。视觉实现边界见 [说明](docs/graphite-v2/README.md)，追加功能进度见 [清单](docs/improvement-checklist-status.md)。云同步继续暂缓。

## 阶段状态

- M0：工程、主题与导航骨架已存在。
- M1：书/卷/章管理、回收站、Room 数据层已存在，先前验收记录见 docs/m1。
- M2：正文自动保存、编辑会话、光标、历史版本已存在；中文 IME 真机验证、滚动恢复等仍需完善。
- UX v0.2：第一轮接手修复侧栏跟手、动画中断、冷启动续写、切章保存门控，详见 docs/codex-takeover.md。
- M3：选章/整卷、顺序与正文预览、系统保存 TXT、安全分享已接通；47 项 JVM 与 3 项设备测试通过，系统保存已读回校验。见 docs/m3/M3-codex-acceptance.md。
- M4：手动独立备份、完整性校验、恢复预览与新副本恢复已接入；0.12.0 新增授权目录、6/24 小时系统任务和启动补尝试。旧备份不自动删除，真机长周期待验收。见 docs/auto-backup/README.md。
- M5：云同步与服务端按用户 2026-11-02 要求暂缓，继续推进本地功能。
- M6：真机兼容性、性能与正式发布验收尚未完成。

阶段表示代码与交付状态，不能视作所有项目已经重新验收通过。

## 构建

安装 Android SDK 与 JDK 21，通过 `android/local.properties` 指定本机 SDK（该文件不入库）。
本机 PowerShell 运行示例：

```powershell
$env:JAVA_HOME = 'C:\JAVA'
$env:GRADLE_USER_HOME = 'C:\Users\HINA\.gradle'
.\android\gradlew.bat -p android --offline --console=plain :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

首次缺少依赖时移除 `--offline`。工具链与依赖来源见 docs/toolchain.md。
APK 输出：android/app/build/outputs/apk/debug/app-debug.apk。

已按用户融雪参考完成第一轮 UI 统一与动效移植，见 docs/ui-v03/acceptance.md；后续继续真机体验回归及章节切换优化。

已完成作品栏目收纳、屏幕键盘输入时工具栏收放、双侧提示与章节标题工具入口，验证见 docs/focus-ux/acceptance.md。

当前内部体验包使用 preview 构建（非 debug + R8），增加近黑侧栏与固定窄顶栏；构建、性能修改与验证限制见 docs/graphite-ux/acceptance.md。构建命令：assemblePreview testDebugUnitTest lintPreview。

Writing Tools：可选一键排版（预览、历史保全、撤销）、字号/行距/留白/字体、预设与自定义颜色、本地图片背景、4 秒提示淡出与全宽页面转场。该批次实现与验证见 docs/writing-tools/acceptance.md。

Safe Backup：从设置或编辑工具进入「备份与恢复」，可将作品/回收站/历史/光标导出到系统文件位置，并选择旧文件校验、预览、恢复为新副本。现有作品不会被覆盖；自动独立备份见 docs/auto-backup/README.md。竞品官方资料对照见 docs/safe-backup/competitor-review.md。

Drawer UX：左侧章节栏展开覆盖编辑顶栏；主页/作品页设置改为右侧覆盖面板。见 docs/drawer-ux/acceptance.md。

Batch Edit：编辑工具栏新增全书替换和整卷／整书排版，先预览、再二次确认，逐章保留原文历史；过时预览或写入失败整批拒绝/回滚。见 docs/batch-edit/README.md。
