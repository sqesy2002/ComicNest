# ComicNest

> 一款 **Android 纯本地漫画阅读器**：直接读取本地目录中的漫画，无需导入拷贝，支持下拉（Webtoon）与左右翻页（Pager）两种阅读方式、多收藏夹、阅读进度记忆。

![Platform](https://img.shields.io/badge/platform-Android-green.svg)
![Min SDK](https://img.shields.io/badge/SDK-26%2B-orange.svg)
![Target SDK](https://img.shields.io/badge/target%20SDK-35-blue.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)
![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-brightgreen.svg)
![Version](https://img.shields.io/badge/version-1.1.0-blue)

---

## ✨ 功能特性

- **纯本地读取**：基于「所有文件访问权限」+ 绝对路径，直接扫描并阅读本地漫画目录，不复制、不导入。
- **两级结构模型**：`漫画库根目录 / 漫画名 / 图片`，整部漫画为单一连续阅读序列。
- **两种阅读模式**：下拉式长图（Webtoon）↔ 左右翻页（Pager），切换时保持当前页。
- **阅读进度记忆**：LRU 策略保存最近 5 本的进度，下次进入自动续读。
- **封面自动选取**：每部漫画以其目录内「自然序最小名」的图片作为封面。
- **多收藏夹**：多对多、指向式（不复制文件）；书架长按或阅读器内 ♡ 均可收藏。
- **书架检索与分组**：左侧抽屉内搜索框模糊搜索漫画名；按每 100 本一组快速跳转。
- **图片格式**：支持 jpg / jpeg / png / webp / gif（GIF 动画播放）等常见位图格式；不支持压缩包 / PDF / EPUB。
- **Material You**：跟随系统深浅色与动态取色。

## 🧱 技术栈与关键版本

| 项目 | 版本 | 说明 |
|------|------|------|
| Gradle | **8.11.1** | 通过 wrapper 分发，无需本机单独安装 |
| Android Gradle Plugin (AGP) | **8.7.3** | |
| Kotlin | **2.0.21** | 含 Compose 编译器插件 |
| KSP | **2.0.21-1.0.28** | Room / Hilt 注解处理 |
| JDK | **21**（运行 Gradle） | 各模块 `jvmTarget = 17` |
| compileSdk / targetSdk | **35** | |
| minSdk | **26** | Android 8.0+ |
| Jetpack Compose BOM | **2024.12.01** | Material 3 |
| Hilt | **2.51.1** | 依赖注入 |
| Room | **2.6.1** | 本地数据库 |
| Coil | **2.7.0** | 图片加载（含 GifDecoder） |
| Navigation Compose | **2.8.5** | 单 Activity + 底部导航 |

## 📁 项目结构

多模块 Gradle 工程，依赖方向为 `feature → domain → (data / source)`，`core` 为底座，`app` 仅做组装。

```
ComicNest/
├─ app/                    # 应用入口：MainActivity、导航、主题、DI 组装
├─ core/
│  ├─ model/               # 纯数据模型（ComicKey、Comic、SortMode…）
│  ├─ common/              # 工具（自然序比较、图片格式判定…）
│  ├─ domain/              # 领域层：UseCase 与 Repository 接口
│  ├─ data/                # 数据层：Room、DataStore、Repository 实现
│  └─ ui/                  # 共享 UI 组件与主题（ComicCoverCard、FavoritesDialog…）
├─ source/
│  ├─ api/                 # 数据源接口（为未来在线源预留扩展点）
│  └─ local/               # 本地目录扫描与取页实现
├─ feature/
│  ├─ library/             # 书架首页
│  ├─ reader/              # 阅读器（Webtoon / Pager）
│  ├─ collection/          # 收藏夹
│  └─ settings/            # 设置（库根管理、默认阅读方式、关于）
├─ docs/                   # PRD / DESIGN 等设计文档
├─ gradle/                 # wrapper 与版本目录（libs.versions.toml）
└─ keystore/               # 签名密钥（本地，已 gitignore，勿提交）
```

## 🛠 本地环境配置

### 前置要求
1. 安装 **JDK 21**。
2. 安装 **Android SDK**，并确保包含 **platforms;android-35** 与 **build-tools**（34/35）。
3. （可选）安装 Android Studio，便于查看与运行。

> 无需单独安装 Gradle —— 项目通过 `gradlew`（wrapper）自动使用 8.11.1。

### 配置 SDK 路径
在项目根目录创建 `local.properties`（**已被 .gitignore 忽略，不会提交**）：

```properties
sdk.dir=C\:\\Users\\<你的用户名>\\AppData\\Local\\Android\\Sdk
```

或在构建前设置环境变量（Windows PowerShell）：

```powershell
$env:ANDROID_HOME = "C:\path\to\Android\Sdk"
```

## ▶️ 构建与运行

所有命令在项目根目录执行；PowerShell 中多条命令请用 `;` 分隔（PowerShell 不支持 `&&`）。

### 生成 Debug APK
```powershell
$env:ANDROID_HOME = "E:\Android\Sdk"   # 若已用 local.properties 指定 sdk.dir 可省略
./gradlew :app:assembleDebug
```
产物：`app/build/outputs/apk/debug/app-debug.apk`

### 安装到设备 / 运行
```powershell
./gradlew :app:installDebug        # 已连接设备/模拟器时直接安装
# 或在 Android Studio 中点击 Run ▶
```

### 运行单元测试
```powershell
./gradlew test
```

### 生成 Release APK（正式签名）
Release 采用**正式签名**：仅当根目录存在 `keystore.properties` 时才启用签名，否则 Release 会构建为未签名。

1. 准备签名密钥（若已有 `keystore/comicnest.jks` 可跳过）：
   ```powershell
   keytool -genkeypair -v -keystore keystore/comicnest.jks -alias comicnest `
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. 在根目录创建 `keystore.properties`（**含口令，切勿提交**）：
   ```properties
   storeFile=keystore/comicnest.jks
   storePassword=你的密钥库口令
   keyAlias=comicnest
   keyPassword=你的密钥口令
   ```
3. 构建 Release：
   ```powershell
   ./gradlew :app:assembleRelease
   ```
   产物自动命名为：**`app/build/outputs/apk/release/ComicNest_<versionName>.apk`**（当前即 `ComicNest_1.1.0.apk`）。

## 🔒 权限与隐私

- 需要 **「所有文件访问权限」（`MANAGE_EXTERNAL_STORAGE`）**：这是「本地直读、免导入」体验的代价，授予后 App 可访问设备上的可读文件。
- **无网络权限**：ComicNest 不联网、不上报、不含广告与追踪，所有数据仅保存在本机。
- 侧载自用场景建议使用；请在可信来源获取安装包。

## 📌 已知限制

- 不支持压缩包（zip/cbz）、PDF、EPUB，仅读取散图目录。
- Android 11+ 即便授予全部文件访问权限，`Android/data`、`Android/obb` 仍被系统屏蔽，无法作为漫画库根。
- 允许不同库根下的同名漫画并存（内部以「库根 + 漫画名」构造唯一键）。

## 🔖 版本

当前版本：**v1.1.0**（`versionName=1.1.0` / `versionCode=2`）。设置页「关于」中显示的版本号于运行时读取自安装包，自动跟随构建版本。

## 📄 许可与致谢

- 本项目参考并移植了 [Mihon](https://github.com/mihonapp/mihon)（Apache License 2.0）的部分组件思路，并使用了 Coil、Room、Hilt、Jetpack Compose 等开源库，详见仓库根目录 `NOTICE`。
- 本项目为个人学习与自用项目，按当前仓库许可条款分发；使用者请自行确保对本地漫画内容拥有合法使用权。

---

如果这个项目对你有帮助，欢迎 Star ⭐ 与反馈。
