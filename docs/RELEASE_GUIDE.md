# APK 发布指南

AutoFlow 的 Release 已启用 R8 代码裁剪和名称混淆。当前采用保守策略：先混淆类名、方法名并移除无用代码，暂不启用 R8 行为优化和资源压缩，降低无障碍、悬浮窗、Gson、OpenCV、Tesseract、TensorFlow Lite 与 JNI 功能失效的风险。

混淆不能让 APK 完全不可反编译，但会显著提高阅读、定位和复用源码的成本。不要在未完整回归真机功能前叠加第三方加固壳，它们可能影响原生库加载、无障碍服务和录屏服务。

## 首次创建正式签名

正式 keystore 只创建一次，并需要永久妥善备份。丢失后将无法用相同签名升级已安装的应用。

```powershell
keytool -genkeypair -v -keystore autoflow-release.jks -alias autoflow -keyalg RSA -keysize 4096 -validity 10000
```

将 `keystore.properties.example` 复制为本机的 `keystore.properties`，填写实际路径和密码：

```properties
storeFile=C:/secure/path/autoflow-release.jks
storePassword=你的密钥库密码
keyAlias=autoflow
keyPassword=你的密钥密码
```

`keystore.properties` 和密钥文件均已被 Git 忽略。不要把它们、密码或 Base64 密钥提交到仓库。

## 本地构建

```powershell
$env:JAVA_HOME='C:\Program Files\BellSoft\LibericaJDK-17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:assembleRelease --no-daemon --console=plain
```

配置正式签名后，APK 位于 `app/build/outputs/apk/release/`。若没有 `keystore.properties`，Gradle 仍可生成用于检查 R8 的未签名 APK，但该 APK 不能直接安装或发布。

项目按 ABI 分包：普通 Android 真机一般使用 `arm64-v8a`，`x86_64` 主要用于模拟器。

## GitHub Actions 签名

在 GitHub 仓库的 `Settings > Secrets and variables > Actions` 中添加：

- `AUTOFLOW_KEYSTORE_BASE64`：keystore 文件的 Base64 内容
- `AUTOFLOW_STORE_PASSWORD`：密钥库密码
- `AUTOFLOW_KEY_ALIAS`：密钥别名
- `AUTOFLOW_KEY_PASSWORD`：密钥密码

PowerShell 生成 Base64：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\secure\autoflow-release.jks')) | Set-Clipboard
```

工作流在缺少任一签名 Secret 时会停止，不会把未签名 APK 发布给用户。手动运行工作流可先检查构建；推送 `v*` 标签会创建 GitHub Release：

```powershell
git tag v1.0.0
git push origin v1.0.0
```

## 必须保存 mapping

每个版本都必须保存对应的 `app/build/outputs/mapping/release/mapping.txt`。它用于把混淆后的崩溃堆栈还原为源码位置，版本不匹配时无法正确还原。GitHub Actions 会将它作为独立的私有构建产物保留 90 天，正式发版还应另行长期归档。

## 真机回归清单

每次调整混淆规则后，至少在一台 Motorola 真机和一台其他品牌设备上检查：

1. 首次启动、权限申请、无障碍服务与悬浮窗
2. 录屏授权、截图、相册与模板库
3. 点击、滑动、连续手势与脚本跳转/循环
4. 模板匹配、图集匹配、OCR、YOLO/TFLite
5. 项目导入导出、Gson 旧项目兼容、配置 UI
6. 定时任务、通知触发、TTS 与运行日志
7. 专注运行模式和脚本结束后的系统状态恢复

先保留当前 `-dontoptimize` 和 `isShrinkResources = false`。以上功能连续稳定后，再分别试开优化和资源压缩，每次只改一项并重新回归。
