# AutoFlow / AtommMaster

一个面向 Android 的无 Root 自动化工具。它通过无障碍服务、录屏授权、悬浮窗和模板匹配能力，完成脚本执行、节点编排、悬浮控制和部分定时任务场景。

当前仓库名是 `AutoFlow`，应用内名称是 `AtommMaster`。如果准备长期开源，建议后续统一品牌命名。

## 项目特点

- 无 Root 自动化执行，基于 `Accessibility Service + MediaProjection`
- 悬浮球和贴边标签交互，运行中可从悬浮入口继续操作
- 项目 / Task / 节点管理，适合组织脚本流程
- OpenCV 模板匹配，支持截图、模板保存和图像识别类操作
- 脚本包导入 / 导出，便于备份和分享
- 基础定时能力，适合做简单自动化演示和触发
- 节点悬浮按钮，可把常用节点做成快捷入口

## 运行要求

- Android 7.0 及以上
- `minSdk = 24`
- `targetSdk = 36`
- 本地开发建议使用 Android Studio 自带 JBR 17
- 代码当前以 Java 11 目标编译

## 首次使用需要的权限

为了让自动化能力正常工作，首次启动后通常需要开启这些权限：

- 无障碍服务
- 屏幕录制授权
- 悬浮窗权限
- 电池优化白名单 / 后台无限制
- 某些机型还需要手动允许自启动

如果权限没补齐，后台执行、悬浮面板和录屏能力都可能不稳定。

## 下载 APK

如果仓库已经启用了 GitHub Releases，可以直接在 Releases 页面下载 APK。

当前项目启用了 ABI 分包，常见产物如下：

- `arm64-v8a`：大多数真机使用这个
- `x86_64`：主要给模拟器使用

普通 Android 手机用户通常下载 `arm64-v8a` 即可。

## 本地构建

本地开发请优先参考 [CODEX_BUILD_NOTES.md](./CODEX_BUILD_NOTES.md)。

几个关键点：

- 必须使用 PowerShell 7
- 不要使用 Windows 自带 PowerShell 5
- 当前 `release` 使用保守配置，便于先稳定导出 APK
- 当前 `release` 暂时沿用 `debug signing`，适合 GitHub 开源分发和侧载测试

已验证可用的基础编译命令：

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac --no-daemon --console=plain
```

如果你要在本机继续处理 release 构建问题，先看 [CODEX_BUILD_NOTES.md](./CODEX_BUILD_NOTES.md) 里的 SDK 和 license 说明。

## GitHub 自动发布 APK

仓库已附带 GitHub Actions 工作流：

- 工作流文件：`.github/workflows/android-apk-release.yml`
- 触发方式包括手动在 GitHub Actions 页面运行，以及推送版本标签，例如 `v1.0.0`

工作流会自动：

- 安装 JDK 17 和 Android SDK 36
- 执行 `:app:assembleRelease`
- 上传生成的 APK 作为 Actions Artifact
- 在 `v*` 标签场景下自动创建 GitHub Release 并挂载 APK

详细步骤见 [docs/RELEASE_GUIDE.md](./docs/RELEASE_GUIDE.md)。

## 适合当前项目的发布方式

当前这套流程更适合：

- GitHub 开源仓库分发
- 测试用户侧载安装
- 早期版本快速验证

当前还不适合直接当成应用商店正式发布流程，因为：

- `release` 暂时关闭了混淆和资源压缩
- `release` 目前仍是便于安装测试的签名方式
- 还没有正式 keystore、版本管理和商店元数据流程

如果后面要上 Google Play 或其他商店，建议再补：

- 正式 `keystore`
- 独立的 release signing 配置
- `versionCode` / `versionName` 版本策略
- 应用图标、截图、隐私说明和发布物料

## 项目结构

```text
app/
  src/main/java/com/auto/master/
    auto/            执行引擎、无障碍能力
    capture/         录屏与截图
    floatwin/        悬浮窗、悬浮球、节点面板
    importer/        脚本包导入导出
    scheduler/       定时任务与模板
```

## 开源前建议

如果你准备公开仓库，建议至少先做完这些：

1. 添加一个明确的 `LICENSE`
2. 在 GitHub 仓库设置里确认 `Actions` 已开启
3. 先手动跑一次 `Android APK Release` 工作流，确认能出包
4. 确认 APK 能安装后，再推送第一个版本标签，比如 `v1.0.0`
5. 准备几张首页和悬浮窗截图，方便别人快速理解项目

## 说明

这个项目当前更适合以 GitHub Releases 的形式公开分发 APK，而不是直接作为商店正式产物。如果你愿意，我下一步还可以继续帮你补：

- `LICENSE`
- `CHANGELOG.md`
- GitHub Release 文案模板
- 仓库首页截图区块
- 正式签名发布方案
