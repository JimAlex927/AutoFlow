# APK 发布指南

这份文档是给仓库维护者用的，目标是让你在不熟悉 Android 发布流程的情况下，也能先把 APK 稳定发到 GitHub。

## 你现在已经有的东西

仓库里已经准备好了：

- 根目录 `README.md`
- 自动构建工作流 `.github/workflows/android-apk-release.yml`

这意味着你不需要先在本地手动处理 Android SDK license、打包再上传，直接走 GitHub Actions 就可以。

## 第一次发布前要确认的事

先检查下面这些：

1. 仓库已经推到 GitHub
2. 仓库的 `Actions` 没被禁用
3. 根目录的 `README.md` 已经是你想公开展示的内容
4. 你已经准备好一个开源许可证
5. 版本号如果有变化，记得同步更新 `app/build.gradle.kts`

如果 `LICENSE` 还没加，建议先补上再公开仓库。

## 最简单的出包方式

### 方式一：手动运行工作流

适合第一次试跑，先确认工作流能否正常产出 APK。

1. 打开 GitHub 仓库
2. 进入 `Actions`
3. 选择 `Android APK Release`
4. 点击 `Run workflow`
5. 等待构建完成
6. 在该次运行页面的 `Artifacts` 区域下载 APK

## 正式发版方式

当你确认 APK 能正常安装后，再打版本标签：

```bash
git tag v1.0.0
git push origin v1.0.0
```

推送标签后，工作流会自动：

- 构建 `release` APK
- 上传 APK 到本次 Actions 产物
- 自动创建 GitHub Release
- 把 APK 挂到 Release 附件里

## 用户应该下载哪个 APK

当前项目启用了 ABI 分包，所以一般会看到多个 APK：

- `arm64-v8a`：大多数 Android 真机使用这个
- `x86_64`：主要给模拟器使用

如果是发给普通手机用户，优先引导他们下载 `arm64-v8a`。

## 当前这套发布方案的定位

这是一套适合开源仓库和侧载分发的方案，不是正式应用商店签名方案。

当前 `release` 的特点：

- 关闭了代码混淆
- 关闭了资源压缩
- 采用更保守的 release 配置，优先保证能稳定导出 APK

这套方案很适合：

- GitHub 开源仓库发包
- 测试群、体验用户分发
- 早期版本验证

## 如果工作流失败，优先看哪里

先看这几个位置：

1. Actions 日志里 `Accept SDK licenses and install required packages`
2. `Build release APK`
3. `Upload release APK artifact`

常见问题一般是：

- Gradle 依赖下载失败
- Android SDK 包版本不匹配
- 构建成功但没有找到 APK 输出

## 如果以后要上应用商店

到那一步你还需要补这些：

1. 正式 keystore
2. 独立的 release signing 配置
3. 更规范的版本号管理
4. 应用商店截图、图标、隐私说明
5. 更严格的混淆、签名和发布校验

如果你后面真的要上架，我可以继续帮你把这套 GitHub Release 流程升级成正式签名流程。
