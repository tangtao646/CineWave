# CineWave

基于 Kotlin Multiplatform (KMP) + Compose Multiplatform 的跨平台影音播放应用。

## 平台支持

| 平台 | 状态 | 说明 |
|------|------|------|
| Android | ✅ 可用 | 支持视频播放、电台直播、后台播放 |
| Desktop (macOS) | ✅ 可用 | 基于 VLCJ (libvlc) 的视频播放，macOS 上正常运行 |
| Desktop (Windows) | ⚠️ 未测试 | 代码层面支持，但未在 Windows 真机验证 |
| iOS | ❌ 暂未实现 | 待后续开发 |

## 技术栈

- **Kotlin Multiplatform** — 跨平台业务逻辑共享
- **Compose Multiplatform** — 跨平台 UI
- **Koin** — 依赖注入
- **Ktor** — HTTP 客户端
- **Room** — 本地数据库
- **Coil 3** — 图片加载
- **VLCJ (libvlc)** — Desktop 端视频播放
- **Media3 (ExoPlayer)** — Android 端视频/音频播放
- **Cash App Paging** — 分页加载

## 项目结构

```
CineWave/
├── shared/                    # 跨平台共享代码
│   └── src/
│       ├── commonMain/        # 各平台通用代码
│       ├── androidMain/       # Android 平台实现
│       ├── jvmMain/           # Desktop (JVM) 平台实现
│       ├── iosMain/           # iOS 平台实现（待完善）
│       └── ...
├── androidApp/                # Android 应用入口
├── desktopApp/                # Desktop 应用入口
└── iosApp/                    # iOS 应用入口（待完善）
```

## 运行方式

### Android

```bash
./gradlew :androidApp:assembleDebug
```

### Desktop (macOS)

```bash
# 标准运行
./gradlew :desktopApp:run

# 热重载
./gradlew :desktopApp:hotRun --auto
```

> **注意**：Desktop 端依赖 VLC，需先安装：
> - macOS: `brew install vlc`
> - Linux: `sudo apt install vlc libvlc-dev`

### iOS

暂未实现，待后续开发。

## 构建产物

使用 GitHub Actions 自动构建，支持：

- Android APK（debug / release）
- Desktop DMG（macOS）
- Desktop DEB（Linux）
- Desktop MSI（Windows）

手动触发构建：GitHub → Actions → CineWave CI/CD → Run workflow

## 许可证

[Apache License 2.0](LICENSE)
