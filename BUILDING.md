# 构建 APK

本项目需要 Android Studio / Android SDK Platform 35、JDK 17 和 Gradle 8.11.1。当前生成环境没有 Android SDK、`android.jar` 或 Gradle，因此这里不能完成 APK 编译或真机验证。

## 本地构建

1. 安装 Android Studio，并通过 SDK Manager 安装 Android SDK Platform 35、Build Tools 35.x。
2. 确认 Android Studio 使用 JDK 17。
3. 用 Android Studio 打开 `PureMusic` 文件夹，等待 Gradle 同步。
4. 运行 `:app:testDebugUnitTest`，再运行 `:app:assembleDebug`。
5. APK 输出位置：`app/build/outputs/apk/debug/app-debug.apk`。

如果使用命令行，请安装 Gradle 8.11.1，然后在项目根目录运行：

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

## GitHub Actions 构建

`.github/workflows/android.yml` 会在 push、pull request 或手动触发时安装 JDK/Android SDK、执行单元测试和 Debug APK 编译，并将 APK 上传为 workflow artifact。将整个项目推送到自己的 GitHub 仓库即可使用。

## 注意

本项目目前是可继续开发的源码快照，不是已通过 Android 构建和真机测试的正式版本。FFmpeg 解码扩展使用 GPL-3.0 组件，分发前请阅读 `THIRD_PARTY_NOTICES.md`。
