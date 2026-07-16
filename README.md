# AutoMaster

AutoMaster 是一个面向 Android 的无 Root 自动化工具。它通过无障碍服务、屏幕录制、悬浮窗和 OpenCV 图像识别能力，把常见的点击、颜色判断、模板匹配、任务跳转和流程编排组合成可反复执行的自动化脚本。

项目当前更偏向个人自动化、脚本调试和侧载分发场景。包名仍为 `com.auto.master`，这是安装身份，不建议随品牌名频繁变更。

## 主要能力

- 无 Root 执行：基于 `Accessibility Service` 完成点击、返回、手势等实际操作。
- 屏幕识别：基于 `MediaProjection` 获取屏幕画面，支持模板匹配、图集匹配、颜色匹配和颜色搜索。
- 模板制作：内置框选截图、模板库、模板预览、搜索区域预览和 Mask 编辑。
- 匹配优化：支持灰度化、随机点采样、随机 ROI、模板 Mask 等参数，适合在性能和准确率之间调节。
- 流程编排：以 Project / Task / Node / Operation 组织脚本，支持下一节点、失败跳转、动态跳转和循环任务。
- 悬浮控制：提供悬浮球、项目面板、节点列表、流程图悬浮窗和运行状态面板。
- 专注模式：运行时可隐藏非关键 UI、减少日志和状态跟随带来的额外负载。
- 脚本迁移：支持项目脚本包导入、导出和分享。
- 定时演示：提供基础定时入口，方便做简单的自动化触发。
- 支持作者：主界面内置“支持”入口，可查看并保存支付宝收款码。
- 扩展节点：OCR 文字识别、AI 目标检测（TFLite/YOLO）、HTTP 请求、无障碍节点操作（查找/点击/取值）、多次尝试节点、系统参数运行时调整、屏幕亮度调整（脚本停止自动恢复50%）、音频播放、日志输出等。

## 权限要求

首次使用通常需要开启这些权限：

- 无障碍服务
- 屏幕录制授权
- 悬浮窗权限
- 电池优化白名单或后台无限制
- 修改系统设置权限（`WRITE_SETTINGS`，用于屏幕亮度调整节点）
- 部分厂商系统还需要允许自启动

如果这些权限不完整，后台执行、悬浮窗显示、截图识别或长时间运行稳定性都可能受到影响。

## 运行环境

- Android 7.0 及以上
- `minSdk = 24`
- `targetSdk = 36`
- OpenCV 4.13.0
- 本地开发建议使用 JDK 17
- Java 编译目标为 Java 11

## 本地构建

本仓库要求使用 PowerShell 7.x 及以上，不要使用 Windows 自带 PowerShell 5。

常用编译命令：

```powershell
$env:JAVA_HOME='C:\Program Files\BellSoft\LibericaJDK-17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:compileDebugJavaWithJavac
```

生成调试 APK：

```powershell
.\gradlew.bat :app:assembleDebug
```

生成 release APK：

```powershell
.\gradlew.bat :app:assembleRelease
```

当前 release 已启用 R8 代码裁剪和名称混淆，暂时关闭行为优化与资源压缩，以降低无障碍、悬浮窗、Gson、OCR/CV 和 JNI 功能的兼容风险。Release 不再使用 Debug 签名；本地需配置 `keystore.properties`，GitHub Actions 需配置签名 Secrets。

更多本地构建细节见 [CODEX_BUILD_NOTES.md](./CODEX_BUILD_NOTES.md)。

## APK 产物

项目启用了 ABI 分包，常见产物包括：

- `arm64-v8a`：大多数 Android 真机使用。
- `x86_64`：主要用于模拟器。

普通手机用户通常安装 `arm64-v8a` 版本即可。



## GitHub 发布

仓库包含 GitHub Actions 发布流程：

- 工作流：`.github/workflows/android-apk-release.yml`
- 支持手动运行
- 支持推送 `v*` 标签后自动创建 GitHub Release

详细步骤见 [docs/RELEASE_GUIDE.md](./docs/RELEASE_GUIDE.md)。

## 项目结构

```text
app/src/main/java/com/auto/master/
  auto/        选择框、取点、运行辅助视图等自动化 UI
  capture/     屏幕录制、截图和分辨率缩放
  floatwin/    悬浮窗、项目面板、节点面板、模板库和操作 Dialog
  Task/        Operation 定义、执行上下文、处理器和响应处理器
  Template/    模板 Mat、manifest、手势等缓存
  importer/    脚本包导入导出
  scheduler/   定时任务相关能力
  utils/       OpenCV、Bitmap、性能档位等工具类
```

## 使用建议

- 长时间运行前，先完成权限加固，尤其是后台无限制和自启动。
- 模板匹配尽量限制搜索区域，必要时使用 Mask、灰度化、随机点采样或随机 ROI。
- 对性能敏感的流程可以使用专注模式，减少运行时 UI 和日志开销。
- 替换模板截图后建议重新运行一次节点验证，确认 bbox、Mask 和匹配阈值仍然合适。

## 许可证

本项目采用 [AutoMaster 非商业许可证 (ANCL) v1.0](./LICENSE)，**仅允许非商业用途**的使用、复制、修改与分发；任何形式的商业使用（销售、提供付费服务、广告变现、捆绑进商业产品等）均需事先获得作者授权。详见 [LICENSE](./LICENSE) 文件。

## 开源前待补

- `CHANGELOG.md`
- 正式签名配置
- 发布截图和演示素材
- 隐私说明与权限说明文档

## 支持作者

如果你喜欢的话，给我点杯奶茶呗！

![support](assets/support.jpg)
