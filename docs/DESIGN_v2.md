# ComicNest 系统设计文档（DESIGN v2：视频库与播放器）

> 版本：v2.0（新增“视频”一级入口、视频库目录、视频网格与分组抽屉、内置播放器、最近观看、本地删除）
> 迭代：**v2.2**（2026-11，6 项体验迭代：多关键词搜索 / 删除一次成功 / 移除“继续观看”UI / 播放器底部三层控制层 / 设置文案去英文 / 默认视频朝向），设计与代码落点见 **§11**；与 §0~§10 冲突时以 §11 为准。
> 上游依据：`docs/demand_v2.txt` + 本轮已确认决策 **V1~V14**（见 §0）
> 与 v1 的关系：`docs/DESIGN.md`（v0.3）所描述的漫画侧设计**全部保持不变**。本文档只做增量；唯一共通改动是 **Room 版本 1→2 迁移**与**底部导航由 3 项增至 4 项**。
> 目标读者：开发实施者。本文档做到“决策完备”，实施者无需再做架构级决策。
> 参考项目：NextPlayer（`github.com/anilbeesetti/nextplayer`，GPL-3.0，**仅参考分层结构与交互思路**）、mpv-android（`github.com/mpv-android/mpv-android`，**仅作格式覆盖面参照**）。二者均为 GPL 系许可，**不得复制其代码**，本工程零代码引入。

---

## 0. 已确认决策清单（V1~V14，正文以此为准）

| 编号 | 决策 | 结论 |
|------|------|------|
| V1 | 底部导航 | 书架 / **视频** / 收藏 / 设置（4 项，视频位于书架与收藏之间） |
| V2 | 视频库目录 | 设置页新增“视频库目录”分区，位于“阅读”与“关于”之间；支持多目录；**只取目录直接下一级的视频文件**，无更深子目录 |
| V3 | 视频页形态 | 与书架同构：顶栏（☰ + 标题 + 排序/随机/刷新）+ 左侧抽屉（顶部搜索框 + 每 50 个一组的分组跳转，v2.2 由 30 调为 50，见 §11 R3）+ 网格；**无收藏功能** |
| V4 | 缩略图 | 取**视频中间帧**；可见项懒生成 + 列表静止后补齐；缓存在 App 私有目录 |
| V5 | 卡片样式 | 16:9 缩略图 + 文件名单行 + 右下角时长角标；`GridCells.Adaptive(minSize = 160.dp)`（手机约 2 列） |
| V6 | 播放器内核 | Media3 ExoPlayer `1.5.1`（`media3-exoplayer` + `media3-ui`）；`AndroidView` 包 `PlayerView`（关内置控制器）+ 自绘 Compose 控制层；**不使用** `media3-ui-compose` 实验 API |
| V7 | 播放器功能 | 拖动进度条、前进/后退 15s（**v2.2 移除 ±15s 按钮并重构底栏，见 §11 R4**）、0.5/1.0/1.5/2.0 倍速、横竖屏切换、轻点显隐控件；**并加手势**：双击暂停/继续、左右滑快进/回退、上下滑调亮度（左半屏）/音量（右半屏）；播放结束停在末帧 |
| V8 | 观看记录 | 最近 **3** 条（第 4 条覆盖最早），持久化 Room，记录播放位置；单次播放 ≥10s 才写入；~~视频页顶部“继续观看”最多 3 张 16:9 卡片~~（**v2.2 起 UI 不再展示，仅保留数据与 LRU=3，见 §11 R3**） |
| V9 | 格式 | 白名单 `mp4/m4v/mkv/webm/ts/mov/3gp`；白名单外**不入库、不展示**（避免点开必失败） |
| V10 | 删除 | 长按底部菜单（播放 / 删除）→ 二次确认（含文件名 + “不可恢复”）→ 清 MediaStore 记录（失败忽略）→ `File.delete()` 判返回值 → 清缩略图与观看记录 → 列表即时移除 |
| V11 | 目录关系 | 视频目录与漫画目录**独立存表**、允许同一路径；互不影响（不污染漫画 `refresh` 整表重建） |
| V12 | 排序 | 保留“名称（自然序）/ 导入时间”排序按钮，默认名称 |
| V13 | 版本交付 | `versionName = 1.2.0`、`versionCode = 3`；Release 产物 `ComicNest_1.2.0.apk` |
| V14 | 边界约定 | 视频不做收藏、不做画中画/后台播放/字幕/连播/倍速与音量记忆（见 §10.3） |

---

## 1. 技术选型与总体方案（视频域）

### 1.1 选型结论

视频域是一条**与漫画域平行**的新业务线：独立的表、独立的扫描器、独立的页面与播放器。为降低对已交付功能的回归风险，**不改造**漫画侧的 `ComicSource`/`LibraryRepository`/`comic_cache`。

| 维度 | 选择 | 说明 |
|------|------|------|
| 播放内核 | **Media3 ExoPlayer 1.5.1**（`media3-exoplayer`、`media3-ui`） | 官方推荐、事件完备、倍速与 seek 精度好；容器支持 mp4/mkv/webm/ts/mov 等；APK 约 +1.5~2MB |
| 播放器 UI | `AndroidView` 包 `PlayerView`（`useController = false`）+ 自绘 Compose 控制层 | 回避 `media3-ui-compose` 的实验 API 与版本耦合，控制层样式与全站 Material3 一致 |
| 缩略图 | `MediaMetadataRetriever.getFrameAtTime()` 取**中间帧** | 取 `durationUs / 2`，`OPTION_CLOSEST_SYNC`（快速）；判定为黑帧时降级 `OPTION_CLOSEST` |
| 视频元数据 | `MediaMetadataRetriever`（时长/分辨率/旋转）+ `File` stat（大小/修改时间） | 扫描阶段只做 stat（快）；时长与缩略图在首次生成缩略图时一并读取并回写 DB |
| 视频扫描 | `java.io.File` 绝对路径（复用 v1 的 `MANAGE_EXTERNAL_STORAGE` 方案） | 只列目录直接下一级的普通文件，不递归 |
| 持久化 | Room 2.6.1 新增 3 张表，DB 版本 **1 → 2** 手写 `Migration` | 保留漫画侧已有数据，禁止 destructive 迁移 |
| 列表 | `LazyVerticalGrid` + `Adaptive(160.dp)` | 复用 v1 的无限滚动形态（数据量在千级内） |
| 异步与并发 | kotlinx-coroutines + `Semaphore(3)` 限制缩略图并发 | 避免多路硬解同时开导致 CPU/内存峰值 |
| 删除 | `File.delete()` + MediaStore 记录清理（降级 `MediaScannerConnection`） | 保证文件与系统索引双向一致 |
| 版本 | 1.2.0 / versionCode 3 | V13 |

**选型代价与前置条件（必须知晓）**

1. Media3 依赖当前**不在本机 Gradle 缓存中**，首次构建需联网拉取 `google()` 仓库；若网络不通会表现为依赖解析失败，需先配置 Gradle 代理或使用本地镜像（见 §5 V0 出口条件）。
2. Media3 容器支持 ≠ 解码支持：能否播放取决于设备解码器（HEVC/AV1 视机型）。白名单只保证“容器可能被解析”，播放失败按 §8.2 兜底。
3. 中间帧缩略图有单文件 30~300ms 的 CPU 开销（大文件/HEVC 更久），因此**禁止在扫描阶段同步生成**（见 §3.6）。

### 1.2 总体架构（双域平行）

```
┌──────────────────────────────────────────────────────────────────┐
│ :app  （底栏 4 项 / 导航图 / DI 组装 / 签名）                        │
├───────────────────────────────┬──────────────────────────────────┤
│ 漫画域（v1，不改）              │ 视频域（v2，新增）                 │
│ :feature:library              │ :feature:video                   │
│ :feature:reader               │   ├─ ui/VideoLibraryScreen        │
│ :feature:collection           │   ├─ ui/VideoPlayerScreen         │
│ :feature:settings（改造：加视频库目录分区）                          │
├───────────────────────────────┴──────────────────────────────────┤
│ :core:domain  （新增 VideoRepository 接口 + Video 系列 UseCase）    │
├───────────────────────────────┬──────────────────────────────────┤
│ :core:data                    │ :source:video（新增）              │
│ （Room 三新表 + 迁移 + 实现）    │ LocalVideoSource / 扫描 / 缩略图    │
├───────────────────────────────┴──────────────────────────────────┤
│ :core:model（新增 Video* 模型）  :core:common（VideoFormats）        │
│ :core:ui（主题/通用组件，新增视频卡片可选下沉）                       │
└──────────────────────────────────────────────────────────────────┘
```

**关键设计原则**

1. **域隔离**：视频不读写漫画的 `library_root` / `comic_cache` / `collection` / `read_progress`；漫画侧代码除“设置页新增分区、底栏新增入口、DB 版本 +1”外零改动。
2. **重活后置**：扫描只做目录列举与 `File` stat（O(N) 且无解码）；缩略图与时长在“卡片可见”时才生成，并按 §3.6 的并发与缓存规则收敛。
3. **播放器唯一实例**：一个播放页对应一个 `ExoPlayer`，绑 `ViewModel` 生命周期，`onStop` 释放；旋转不重建 Activity（`MainActivity` 已声明 `configChanges="orientation|screenSize|keyboardHidden|uiMode"`），保证切横竖屏不中断播放。
4. **状态即数据**：视频页与播放器均用 `StateFlow<UiState>` 密封状态暴露，UI 只渲染状态（沿用 v1 §8.3 的做法）。
5. **边界防御**：所有文件 IO、媒体读取、删除、DB 操作包裹 `runCatchingIo`，异常降级为占位/提示，绝不崩溃。
6. **唯一键**：`VideoKey = videoRootId + "::" + fileName`，展示名仅为 `fileName`（跨目录同名文件天然不冲突；同一目录内文件名不可重复）。

### 1.3 关键数据流

- **扫描流**：`VideoRoot(path)` → `LocalVideoSource.scanLibrary()` → `List<VideoSummary>`（仅 stat，`thumbState = PENDING`）→ `VideoRepository.refresh()` 按根 `replaceRoot` 写 `video_cache` → `feature:video` 观察 `Flow` → 网格渲染（先出文件名与占位，再逐步出缩略图）。
- **缩略图流**：卡片进入可见区 → `EnsureVideoThumbnailUseCase(key)` → 若 `thumbState != PENDING` 直接返回 → 生成中间帧 JPEG 落盘 `filesDir/video_thumbs/<hash>.jpg` → 回写 `thumb_path/thumb_state/duration_ms` → DB Flow 触发卡片重绘。
- **播放流**：点击卡片 → 路由 `video_player/{videoKeyId}` → `VideoPlayerViewModel` 查 `VideoSummary` → 校验文件存在 → `ExoPlayer` 播放 → 每 5s 节流 + `onStop` 落库（LRU=3）→ 返回列表时视频页顶部“继续观看”更新。
- **删除流**：长按 → 菜单“删除” → 确认框 → `DeleteVideoUseCase` → 清 MediaStore 记录 → `File.delete()` → 清缩略图缓存 + 删除该视频的进度行与缓存行 → DB Flow 驱动列表即时移除（行数未变时兼一篇 `refresh()` 兜底）。

---

## 2. 模块划分与功能边界

| 模块 | 本次职责（做什么） | 边界（不做什么） | 依赖 |
|------|--------------------|------------------|------|
| `:feature:video`（新） | 视频页（网格/抽屉/搜索/30 分组/随机/刷新/排序）、播放页（播放器 + 控件 + 手势 + 横竖屏）、继续观看卡片、删除交互 | 不做文件扫描、不做持久化、不做解码细节 | domain, ui, model |
| `:source:video`（新） | 一级视频文件扫描、格式判定、媒体元数据读取、中间帧缩略图生成与缓存落盘 | 不持久化、不做 UI、不依赖 feature | model, common |
| `:core:domain`（改） | 新增 `VideoRepository` 接口与视频用例（扫描/增删根/随机/删除/进度/继续观看） | 不实现 IO | model, common |
| `:core:data`（改） | 新增 3 表/DAO/`MIGRATION_1_2`、`VideoRepositoryImpl`、DI 绑定 | 不做业务编排 | domain, model, common, source:video |
| `:core:model`（改） | 新增 `VideoKey/VideoRoot/VideoSummary/VideoProgress/VideoSortMode/VideoThumbnailState` | 无 Android 依赖 | 无 |
| `:core:common`（改） | 新增 `VideoFormats`（白名单判定）、`formatDuration` | 不依赖 model/domain | 无 |
| `:core:ui`（改） | 主题与通用状态组件复用；若视频卡片被两处以上使用再下沉（本期仅一处，**不下沉**） | 不含业务页面 | model, common |
| `:feature:settings`（改） | 新增“视频库目录”分区（列表/添加/移除/失效标记），位置在“阅读”与“关于”之间 | 不做视频扫描触发以外的业务 | domain, ui, model |
| `:app`（改） | 底栏 4 项、路由 `videos` / `video_player/{videoKeyId}`、media3 依赖与 `source:video`、`feature:video` 装配 | 不含业务逻辑 | 全部 |

**模块交互方式（沿用 v1 约定）**

- feature 的 ViewModel 构造注入 UseCase（Hilt），得到 `Flow`/`suspend` 结果并映射为 `UiState`；UI 不含业务判断。
- UseCase 只依赖 `:core:domain` 的接口，由 `:core:data` 用 Hilt `@Binds` 绑定实现。
- `:core:data` 的 `VideoRepositoryImpl` 聚合 `:source:video` 的 `VideoSource` + `VideoDao`，与 v1 `LibraryRepositoryImpl` 聚合 `ComicSource` + `ComicDao` 的结构完全一致。
- 视频页 → 播放页通过 `:app` 定义的路由字符串跳转，feature 之间不互相依赖。

---

## 3. 各模块详细设计

> 约定沿用 v1：包根 `com.comicnest`；所有 `suspend` IO 在 `Dispatchers.IO`；对外返回 `Result<T>` 或密封状态，不抛裸异常。

### 3.1 `:core:model`（新增类型）

| 类型 | 字段 | 说明 |
|------|------|------|
| `VideoKey` | `rootId: String`、`fileName: String` | 值对象；`id = "$rootId::$fileName"`；`displayName = fileName`（含扩展名，展示时按 §9.2 截断） |
| `VideoRoot` | `id: String`（路径哈希）、`path: String`（绝对）、`displayName: String`、`addedAt: Long` | 一个视频库根目录 |
| `VideoSummary` | `key: VideoKey`、`absPath: String`、`sizeBytes: Long`、`lastModified: Long`、`importedAt: Long`、`durationMs: Long?`、`thumbPath: String?`、`thumbState: VideoThumbnailState` | 网格项。`durationMs` 为 `null` 表示尚未读取（缩略图生成时回填）；`thumbState` 驱动占位/失败态 |
| `VideoThumbnailState` | `PENDING` / `READY` / `FAILED` | 枚举。`FAILED` 不自动重试（用户“刷新”时重置为 `PENDING`） |
| `VideoProgress` | `videoKeyId: String`、`positionMs: Long`、`durationMs: Long`、`updatedAt: Long` | 最近观看（LRU=3，V8） |
| `VideoSortMode` | `NAME` / `IMPORT_TIME` | 视频页排序（默认 `NAME`，V12） |

> `VideoSummary` 不包含“所属收藏夹”等字段——视频不做收藏（V14）。

### 3.2 `:core:common`（新增工具）

| 函数/类 | 签名 | 功能/规则 |
|---------|------|-----------|
| `VideoFormats.extensions` | `val extensions: Set<String>` | 白名单：`mp4, m4v, mkv, webm, ts, mov, 3gp`（V9）；小写、无点 |
| `VideoFormats.isSupported` | `fun isSupported(fileName: String): Boolean` | 取最后一个 `.` 后的扩展名，忽略大小写比对白名单 |
| `formatDuration` | `fun formatDuration(durationMs: Long?): String` | `null`/`<=0` → `"--:--"`；`<1h` → `mm:ss`；`>=1h` → `h:mm:ss` |
| `md5Hex` | `fun md5Hex(input: String): String` | 缩略图缓存键（与 v1 库根 `id` 哈希同算法族，便于排查） |

### 3.3 `:core:domain`（新增接口与用例）

**`VideoRepository` 接口（由 `:core:data` 实现）**

| 方法（参数 → 返回） | 承担功能 |
|---------------------|----------|
| `observeRoots(): Flow<List<VideoRoot>>` | 观察已导入视频根 |
| `addRoot(path: String): Result<VideoRoot>` | 校验存在/是目录/可读后新增（同路径去重） |
| `removeRoot(rootId: String): Result<Unit>` | 移除根，级联清理该根的 `video_cache`、`video_progress` 与缩略图文件 |
| `observeVideos(sort: VideoSortMode): Flow<List<VideoSummary>>` | 视频总览（`NAME` 用自然序，`IMPORT_TIME` 用 `imported_at DESC`） |
| `refresh(): Result<Int>` | 对全部根重扫并整表替换，返回视频总数；写缓存前保留已有 `thumbPath/thumbState/durationMs`（同 key 且 size+mtime 未变时继承） |
| `getVideo(key: VideoKey): Result<VideoSummary>` | 单个视频详情（播放页入口校验） |
| `saveThumbnail(key: VideoKey, path: String, durationMs: Long?): Result<Unit>` | 缩略图生成成功后回写 `thumb_path/thumb_state=READY/duration_ms` 并刷新 `last_modified` |
| `markThumbnailFailed(key: VideoKey): Result<Unit>` | 标记 `FAILED`（避免反复重试）；`refresh` 时重置为 `PENDING` |
| `deleteVideo(key: VideoKey): Result<Unit>` | 物理删除文件 + 清 MediaStore 记录 + 清缩略图 + 删进度行 + 从 `video_cache` 移除 |
| `saveProgress(key: VideoKey, positionMs: Long, durationMs: Long): Result<Unit>` | 保存并在**同一事务**内 `evictOldest(keep = 3)`（V8） |
| `getProgress(key: VideoKey): VideoProgress?` | 取单个进度（无则 null → 从头播） |
| `observeRecent(): Flow<List<RecentVideo>>` | 最近观看（≤3），`RecentVideo(progress, summary)` 供“继续观看”卡片；`summary` 已被删除/失效时过滤掉 |

**UseCase（纯 Kotlin，可单测）**

| UseCase | 依赖 | `operator fun invoke(...)` | 规则 |
|---------|------|----------------------------|------|
| `GetVideoLibraryUseCase` | VideoRepository, PreferencesRepository | `(): Flow<VideoLibraryUiData>` | 合并 videos + sortMode + roots + recent（4 流 `combine`） |
| `ScanVideoLibraryUseCase` | VideoRepository | `(): Result<Int>` | 直接委托 `refresh()`；失败保留旧数据 |
| `AddVideoRootUseCase` | VideoRepository | `(path: String): Result<VideoRoot>` | 绝对路径、存在、是目录、可读；与漫画目录允许重复（V11） |
| `RemoveVideoRootUseCase` | VideoRepository | `(rootId: String): Result<Unit>` | 委托 repository，附带缩略图清理 |
| `RandomVideoUseCase` | VideoRepository | `(): Result<VideoKey>` | 在全部视频中均匀随机；空库返回 failure（文案“视频库为空”） |
| `DeleteVideoUseCase` | VideoRepository | `(key: VideoKey): Result<Unit>` | 委托 `deleteVideo`；返回后 UI 只依赖 DB Flow 自然刷新，不做手动剔除 |
| `SaveVideoProgressUseCase` | VideoRepository | `(key, positionMs, durationMs): Result<Unit>` | **V8 阈值**：`positionMs < 10_000` 时不写；`durationMs <= 0` 时不写；`positionMs` 夹取到 `[0, durationMs]` |
| `EnsureVideoThumbnailUseCase` | VideoRepository | `(key: VideoKey): Boolean` | 已 `READY`/`FAILED` 直接返回 false；否则生成 → 成功 `saveThumbnail`，失败 `markThumbnailFailed` |

### 3.4 `:source:video`（新增模块）

**接口（供 `:core:data` 注入；与 v1 `ComicSource` 同构）**

| 成员 | 签名 | 功能 |
|------|------|------|
| `id` | `val id: String` = `"local-video"` | 来源唯一标识 |
| `label` | `val label: String` = `"本地视频"` | 展示名 |
| `scanLibrary` | `suspend fun scanLibrary(root: VideoRoot): Result<List<VideoSummary>>` | 列举一级视频文件并 stat |
| `readMeta` | `suspend fun readMeta(absPath: String): Result<VideoMeta>` | 时长/宽高/旋转（`VideoMeta(durationMs, width, height, rotationDegrees)`） |
| `generateThumbnail` | `suspend fun generateThumbnail(absPath: String, maxWidthPx: Int = 480): Result<ThumbnailResult>` | 生成 JPEG 字节 + 同时返回 `durationMs`（一次 retriever 复用） |

**内部类/函数**

| 类/函数 | 参数 → 返回 | 功能/规则 |
|---------|-------------|-----------|
| `VideoScanner.listVideoFiles(root)` | `(File) → List<File>` | 只取 `root` 直接子级**普通文件**（`isFile`），跳过隐藏项（`.` 前缀或 `isHidden()`）、跳过 `size == 0`、按 `VideoFormats` 白名单过滤，结果按 `naturalOrderCompare` 排序（复用 v1 工具） |
| `VideoScanner.toSummary(root, file)` | `(VideoRoot, File) → VideoSummary` | `importedAt` 复用首次入库时间（刷新时由 data 层继承）；`thumbState = PENDING` |
| `VideoMetaReader.fromRetriever(r)` | `(MediaMetadataRetriever) → VideoMeta?` | 读 `METADATA_KEY_DURATION`、`VIDEO_WIDTH/HEIGHT`、`VIDEO_ROTATION`；任一关键项缺失返回 `null`（上层标记 FAILED） |
| `VideoThumbnailGenerator.frameAtMiddle(r, durationMs)` | `(MediaMetadataRetriever, Long) → Bitmap?` | `US = durationMs * 500`（即 50% 处，单位微秒），先 `OPTION_CLOSEST_SYNC`；若结果为空或“整图近黑”则用 `OPTION_CLOSEST` 重试一次 |
| `VideoThumbnailGenerator.isNearBlack(bmp)` | `(Bitmap) → Boolean` | 采样中心 20% 区域，平均亮度 < 16 视为黑帧（纯黑片头片尾保护） |
| `VideoThumbnailGenerator.encodeJpeg(bmp)` | `(Bitmap) → ByteArray` | 先按 `maxWidthPx = 480` 等比缩放（保持 16:9 原比例），`Bitmap.compress(JPEG, 85)`；单张约 15~40KB |
| `VideoThumbnailStore.pathFor(videoKeyId, sizeBytes, lastModified)` | `(String, Long, Long) → File` | 目录 `filesDir/video_thumbs/`；文件名 `md5Hex("$videoKeyId|$sizeBytes|$lastModified").jpg`；**大小或修改时间变化即换 key**，天然失效 |
| `VideoThumbnailStore.write / delete / clearAll` | — | 写临时文件后 `renameTo` 保证原子性；`delete` 幂等 |
| `VideoSourceModule` | Hilt `@Binds` | `VideoSource → LocalVideoSource`（`@InstallIn(SingletonComponent)`） |

**硬性约束**

1. `MediaMetadataRetriever` **必须成对 `release()`**（`use {}` 或 `try/finally`），否则连续滚动会触发 `MediaMetadataRetriever` 句柄泄漏与 OOM。
2. 竖拍视频需按 `rotationDegrees` 旋转 Bitmap 后再落盘，避免缩略图歪 90°。
3. 生成器全局并发由 `Semaphore(3)` 限制（见 §3.6），模块内部不自行开线程池。

### 3.5 `:core:data`（新增表、DAO、迁移与实现）

**Room 表（DB 版本 1 → 2）**

| 表 | 主键 | 字段 | 索引/约束 |
|----|------|------|-----------|
| `video_root` | `id` | path, display_name, added_at | `path` UNIQUE（同路径不重复导入，V11） |
| `video_cache` | `video_key_id` | root_id, file_name, abs_path, size_bytes, last_modified, imported_at, duration_ms(可空), thumb_path(可空), thumb_state(默认 0) | `root_id` 索引；`refresh` 时按 root 整表替换 |
| `video_progress` | `video_key_id` | position_ms, duration_ms, updated_at | 保存时保证行数 ≤ 3（LRU，V8） |

**迁移策略（`MIGRATION_1_2`）**

- 在 `ComicNestDatabase` 中 `version = 2`，`entities` 追加三个实体；`Room.databaseBuilder(...).addMigrations(MIGRATION_1_2)`。
- `MIGRATION_1_2` 只执行三条 `CREATE TABLE`（含索引）与 `IF NOT EXISTS` 保护，**不触碰 v1 的五张表**，保证升级后漫画书库、收藏、阅读进度、库根全部保留。
- 迁移 SQL 的列名/类型/可空性必须与实体声明逐字一致；否则 Room 会在启动时抛 `IllegalStateException`（v1 未开 `exportSchema`，无 schema 校验兜底，必须人工核对）。
- 禁止使用 `fallbackToDestructiveMigration()`。

**DAO 关键方法**

| DAO | 方法 | 说明 |
|-----|------|------|
| `VideoRootDao` | `observeAll()`、`insert`、`deleteById`、`getAllOnce()` | 视频根 CRUD（与 `RootDao` 同构，独立表） |
| `VideoDao` | `observeAllByName()`（自然序由 data 层排序）、`observeAllByImportedDesc()`、`replaceRoot(rootId, list)`（`@Transaction`）、`getById`、`upsertThumb(key, path, durationMs, thumbState, lastModified)`、`updateThumbState(key, state)`、`deleteById`、`getMetaById` | 视频缓存 CRUD；`replaceRoot` 保证单根扫描原子性 |
| `VideoProgressDao` | `upsert(p)`、`evictOldest(keep = 3)`、`observeRecent(limit = 3)`、`getById`、`deleteById` | **LRU 淘汰与 upsert 在同一事务**（`@Transaction` 方法内先 upsert 再 evict） |

**`VideoRepositoryImpl` 要点**

1. `refresh()`：`observeRoots()` 取全部根 → 并发 `scanLibrary`（`Semaphore(2)` 限流，避免多根同时列举造成 IO 抖动）→ 逐根 `replaceRoot` 前先读旧 `MetaMap(key → duration_ms/thumb_path/thumb_state/size/last_modified/imported_at)`；**若 `size` 与 `last_modified` 未变，则继承旧的缩略图与时长、`imported_at`**，仅 `PENDING/FAILED → PENDING` 重置失败标记，避免每次刷新重算全部缩略图。
2. `saveThumbnail()` / `markThumbnailFailed()`：单行 `UPDATE`，失败返回 `Result.failure`。
3. `deleteVideo(key)`：见 §3.7 的顺序，DB 侧最后执行 `deleteById` + `videoProgressDao.deleteById`。
4. `observeRecent()`：`videoProgressDao.observeRecent(3)` 与 `videoDao.observeAllByName()` `combine`，按 `video_key_id` 关联出 `RecentVideo`，**关联不到的条目直接丢弃**（文件已被外部删除）。
5. 全部方法 `runCatchingIo` 包裹并由 `AppLog` 记录失败上下文（key/path/耗时）。
6. `VideoRepositoryImpl` 与 `LibraryRepositoryImpl` 不共享任何 DAO 实例与状态；`DatabaseModule` 新增 `provideVideoRootDao/provideVideoDao/provideVideoProgressDao`，`RepositoryModule` 新增 `bindVideoRepository`。

### 3.6 缩略图流水线（专节：生成时机与生命周期）

**生成时机（按优先级）**

1. **可见优先**：卡片进入可见区（`LazyGridState.layoutInfo.visibleItemsInfo` ∩ 首屏）→ 触发 `EnsureVideoThumbnailUseCase(key)`；同一 key 在生成中则复用同一个 `Deferred`（去重，避免同一视频重复解码）。
2. **静止后补齐**：列表**停止滚动 300ms** 后，对“已缓存区域前后各 20 项”内的 `PENDING` 项排队补齐，队列容量上限 30，超出丢弃（下次静止再补）。滚动进行中**不启动新任务**，已有任务不打断。
3. **绝不预生成**：扫描/刷新阶段不生成任何缩略图。

**并发与资源**

- 全局 `Semaphore(3)`；单任务 `withContext(Dispatchers.IO)`。
- 每任务顺序：`retriever.setDataSource` → 读 `durationMs` → 取中间帧 → 缩略/旋转 → JPEG 编码 → 落盘 → DB 回写 → `release()`。
- 单任务超时 8s（`withTimeoutOrNull`），超时按失败处理并释放句柄。

**缓存键与失效**

- `key = md5Hex("$videoKeyId|$sizeBytes|$lastModified")`，落盘 `filesDir/video_thumbs/<key>.jpg`。
- 文件被同名替换/重新转码 → `size` 或 `lastModified` 变化 → key 变化 → 自动重新生成；旧文件由清理规则删除。
- 删除视频 / 移除根 / `FAILED` 重试前 → 删除对应文件。

**规模与容量控制**

- 单张 15~40KB；1000 个视频约 15~40MB，可接受；`video_thumbs` 总大小超过 **80MB** 时按 `lastModified` 从旧到新批量删除，直到低于 60MB。
- 每次 App 启动后首次进入视频页执行一次“孤儿清理”：`video_thumbs` 中存在但 `video_cache.thumb_path` 中不存在的文件（且创建时间早于 1 天）删除。

**失败与重试**

- 生成失败（空帧/解码异常/文件不可读）→ `thumb_state = FAILED`，卡片显示占位（图标 + 文件名），**不自动重试**；用户点“刷新”或重新导入根时重置为 `PENDING` 再试一次。
- 连续失败 10 个视频后，本页后续 `PENDING` 不再尝试（熔断），避免一圈“坏文件”持续消耗 CPU。

### 3.7 删除流水线（专节）

删除是**不可逆**操作，顺序固定如下，任何一步失败都不阻塞“文件删除”这一核心诉求：

1. **二次确认**：`AlertDialog`，标题“删除视频”，正文含文件名与“删除后不可恢复，文件将从设备中永久移除”。确认按钮为 `error` 色。
2. **清 MediaStore 记录**（尽力而为）：`ContentResolver.delete(MediaStore.Files.getContentUri("external"), "_data = ?", [absPath])`；若抛异常或返回 0 行，降级 `MediaScannerConnection.scanFile(absPath, null)`。两者都失败仅记日志（保证系统相册不残留失效条目）。
3. **删除文件**：`File(absPath).delete()`；返回 `false` 或文件仍存在 → 判定失败，返回 `Result.failure`，**不做**后续缓存清理，Snackbar 提示“删除失败，文件可能被占用或目录只读”。
4. **清缓存与记录**：删除缩略图文件、`video_progress` 对应行、`video_cache` 对应行。
5. **列表刷新**：以上均完成后由 DB Flow 自动移除该卡片；若该视频是“继续观看”之一，卡片同步消失。
6. **回滚与一致性**：步骤 4 失败不回滚文件删除（文件已删，缓存残留由 §3.6 孤儿清理兜底）；步骤 3 失败则完全不动 DB，保证“列表里还能看到该视频”。

新增一个“删除后立即重扫该根”的兜底开关：仅在 `deleteVideo` 返回 `success` 但 `video_cache` 行数未变化时触发一次 `refresh()`（防御 DB 写失败导致的“幽灵卡片”）。

### 3.8 `:feature:video`（页面与 ViewModel）

**`VideoLibraryViewModel`**

- 注入：`GetVideoLibraryUseCase`、`ScanVideoLibraryUseCase`、`AddVideoRootUseCase`、`RandomVideoUseCase`、`DeleteVideoUseCase`、`PreferencesRepository`。
- `UiState` 密封类：`Loading` / `Ready(videos, sortMode, roots, recent)` / `Empty(firstRun: Boolean)` / `NoResult(query)` / `Error(msg)`。
- 对外状态：`uiState`、`query: StateFlow<String>`（纯内存临时态，不持久化）、`totalCount: StateFlow<Int>`（= 全库视频数，不受搜索影响，供 30 分组划分）、`refreshing: StateFlow<Boolean>`、`pendingDelete: StateFlow<VideoSummary?>`（确认框数据）。
- 事件：`onRefresh()`、`onSortChange(mode)`、`onQueryChange(text)`、`onAddRoot(path)`、`onRandom()`、`onOpenVideo(key)`、`onLongPressVideo(summary)`、`onRequestDelete(summary)`、`onConfirmDelete()`、`onDismissDelete()`、`onThumbnailVisible(key)`。
- 一次性事件（`Channel`）：`NavigateToPlayer(videoKeyId)`、`Message(text)`（含“扫描完成，共 N 个视频”“删除失败…”）。
- 分组常量 `VIDEO_GROUP_SIZE = 30`（V3），划分逻辑与 v1 书架完全一致：`ceil(totalCount / 30)` 项，`第1组 1–30`、`第2组 31–60`…，仅当 `totalCount > 30` 时展示；点组先清空搜索再 `scrollToItem(index * 30)`。
- 缩略图触达：`onThumbnailVisible` 进入 `viewModelScope` 的 `Semaphore(3)` 队列（队列在 VM 内，VM 销毁即取消，避免切页后继续解码）。

**`VideoPlayerViewModel`**

- 注入：`VideoRepository`/`SaveVideoProgressUseCase`、`@ApplicationContext Context`（构造 `ExoPlayer`）。
- `UiState`：`Loading` / `Ready(summary, startPositionMs)` / `Error(msg)`。
- 播放状态（独立 `StateFlow<PlaybackUiState>`）：`isPlaying`、`positionMs`、`durationMs`、`speed(0.5/1/1.5/2)`、`controlsVisible`、`gestureOverlay`（`null` / `Brightness(percent)` / `Volume(percent)` / `SeekPreview(deltaMs)`）、`isLandscape`。
- 事件：`onSurfaceReady`、`onTogglePlay`、`onSeekBy(deltaMs = ±15_000)`、`onSeekTo(ms)`、`onSpeedChange(speed)`、`onToggleOrientation()`、`onToggleControls()`、`onPlaybackEnded()`（停末帧、不自动连播）、`onStop()`。
- 进度写入：播放中每 **5s** 节流一次；`onStop`/`onCleared` 强制写一次；均经 `SaveVideoProgressUseCase`（内部执行 ≥10s 阈值与夹取，V8）。
- 生命周期：`ExoPlayer` 在 `init` 创建、`onCleared` 释放；`AudioFocus` 用 `AudioAttributes` + `handleAudioFocus = true`；播放页 `keepScreenOn`。

**Screens**

- `VideoLibraryScreen`：`ModalNavigationDrawer` + `Scaffold` + `TopAppBar` + `LazyVerticalGrid`；“继续观看”区（最多 3 张 16:9 卡）位于网格之上且**仅在搜索为空时展示**；空态/未导入/无匹配/错误四态复用 v1 组件。
- `VideoPlayerScreen`：黑底全屏，`AndroidView(PlayerView)` + 自绘控制层 + 手势层；进入时隐藏系统栏（沉浸式），退出恢复。

### 3.9 `:app`（导航、底栏与系统配置）

- `BottomDestination` 新增 `Video(Routes.VIDEOS, "视频", Icons.Filled.Movie)`，插在 `Library` 与 `Collections` 之间（枚举顺序即底栏顺序，V1）。`BOTTOM_BAR_ROUTES` 由枚举派生，**自动只含 4 个根路由**。
- 新路由：`Routes.VIDEOS = "videos"`、`Routes.VIDEO_PLAYER = "video_player"`；播放页路径 `video_player/{videoKeyId}`，参数用 `Uri.encode` 编码（与 v1 阅读器一致）。
- 播放页不进入底栏 → 自动全屏；返回时 `popBackStack()`。
- **横竖屏**：`MainActivity` 已声明 `configChanges="orientation|screenSize|keyboardHidden|uiMode"`，方向切换不重建 Activity，`ExoPlayer` 与 Compose 状态天然保留；`onToggleOrientation()` 通过 `LocalContext` 取 `ComponentActivity.requestedOrientation`（竖→`SCREEN_ORIENTATION_SENSOR_LANDSCAPE`，横→`SCREEN_ORIENTATION_PORTRAIT`），退出播放页时恢复 `SCREEN_ORIENTATION_UNSPECIFIED`。
- 权限：沿用 `MANAGE_EXTERNAL_STORAGE`（已在 manifest 声明），**不新增任何权限**。
- `settings.gradle.kts` 注册 `:source:video`、`:feature:video`；`app/build.gradle.kts` 依赖二者。

### 3.10 模块交互时序（关键路径）

1. **首次导入**：设置页“视频库目录”→“添加目录”→ `AddVideoRootUseCase` → `refresh()` → 弹出“共 N 个视频”（失败保留旧数据）。
2. **进入视频页**：`GetVideoLibraryUseCase()` 四流合并 → `Ready` 渲染（先文件名 + 占位）→ 可见项触发缩略图 → DB Flow 回填 → 卡片出图与时长。
3. **播放**：卡片点击 → `NavigateToPlayer(key)` → 播放页 `Loading` → `getVideo` + `getProgress` → `Ready(summary, startPositionMs)` → 播放 → 每 5s 落库。
4. **返回**：`onStop` 落库 → 返回列表 → “继续观看”首位即该视频（`observeRecent` 已更新）。
5. **删除**：长按 → 菜单 → 确认 → `DeleteVideoUseCase`（§3.7 五步）→ DB Flow 移除卡片 → Snackbar“已删除”。

---

## 4. 各模块测试方案与单模块验收标准

> 测试框架沿用 v1：JUnit4 + kotlinx-coroutines-test + MockK + Turbine；Room 用 in-memory；`:source:video` 用临时目录 fixture。

| 模块 | 测试方案 | 单模块验收标准 |
|------|----------|----------------|
| `:core:common` | `VideoFormats` 白名单命中/大小写/无扩展名/多点文件名（`a.b.mp4`）；`formatDuration` 0/负/null/59s/61min | 白名单 7 种全中、其余全否；时长格式正确 |
| `:core:domain` | `SaveVideoProgressUseCase`（<10s 不写、=10s 写、超 duration 夹取）；`RandomVideoUseCase`（空库 failure）；`DeleteVideoUseCase` 委托与失败透传；`GetVideoLibraryUseCase` 四流合并 | 阈值/夹取/空库规则与 V8 一致 |
| `:core:data` | Room in-memory：`VideoProgressDao` 连插 4 条仅剩最近 3 条；`replaceRoot` 原子性；`refresh` 继承 `thumb_state/imported_at`（size+mtime 不变时）；`deleteVideo` 后三表皆清 | LRU=3 精确；继承规则正确；删除无残留 |
| `:source:video` | 临时目录：一级文件、子目录内文件不扫、隐藏跳过、0 字节跳过、avi 等被过滤、自然序（`v2` < `v10`）；缩略图 key 随 size/mtime 变化；近黑判定 | 扫描/过滤/排序/key 失效四项正确 |
| `:feature:video` | `VideoLibraryViewModel`：搜索命中/未命中/空串、`totalCount` 不受搜索影响、30 分组边界（30/31/60）、随机导航事件、删除事件；`VideoPlayerViewModel`：进度节流（5s 窗口内只写一次）、倍速与方向状态、`onStop` 强制写 | 状态机与事件映射正确；LRU 与阈值不被 UI 层绕过 |
| 回归（漫画侧） | 复用 v1 既有单测；额外加“DB v1 → v2 迁移后 `comic_cache/collection/read_progress/library_root` 行数不变” | 漫画侧单测全绿；迁移后数据零丢失 |

**单模块通用验收**：模块编译通过、单测全绿、无 lint error（warning 可接受）、对外接口与 §3 定义一致。

---

## 5. 开发顺序（阶段推进）

| 阶段 | 内容 | 出口条件 |
|------|------|----------|
| **V0 依赖与迁移** | `libs.versions.toml` 增 media3 1.5.1；注册 `:source:video`、`:feature:video`；`ComicNestDatabase` v2 + `MIGRATION_1_2` + 三实体/DAO；`versionName 1.2.0 / versionCode 3` | 依赖可解析（**若离线拉取失败，先解决 Gradle 代理/镜像再继续**）；升级安装后漫画数据零丢失；`:app` 可出 Debug 包 |
| **V1 模型与扫描** | `:core:model`、`:core:common`、`:source:video`（扫描/格式/元数据） | 扫描与格式单测全绿；对真实视频目录能列出正确条数 |
| **V2 数据与领域** | `:core:domain` 接口与 UseCase、`:core:data` 实现与 DI | LRU=3、继承规则、删除用例单测全绿 |
| **V3 视频页骨架** | `:feature:video` 网格 + 顶栏 + 抽屉（30 分组/搜索）+ 随机/刷新/排序 + 空态；`:app` 底栏与路由；设置页视频库目录分区 | 实机可导入目录、看到文件名网格、分组与搜索可用 |
| **V4 缩略图** | 生成器 + 缓存 store + 可见触发 + 静止补齐 + 失败熔断 + 孤儿清理 | 首屏 1~3s 内出图；连续快速滚动不卡顿、不 OOM |
| **V5 播放器** | `VideoPlayerScreen` + `ExoPlayer` 封装 + 控件（进度/±15s/倍速/方向）+ 手势 + 结束停末帧 | 实机可播放、四种倍速可用、手势生效、横竖屏不中断 |
| **V6 记录与删除** | `SaveVideoProgress` 节流、继续观看卡片、长按菜单 + 确认框 + 删除流水线 | 读 4 个视频后仅剩 3 条记录；删除后文件/相册/缓存/列表四处一致 |
| **V7 打磨与交付** | 中文化校对、异常态文案、大目录性能（1000 视频）、README/CHANGELOG、Release 签名包 | 出 `ComicNest_1.2.0.apk`，§7 全部 AC 通过 |

---

## 6. 项目目录规则与文件树规划（本次新增/改动）

### 6.1 目录规则

- 模块命名沿用 v1：`:core:*` 底座、`:source:*` 来源、`:feature:*` 业务页、`:app` 壳。本次新增 `:source:video`（与 `:source:local` 平级）与 `:feature:video`（与 `:feature:library` 平级）。
- 包结构：`com.comicnest.<module>.<layer>`；feature 内分 `ui/`（Composable + Screen）、`viewmodel/`；`:source:video` 内分 `scan/`、`thumb/`、`meta/`、`di/`。
- 视频域文件统一以 `Video` 前缀命名，避免与漫画域同名类混淆（如 `VideoSummary` vs `ComicSummary`）。
- 缩略图缓存只写 App 私有目录 `filesDir/video_thumbs/`，**不写用户媒体目录**（沿用 v1“只读用户文件”的语义）。

### 6.2 文件树（新增文件）

```
ComicNest/
├─ gradle/libs.versions.toml                    # 【改】新增 media3 版本与库条目
├─ settings.gradle.kts                          # 【改】include(":source:video", ":feature:video")
├─ app/
│  ├─ build.gradle.kts                          # 【改】依赖 :feature:video、:source:video；版本号 1.2.0/3
│  └─ src/main/kotlin/com/comicnest/navigation/
│     └─ ComicNestNavHost.kt                    # 【改】底栏 4 项、videos / video_player 路由
├─ core/
│  ├─ model/src/main/kotlin/com/comicnest/core/model/
│  │  ├─ VideoKey.kt                            # 【新】VideoKey
│  │  ├─ VideoRoot.kt                           # 【新】VideoRoot
│  │  ├─ VideoSummary.kt                        # 【新】VideoSummary + VideoThumbnailState
│  │  └─ VideoProgress.kt                       # 【新】VideoProgress + VideoSortMode + RecentVideo
│  ├─ common/src/main/kotlin/com/comicnest/core/common/util/
│  │  ├─ VideoFormats.kt                        # 【新】白名单 + formatDuration + md5Hex
│  │  └─ src/test/.../VideoFormatsTest.kt       # 【新】白名单/时长格式单测
│  ├─ domain/src/main/kotlin/com/comicnest/core/domain/
│  │  ├─ repository/VideoRepository.kt          # 【新】接口
│  │  └─ usecase/video/                         # 【新】8 个视频 UseCase
│  │     ├─ GetVideoLibraryUseCase.kt
│  │     ├─ ScanVideoLibraryUseCase.kt
│  │     ├─ AddVideoRootUseCase.kt
│  │     ├─ RemoveVideoRootUseCase.kt
│  │     ├─ RandomVideoUseCase.kt
│  │     ├─ DeleteVideoUseCase.kt
│  │     ├─ SaveVideoProgressUseCase.kt
│  │     └─ EnsureVideoThumbnailUseCase.kt
│  ├─ data/src/main/kotlin/com/comicnest/core/data/
│  │  ├─ db/entity/VideoEntities.kt             # 【新】三实体
│  │  ├─ db/dao/VideoDaos.kt                    # 【新】三 DAO
│  │  ├─ db/ComicNestDatabase.kt                # 【改】version=2、追加实体/DAO、新增 MIGRATION_1_2
│  │  ├─ mapper/VideoMappers.kt                 # 【新】实体 ↔ 模型
│  │  ├─ repository/VideoRepositoryImpl.kt      # 【新】实现（含删除流水线、缩略图回写）
│  │  └─ di/DataModule.kt                       # 【改】三 DAO provide + bindVideoRepository
│  └─ ui/src/main/kotlin/com/comicnest/core/ui/component/
│     └─ DurationBadge.kt                       # 【新】时长角标（小，供视频卡片用；如仅一处使用可内联）
├─ source/
│  └─ video/
│     ├─ build.gradle.kts                       # 【新】namespace com.comicnest.source.video
│     └─ src/main/kotlin/com/comicnest/source/video/
│        ├─ VideoSource.kt                      # 【新】接口 + VideoMeta/ThumbnailResult
│        ├─ LocalVideoSource.kt                 # 【新】实现
│        ├─ scan/VideoScanner.kt                # 【新】一级文件扫描/过滤/排序
│        ├─ meta/VideoMetaReader.kt             # 【新】retriever → VideoMeta
│        ├─ thumb/VideoThumbnailGenerator.kt    # 【新】中间帧/黑帧降级/编码
│        ├─ thumb/VideoThumbnailStore.kt        # 【新】缓存路径/key/清理
│        └─ di/VideoSourceModule.kt             # 【新】@Binds
├─ feature/
│  └─ video/
│     ├─ build.gradle.kts                       # 【新】namespace com.comicnest.feature.video
│     └─ src/main/kotlin/com/comicnest/feature/video/
│        ├─ ui/VideoLibraryScreen.kt            # 【新】网格 + 继续观看 + 四态
│        ├─ ui/components/VideoDrawer.kt        # 【新】搜索框 + 30 分组跳转
│        ├─ ui/components/VideoCard.kt          # 【新】16:9 卡片 + 时长角标 + 占位/失败态
│        ├─ ui/components/VideoActionSheet.kt   # 【新】长按菜单（播放/删除）+ 确认框
│        ├─ ui/VideoPlayerScreen.kt             # 【新】PlayerView + 控制层 + 手势层
│        ├─ ui/components/PlayerControls.kt     # 【新】进度条/±15s/倍速/方向
│        ├─ viewmodel/VideoLibraryViewModel.kt  # 【新】
│        └─ viewmodel/VideoPlayerViewModel.kt   # 【新】
└─ feature/settings/src/main/kotlin/com/comicnest/feature/settings/
   ├─ ui/SettingsScreen.kt                      # 【改】“视频库目录”分区（阅读与关于之间）
   └─ viewmodel/SettingsViewModel.kt            # 【改】videoRoots/videoAvailable 状态与增删事件
```

### 6.3 每个文件内容要点

- `*ViewModel.kt`：`StateFlow<UiState>` + 事件函数；不直接碰 IO（缩略图触发也只发 UseCase 调用）。
- `*Screen.kt`：只渲染 UiState；不含业务判断；手势层只上报事件（`onSeekBy`/`onBrightness`/`onVolume`）。
- `VideoRepositoryImpl.kt`：唯一允许同时接触 DAO 与 `VideoSource` 的地方；删除、缩略图回写、扫描继承均在此收口。
- `LocalVideoSource.kt`：不持久化、不缓存路径（缓存路径由 `VideoThumbnailStore` 决定并回传绝对路径给 data 层入库）。
- `SettingsViewModel`：新增 `videoRoots: StateFlow<List<VideoRoot>>`、`onAddVideoRoot(path)`、`onRemoveVideoRoot(root)`；与漫画 roots 完全独立（两个列表、两个添加按钮）。

### 6.4 依赖与版本增项（`gradle/libs.versions.toml`）

| 位置 | 增项 | 说明 |
|------|------|------|
| `[versions]` | `media3 = "1.5.1"` | 与 compileSdk 35 / Kotlin 2.0.21 兼容的稳定版 |
| `[libraries]` | `androidx-media3-exoplayer`、`androidx-media3-ui` | ExoPlayer 内核 + `PlayerView`（`useController=false`） |
| `[libraries]` | `androidx-media3-common`（随 exoplayer 传递，可不显式） | 仅在需要 `MediaItem`/`PlaybackException` 时显式 |
| `settings.gradle.kts` | `include(":source:video")`、`include(":feature:video")` | 模块注册 |

模块依赖约定：`:source:video` 只依赖 `:core:model` + `:core:common` + Hilt；`:feature:video` 依赖 `:core:ui`、`:core:domain`、`:core:model` + lifecycle/compose/hilt/media3-ui；`:core:data` 新增 `implementation(projects.source.video)`。

---

## 7. 集成/联调测试与整体验收

### 7.1 集成测试（IT9~IT15，续 v1 的 IT1~IT8）

| 编号 | 场景 | 步骤 | 预期 |
|------|------|------|------|
| IT9 | 导入→扫描→缩略图 | 设置页添加含 200 个 mp4 的目录 → 回到视频页 | 列表出现全部视频；缩略图为**中间帧**（非首帧）；时长角标正确；首屏 1~3s 出图 |
| IT10 | 分组与搜索 | 视频数 >30 | 抽屉出现“第1组 1–30”等；点第 3 组滚动到第 61 项；搜索“abc”即时过滤且分组区不变；无命中显示“无匹配视频”而非“添加目录”引导 |
| IT11 | 播放基础 | 点开视频 | 自动播放；进度条可拖拽；±15s 准确；0.5/1/1.5/2 四档生效（声音不变调） |
| IT12 | 手势与方向 | 播放中双击/左右滑/上下滑/点方向按钮 | 双击暂停恢复；左右滑出现 seek 预览并生效；左右半屏分别调亮度/音量；横竖屏切换不中断、不重头播 |
| IT13 | 最近观看覆盖 | 依次看完（>10s）4 个不同视频 | “继续观看”始终只有 3 张，最早的被覆盖；点卡片直接从上次位置续播（偏差 ≤10s） |
| IT14 | 删除全链路 | 长按→删除→确认 | 二次确认含文件名；文件从设备消失；系统相册不再显示该条；缩略图缓存/记录清除；列表即时移除；“继续观看”同步更新 |
| IT15 | 升级与回归 | 先用 v1.1.0 建库/收藏/读进度，再覆盖安装 v1.2.0 | 启动不崩；书架、收藏、阅读进度、库根全部保留；视频功能可用 |

### 7.2 整体验收标准（AC1~AC12）

| 编号 | 验收项 |
|------|--------|
| AC1 | 底栏为 4 项且“视频”位于书架与收藏之间 |
| AC2 | 视频库目录在设置页“阅读”与“关于”之间，可多目录、可移除，且只扫描直接下一级文件 |
| AC3 | 缩略图为视频中间帧，竖拍视频方向正确，黑帧自动降级 |
| AC4 | 抽屉每 30 个一组，搜索为文件名模糊查询，点组跳转准确 |
| AC5 | 随机与刷新按钮存在且图标与书架一致；视频页无收藏入口 |
| AC6 | 播放器具备进度拖拽、±15s、四档倍速、横竖屏切换 |
| AC7 | 手势具备：双击暂停、左右滑 seek、左侧亮度/右侧音量 |
| AC8 | 最近观看恰好 3 条，第 4 条覆盖最早，且仅记录 ≥10s 的播放 |
| AC9 | 白名单外格式（avi/rmvb/wmv/flv）不出现在列表中 |
| AC10 | 删除经二次确认，文件/MediaStore/缩略图/记录/列表五处一致 |
| AC11 | 视频页与播放页文案全中文，视觉风格与漫画侧一致 |
| AC12 | Release 签名包 `ComicNest_1.2.0.apk` 可侧载安装、不触发“测试版拒装” |

---

## 8. 异常处理与状态流转

### 8.1 总则

- 所有文件 IO / 媒体读取 / 删除 / DB 操作包裹 `runCatchingIo`；异常 → `Result.failure` → UiState 提示或占位，不抛到主线程。
- 播放器错误（`PlaybackException`）分类：`ERROR_CODE_IO_FILE_NOT_FOUND` → “文件已不存在，已从列表移除”；解码不支持 → “该视频编码不被当前设备支持”；其余 → “播放失败，请重试”。
- `MediaMetadataRetriever` 与 `ExoPlayer` 的生命周期释放写入代码评审检查项。

### 8.2 分场景异常策略

| 场景 | 检测 | 处理 |
|------|------|------|
| 视频根目录失效/被删 | 扫描前 `exists && isDirectory` | 该根标红；视频页可提示并直接移除；不影响其他根 |
| 目录内无视频 | 扫描结果为空 | `Empty(firstRun=false)`，引导去设置添加目录 |
| 单视频 0 字节/损坏 | `size == 0` 或 `readMeta` 为空 | 扫描阶段直接跳过；缩略图阶段失败则 `FAILED` 并显占位 |
| 缩略图取到黑帧 | `isNearBlack` | 降级 `OPTION_CLOSEST` 重试一次；仍黑则视为失败 |
| 缩略图生成超时（>8s） | `withTimeoutOrNull` | 标 `FAILED`，释放 retriever，不阻塞队列 |
| 连续大量失败 | 失败计数 ≥10 | 本页熔断，不再尝试，提示一次“部分视频无法生成预览” |
| 文件在进入播放前被外部删除 | `getVideo` 后校验 `exists` | 提示“文件已不存在”并 popBackStack |
| 播放中文件被删/拔 SD 卡 | `PlaybackException` | 停止播放，提示并返回列表 |
| 旋转/返回时进度丢失 | `onStop` + `onCleared` 强制写 | 进度不丢；且写入失败不影响退页 |
| 删除时文件被占用/只读 | `delete()` 返回 false | 不删 DB，提示失败原因 |
| MediaStore 清理失败 | 异常/影响 0 行 | 降级 scanFile；仍失败只记日志，不阻断 |
| 删除后 DB 行未减少 | 删除后行数比对 | 自动触发一次 `refresh()` 兜底 |
| 缩略图缓存膨胀 | 目录总大小 >80MB | 按 `lastModified` 从旧到新删除至 <60MB |
| 孤立缩略图 | `thumb_path` 不存在的文件且创建早于 1 天 | 启动后首次进视频页清理 |
| DB 迁移失败 | Room 抛异常 | 不静默重建：捕获后提示“数据升级失败”，保留原 DB 文件供排查（v1 无 schema 导出的代价，靠单测先行验证） |

### 8.3 状态流转

- **视频页**：`Loading → Ready | Empty | NoResult | Error`；`onRefresh` 期间保持 `Ready` 并叠加顶部进度条（沿用 v1 书架做法，避免整屏 Loading）。
- **播放器页**：`Loading → Ready(summary,startPos) | Error`；`Ready` 内维护 `PlaybackUiState`（isPlaying/position/duration/speed/controlsVisible/gestureOverlay）。
- **删除**：`Ready` + `pendingDelete != null`（弹窗）→ 确认后 `deleting=true`（按钮禁用）→ 成功后 DB Flow 驱动列表更新 → Snackbar 一次性事件。
- **一次性事件**（导航、Snackbar）统一用 `Channel`/`SharedFlow`，避免重组重放。

---

## 9. 前端 UI 设计（新增页面）

> 基座沿用 v1：Compose + Material 3 + 单 Activity；全中文文案；触控目标 ≥ 48dp；深浅色自适应。

### 9.1 全局导航骨架（4 项）

```
┌───────────────────────────────────┐
│           [ 页面内容区 ]            │
├───────────────────────────────────┤
│  书架      视频      收藏      设置  │  ← NavigationBar（4 项）
└───────────────────────────────────┘
```

| 底部入口 | 路由 | feature | 备注 |
|----------|------|---------|------|
| 书架 | `library`（start） | `:feature:library` | 不变 |
| **视频** | `videos` | `:feature:video` | 新增 |
| 收藏 | `collections` | `:feature:collection` | 不变 |
| 设置 | `settings` | `:feature:settings` | 加视频库目录分区 |
| （全屏，不入底栏） | `video_player/{videoKeyId}` | `:feature:video` | 播放页黑底全屏 |

### 9.2 视觉规范（视频域 Tokens）

| 项 | 规格 |
|----|------|
| 缩略图比例 | **16:9**，圆角 12dp；内部 `ContentScale.Crop` |
| 网格列 | `GridCells.Adaptive(minSize = 160.dp)`（手机约 2 列，平板自动增列） |
| 卡片结构 | 缩略图（上）+ 文件名单行省略（下）；时长角标叠在缩略图**右下角** |
| 时长角标 | 黑底 60% 透明 + 白字 11sp，圆角 4dp，内边距 4×2dp，格式见 `formatDuration` |
| 文件名 | 13sp，单行 `ellipsis`；文本为去掉扩展名的文件名（扩展名由时长角标区暗示） |
| 占位态 | `thumbState=PENDING` → 深色底 + 加载指示；`FAILED` → 深色底 + 断图图标 |
| 继续观看卡 | 横向 3 等分（窄屏改为横向可滚动），16:9 + 底部 2dp 进度条 + 右上角“剩余 mm:ss” |
| 播放页 | 纯黑背景，控件区底部渐变遮罩；图标 24dp，触控区 48dp |
| 动效 | 卡片缩略图淡入 150ms；控件显隐 200ms 渐变；Snackbar 标准 |

### 9.3 视频页（VideoLibraryScreen）

```
┌────────────────────────────────────────────┐
│ ☰  视频        [排序⇅] [随机] [刷新]       │
├────────────────────────────────────────────┤
│ 继续观看                 (仅搜索为空时显示)   │
│ ┌──────┐ ┌──────┐ ┌──────┐                 │
│ │16:9  │ │16:9  │ │16:9  │  ← 最多 3 张      │
│ │▬▬▬───│ │▬─────│ │▬▬▬▬▬─│  底部进度条        │
│ └──────┘ └──────┘ └──────┘                 │
│ ┌──────┐ ┌──────┐                          │
│ │16:9  │ │16:9  │   ← VideoCard（时长角标）   │
│ │ 12:34│ │ 1:02:03                         │
│ └──────┘ └──────┘                          │
│  文件名…    文件名…       … 无限滚动          │
├────────────────────────────────────────────┤
│  书架      视频      收藏      设置          │
└────────────────────────────────────────────┘
```

- 顶栏：☰（开抽屉）、标题“视频”、`Sort`（名称/导入时间）、`Casino`（随机）、`Refresh`（刷新）—— 与书架图标一致（V3/V5）。
- 卡片点击→播放；**长按**→ 底部动作菜单（播放 / 删除）—— 视频无收藏动作（V14）。
- 状态：`Empty(firstRun=true)` → 居中引导“添加视频目录”（跳设置页或直接弹目录选择器）；`Empty(firstRun=false)` → “该目录下暂无视频”；`NoResult` → “无匹配视频”；`Error` → 重试。

### 9.4 左侧抽屉（搜索 + 每 50 个一组）

```
┌────────────────────────────┐
│ 搜索视频文件名        [✕]   │
│ ────────────────────────── │
│ 分组跳转 · 共 87 个         │
│ 第1组  1–30      ✓(当前)    │
│ 第2组  31–60                │
│ 第3组  61–87                │
│ ────────────────────────── │
│ 视频库目录：A、B  [添加]     │
└────────────────────────────┘
```

- 搜索：`OutlinedTextField`，前导搜索图标、尾部✕，`singleLine`，圆角 24dp；输入即内存过滤（v2.2 起为“空格分隔多关键词 AND 匹配”，见 §11 R1），抽屉保持展开。
- 分组：`VIDEO_GROUP_SIZE = 50`，按**全库总数**划分且与搜索无关；≤50 时显示“视频不足 50 个，无需分组”；点组先清空搜索再 `scrollToItem`。
- 当前组高亮：`firstVisibleItemIndex / 50` 派生（仅在搜索为空时高亮，与 v1 §10.3 一致）。

### 9.5 播放页（VideoPlayerScreen）

> **v2.2 起底部控制层改为三层结构（时间 / 细进度条 / 按钮）并移除 ±15s，下方示意图仅作历史参考，最新规格以 §11 R4 为准。**

```
横屏（推荐方向）
┌──────────────────────────────────────────────────────┐
│ ← 文件名.mp4                                           │
│                                                      │
│                    [ 视频画面 ]                         │
│                                                      │
│  ──────●──────────────────────────────────────────  │
│  02:31 / 12:40   -15s  播放/暂停  +15s  倍速  全屏  方向 │
└──────────────────────────────────────────────────────┘

手势区（全屏透明层）：
双击   → 暂停/继续
左右滑 → 出现 [⇠ 00:15] / [00:10 ⇢] 预览，松手 seek
左侧上下滑 → 亮度百分比气泡   右侧上下滑 → 音量百分比气泡
单击   → 显示/隐藏控件（含系统栏）
```

- 控制层自动隐藏 3s（播放中）；暂停时不隐藏。
- 倍速按钮循环切换 `0.5 → 1.0 → 1.5 → 2.0`，当前档位文案直显（不做记忆，V14）。
- 方向按钮图标随当前方向切换；退出页面恢复 `UNSPECIFIED`。
- 亮度调节只改当前窗口 `WindowManager.LayoutParams.screenBrightness`（不改系统设置，退出即恢复）；音量走 `AudioManager` 媒体流。
- 播放结束：停在末帧 + 显示重播按钮（不自动连播）。

### 9.6 长按菜单与删除确认框

```
底部动作菜单（ModalBottomSheet）      删除确认（AlertDialog）
┌────────────────────────┐          ┌──────────────────────────┐
│ 播放                     │          │ 删除视频                   │
│ 删除（error 色）          │          │ 确定删除“xxx.mp4”？          │
└────────────────────────┘          │ 删除后不可恢复，文件将从设备   │
                                    │ 中永久移除。                 │
                                    │        [取消]  [删除]      │
                                    └──────────────────────────┘
```

### 9.7 设置页（新增分区）

> **v2.2 调整**：分区顺序为 漫画库目录 → 漫画阅读方式 → 视频库目录 → **视频播放方式（新增）** → 关于；阅读方式选项去掉英文、新增“默认视频朝向”（见 §11 R5/R6）。

```
┌──────────────────────────────────────────┐
│ 设置                                        │
│ 漫画库目录                                  │
│  • 目录A (/storage/…/A)        [移除]       │
│  [+ 添加目录]                               │
│ ─────────────────────────────────────────  │
│ 阅读                                        │
│  • 默认阅读方式        [下拉式 ▾]            │
│ ─────────────────────────────────────────  │
│ 视频库目录                       ← 新增      │
│  • 视频A (/storage/…/Videos)   [移除]       │
│  • 视频B (/storage/…/Movies)   [失效]       │
│  [+ 添加视频目录]                            │
│ ─────────────────────────────────────────  │
│ 关于                                        │
│  • 版本 1.2.0     • 开源许可                 │
└──────────────────────────────────────────┘
```

- 分区顺序固定：漫画库目录 → 阅读 → **视频库目录** → 关于（V2）。
- 视频目录条目显示 `displayName` + 绝对路径；移除按钮与漫画一致；失效目录红字提示（路径不存在）。

### 9.8 组件与页面映射

| 页面/组件 | 复用 | 关键 Compose 构件 |
|-----------|------|-------------------|
| 视频网格 | `LoadingState`/`ErrorState`（v1 core:ui） | `LazyVerticalGrid`、`VideoCard` |
| 抽屉 | v1 抽屉写法 | `ModalNavigationDrawer`、`OutlinedTextField`、`LazyColumn` |
| 继续观看 | — | `LazyRow` + 横向 16:9 卡片 |
| 播放页 | — | `AndroidView(PlayerView)`、`Slider`、`IconButton`、`pointerInput` |
| 菜单/确认 | — | `ModalBottomSheet`、`AlertDialog` |

- UI 层不含业务判断：缩略图是否生成、进度是否写入、删除是否允许，全部由 ViewModel/UseCase 决定。

---

## 10. 变更影响、回归与不做项

### 10.1 涉及文件清单（改/新）

| 模块 | 文件 | 变更 |
|------|------|------|
| 根 | `gradle/libs.versions.toml` | 新增 media3 版本与库条目 |
| 根 | `settings.gradle.kts` | include 两个新模块 |
| `:app` | `build.gradle.kts` | 依赖新模块；versionName 1.2.0 / versionCode 3 |
| `:app` | `navigation/ComicNestNavHost.kt` | 底栏 4 项；`videos`、`video_player/{videoKeyId}` 路由；播放页全屏 |
| `:core:model` | `VideoKey/VideoRoot/VideoSummary/VideoProgress` | 新增 4 文件（含 2 枚举 + RecentVideo） |
| `:core:common` | `VideoFormats.kt` + test | 新增 |
| `:core:domain` | `repository/VideoRepository.kt`、`usecase/video/*` | 新增 |
| `:core:data` | `db/entity/VideoEntities.kt`、`db/dao/VideoDaos.kt`、`mapper/VideoMappers.kt`、`repository/VideoRepositoryImpl.kt` | 新增 |
| `:core:data` | `db/ComicNestDatabase.kt`、`di/DataModule.kt` | 改：版本 2 + 迁移 + 三 DAO + 绑定 |
| `:source:video` | 整个模块 | 新增 |
| `:feature:video` | 整个模块 | 新增 |
| `:feature:settings` | `SettingsScreen.kt`、`SettingsViewModel.kt` | 改：视频库目录分区与状态 |
| 文档 | `README.md`、`CHANGELOG`（用户要求时） | 更新功能列表与版本号；本次设计**只新增本文档**，不改 `docs/DESIGN.md` |

### 10.2 漫画侧回归必测（改动波及点）

1. 升级安装后：书架条目数/封面/排序不变；收藏夹与多对多关系完好；阅读进度可续读；库根列表正常（**DB v1→v2 迁移正确性**）。
2. 书架抽屉：搜索与 100 分组跳转、随机、刷新不被底栏改动影响。
3. 阅读器：翻页、模式切换、收藏入口、退出返回滚动位置保持。
4. 设置页：漫画库目录增删仍正常（与视频目录互不干扰）。
5. 底栏 4 项切换：`saveState/restoreState` 行为不变（视频页与书架互不串状态）。

### 10.3 明确不做项（写入验收口径，避免后续扯皮）

- 画中画（PiP）、后台/锁屏播放、通知栏媒体控制、耳机线控。
- 字幕外挂/内嵌切换（ExoPlayer 默认不显示字幕轨）。
- 倍速与音量记忆、自动连播、播放列表模式。
- 视频收藏、视频标签、视频重命名/移动。
- 网络流播放（http/rtsp/smb 等）。
- AVI / WMV / RMVB / FLV 等需第三方解码器的格式（V9）。
- 视频缩略图手动选择帧（用户指定某个时间点的画面）。

### 10.4 参考项目与许可

| 项目 | 链接 | 用途 | 许可 | 使用方式 |
|------|------|------|------|----------|
| NextPlayer | `https://github.com/anilbeesetti/nextplayer` | 播放器 UI 分层、Media3 封装、列表与播放页交互参考 | GPL-3.0 | 仅阅读思路，**零代码/零资源复制** |
| mpv-android | `https://github.com/mpv-android/mpv-android` | 格式覆盖面与本地播放器能力参照 | GPL-2.0+ | 仅作能力参照，不引入 native 库 |
| Media3 ExoPlayer | `https://github.com/androidx/media` | 播放内核与 `PlayerView` | Apache-2.0 | 以 Maven 依赖引入；`NOTICE` 追加 “AndroidX Media3 (Apache-2.0)” 归属 |

> 若日后需引入 `media3-ui-compose`、FFmpeg 扩展（`media3-decoder-ffmpeg`）或 mpv 系 native 库，须先评估许可与 APK 体积（后者会增加 8~20MB），并另开设计变更。

---

## 11. v2.2 迭代设计（R1~R6）

> 本节覆盖 2026-11 的 6 项体验迭代（R1~R6）。每项按“现状 → 目标 → 规则 → 代码落点 → 边界 → 测试”给出，实施者无需再做架构级决策。与 §0~§10 冲突时以本节为准。

### 11.0 迭代决策清单（D15~D22）

| 编号 | 对应需求 | 决策结论 |
|------|----------|----------|
| D15 | R1 搜索 | 以空白字符（半角空格 / 制表符 / 换行 / 全角空格 U+3000）分词，多关键词 **AND** 匹配、顺序无关、大小写不敏感；单关键词保持现行为 |
| D16 | R2 删除 | **先删文件、后清索引**；删除路径不再触发媒体扫描；删除前等待该文件的读取任务结束；`delete()` 短重试 3 次；退出播放页立即 `release()` 播放器 |
| D17 | R3 继续观看 | 数据层（表、LRU=3、≥10s 阈值、`getProgress` 续播）**全部保留**；仅移除视频页 UI，不做“可开关” |
| D18 | R4 播放器底栏 | 三层：① 左上时间 `8:21/23:05`；② 细进度条（3dp 轨道、5dp 圆点光标，拖动时 4.5dp/7dp）；③ 左（播放/暂停 + 下一个）· 右（倍速 + 旋转）；**移除 ±15s** |
| D19 | R4 下一个视频 | 顺序 = 当前搜索词 + 当前排序得到的列表；列表末项时按钮禁用（图标 35% 不透明）；切换在页内完成，不新建页面 |
| D20 | R5 设置文案 | 阅读方式选项去英文：`下拉式`、`左右翻页` |
| D21 | R6 默认视频朝向 | 新增偏好 `横屏 / 竖屏（默认）/ 根据视频比例`；进入播放页及切换视频时按当前视频应用，同一视频内不覆盖手动旋转；退出恢复 `UNSPECIFIED` |
| D22 | 版本 | `versionName = 2.2.0`、`versionCode = 6`；Release 产物 `ComicNest_2.2.0.apk` |

---

### 11.1 R1：书架 / 视频搜索支持多关键词（空格分隔）

#### 现状与问题

两处过滤均为“整串 `contains`”，空格被当成普通字符：

| 位置 | 代码 |
|------|------|
| 书架 | `feature/library/.../LibraryViewModel.kt`：`it.key.displayName.contains(keyword, ignoreCase = true)` |
| 视频 | `feature/video/.../VideoLibraryViewModel.kt`：`it.key.fileName.contains(keyword, ignoreCase = true)` |

因此输入 `胡桃 芙宁娜` 只能匹配文件名里**连续出现**“胡桃 芙宁娜”的名称，无法匹配 `xx胡桃xx芙宁娜`。

#### 目标规则（D15）

| 输入 | 解析结果 | 命中条件 |
|------|----------|----------|
| `胡桃` | `[胡桃]` | 名称包含“胡桃” |
| `胡桃 芙宁娜` | `[胡桃, 芙宁娜]` | 名称**同时**包含两者（顺序任意） |
| `  胡桃　　芙宁娜  ` | `[胡桃, 芙宁娜]` | 首尾空白丢弃；连续空白、全角空格、制表符都视为分隔符 |
| `胡桃  ` | `[胡桃]` | 与单关键词一致 |
| `   `（全空白） | `[]` | 等价于无搜索，显示全部（不进入 `NoResult`） |
| `胡桃 芙宁娜`（名称只含“胡桃”） | `[胡桃, 芙宁娜]` | **不命中**（AND 语义） |

- 匹配对象：漫画 `ComicKey.displayName`（不含路径）；视频 `VideoKey.fileName`（含扩展名，与现状一致）。
- 大小写：`ignoreCase = true`（保持现状）。
- 关键词数量上限：取前 **8** 个 token（防止粘贴长文本造成无效遍历），超出部分忽略。

#### 实现（新增共享纯函数）

新增 `core/common/src/main/kotlin/com/comicnest/core/common/util/SearchKeywords.kt`：

```kotlin
/** 分隔符：任意空白 + 全角空格（\u3000）。 */
private val KEYWORD_SEPARATOR = Regex("[\\s\\u3000]+")

/** 搜索词 → 关键词列表；空白串返回空列表（=不过滤）。 */
fun parseSearchKeywords(query: String): List<String> =
    query.trim()
        .split(KEYWORD_SEPARATOR)
        .filter { it.isNotEmpty() }
        .take(MAX_KEYWORDS)

/** 文本是否同时包含全部关键词（AND、顺序无关、忽略大小写）。 */
fun matchesAllKeywords(text: String, keywords: List<String>): Boolean =
    keywords.isEmpty() || keywords.all { text.contains(it, ignoreCase = true) }

private const val MAX_KEYWORDS = 8
```

统一改为：

```kotlin
// LibraryViewModel.render
val keywords = parseSearchKeywords(query)
val filtered = data.comics.filter { matchesAllKeywords(it.key.displayName, keywords) }

// VideoLibraryViewModel.render
val keywords = parseSearchKeywords(query)
val filtered = data.videos.filter { matchesAllKeywords(it.key.fileName, keywords) }
```

- `NoResult(query = ...)` 继续传 `query.trim()` 原文，提示语保持「没有找到匹配「胡桃 芙宁娜」的漫画」。
- 搜索框 placeholder 改为：书架 `搜索漫画名`、视频抽屉 `搜索视频文件名`（不附带括号说明文字）。
- 不做拼音/首字母/模糊纠错（不属于本次范围）。

#### 测试（`core:common` 单测，新增 `SearchKeywordsTest`）

1. `胡桃 芙宁娜` 命中 `xx胡桃xx芙宁娜.mp4`，且命中 `芙宁娜-胡桃.jpg`（顺序无关）；
2. 仅含单个关键词时不命中（AND）；
3. 连续空格 / 制表符 / 全角空格混合分隔解析为同一列表；
4. 首尾空白与全空白输入；
5. 大小写混合（`KaMi` 命中 `kamisato`）；
6. 超过 8 个关键词只取前 8 个。

---

### 11.2 R2：删除视频“第一次失败、第二次成功”的修复

#### 现象

只打开 ComicNest（无其他 App 占用），首次确认删除提示“文件可能被占用”，再次点击删除即成功。

#### 现状链路（`VideoRepositoryImpl.deleteVideo` + `LocalVideoSource`）

```
确认删除
  └─ source.purgeMediaStore(absPath)        // ① 先清 MediaStore
       ├─ resolver.delete(_data = ?)          //   有索引：删索引（MediaProvider 可能打开文件）
       └─ 0 行：MediaScannerConnection.scanFile(absPath)  //   无索引：异步扫描 → 打开文件读头
  └─ File.delete()                          // ② 紧接着删文件 → 撞上 ① 的句柄 → false
  └─ 失败：error(“删除失败，文件可能被占用或目录只读”)，DB 完全不动
```

#### 根因分析（按影响排序）

1. **P0 — 自伤式占用**：绝大多数用户目录下的视频**没有 MediaStore 索引**，因此每次删除都会走降级分支 `MediaScannerConnection.scanFile`。该调用是异步的，媒体扫描器会**打开文件读取头部元数据**，回调未等待；紧随其后的 `File.delete()` 必然撞上这个短暂句柄。第二次点击时上一次扫描早已结束，于是成功——与“永远第二次成功”的现象完全一致。
2. **P1 — 播放器释放滞后**：`ExoPlayer.release()` 只在 `VideoPlayerViewModel.onCleared()` 调用，且释放动作在其内部播放线程异步执行；退出播放页后立即删除该视频，可能仍有 fd 未归还。
3. **P2 — 缩略图/元数据读取**：`MediaMetadataRetriever`（缩略图生成、`readMeta`）在读同一文件时持有 fd（`Semaphore(3)` 允许 3 路并发）。
4. **P3 — 系统侧延迟**：媒体扫描服务、文件索引等可能在数秒内释放；这部分只能靠短重试兜底。

#### 修复设计（D16）

**① 顺序反转：先删文件，成功后才清索引**

```kotlin
override suspend fun deleteVideo(key: VideoKey): Result<Unit> = withContext(Dispatchers.IO) {
    runCatchingIo {
        val entity = videoDao.getById(key.id) ?: error("视频不存在：${key.fileName}")
        // 1) 等待该文件的读取任务（缩略图/元数据）结束，最多 1.5s
        source.awaitFileIdle(entity.absPath, 1_500)
        // 2) 先删文件（带短重试）；失败则不动 MediaStore、不动 DB
        val file = File(entity.absPath)
        if (file.exists() && !deleteWithRetry(file)) {
            error("删除失败，文件仍被系统占用，请稍后重试")
        }
        // 3) 文件已不存在：只删索引，绝不触发扫描
        source.purgeMediaStore(entity.absPath)
        // 4) 清缩略图 + 进度行 + 缓存行（原逻辑不变）
        source.deleteThumbnail(entity.thumbPath)
        progressDao.deleteById(key.id)
        videoDao.deleteById(key.id)
    }
}

private suspend fun deleteWithRetry(file: File): Boolean {
    repeat(DELETE_ATTEMPTS) { attempt ->
        if (runCatching { file.delete() }.getOrDefault(false)) return true
        if (!file.exists()) return true            // 已被系统/MediaStore 删除也算成功
        if (attempt < DELETE_ATTEMPTS - 1) delay(DELETE_RETRY_DELAYS_MS[attempt])
    }
    return false
}
// DELETE_ATTEMPTS = 3；DELETE_RETRY_DELAYS_MS = longArrayOf(50, 150)
```

**② `purgeMediaStore` 只删索引，不再触发扫描**（`LocalVideoSource`）

```kotlin
override suspend fun purgeMediaStore(absPath: String) = withContext(Dispatchers.IO) {
    // 文件此时已被删除；只删索引行，不调用 MediaScannerConnection.scanFile
    // （scanFile 会重新打开文件读头，正是“第一次删不掉”的直接原因）
    runCatching {
        context.contentResolver.delete(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            "${MediaStore.MediaColumns.DATA} = ?",
            arrayOf(absPath),
        )
    }
    Unit
}
```

> 重命名路径的 `purgeMediaStore(oldPath)` 不受影响（文件已改名，不存在“删除前扫描”问题）；但同样不再需要 `scanFile(oldPath)`。

**③ 新增读取任务互斥接口**（`VideoSource`，默认实现为空，避免影响测试 Fake）

```kotlin
/** 等待对该路径的读取任务（缩略图/元数据）全部结束，最多等 [timeoutMs]。 */
suspend fun awaitFileIdle(absPath: String, timeoutMs: Long) = Unit
```

`LocalVideoSource` 用 `ConcurrentHashMap<String, Int>` 记录 in-flight 计数：`generateThumbnail` / `readMeta` 开始时 `+1`、`finally` 时 `-1`；`awaitFileIdle` 每 50ms 轮询直到计数为 0 或超时（超时继续删除流程，不抛异常）。

**④ 播放器立即释放**（`VideoPlayerViewModel` + `VideoPlayerScreen`）

```kotlin
// ViewModel：幂等释放，不依赖 onCleared
private var released = false
fun releasePlayer() {
    if (released) return
    released = true
    runCatching { player.stop(); player.clearMediaItems(); player.release() }
}

// Screen：dismissPlayer 顺序固定
viewModel.onStop()            // 强制落库（release 前必须完成，release 后拿不到 duration）
viewModel.releasePlayer()     // 立即归还 fd
playerView?.apply { player = null; visibility = View.INVISIBLE }
onBack()
```

`onCleared` 中改为 `persistProgressFinal(); releasePlayer()`，重复调用自动跳过。

**⑤ 错误文案**：仍失败时提示「删除失败，文件仍被系统占用，请稍后重试」（去掉“目录只读”这类误导性描述，目录只读会在更早的写入阶段暴露）。

#### 边界与不做项

- 不引入 `MediaStore.createDeleteRequest`（需要系统弹窗，破坏“长按即删”的体验）。
- 不把删除改成“先移入回收站目录再删”（用户明确要求物理删除）。
- 删除失败时**仍然完全不动 DB**（保持 §3.7 的原子性约定）。

#### 测试与验收

| 层级 | 用例 |
|------|------|
| `core:data`（Fake Source） | ① 删除成功时调用顺序为 `awaitFileIdle → delete → purge`；② 删文件失败时不调用 `purge`、不删 DB 行；③ 第一次失败第二次成功（Fake `File` 不可注入，改用可注入的 `deleteWithRetry` 纯函数测重试次数） |
| 真机 | 单个视频连续删除 10 次，**每次一次成功**；播放后立刻返回并删除；缩略图生成中（快速滑列表）立即长按删除 |

---

### 11.3 R3：移除视频页“继续观看”UI

#### 决策（D17）

- **保留**：`video_progress` 表、LRU=3、`SaveVideoProgressUseCase` 的「≥10s 才写、位置夹取」规则、`VideoRepository.getProgress()`（播放页续播仍依赖它）。
- **移除**：视频页顶部“继续观看”整块 UI（标题、横向卡片、剩余时长角标、进度条）。
- **不做**：不提供“显示/隐藏继续观看”的开关（用户要求直接删去）。

#### 代码落点

| 文件 | 改动 |
|------|------|
| `VideoLibraryViewModel.kt` | `VideoLibraryUiState.Ready` 删除 `recent` 字段；`render()` 不再透传 `data.recent` |
| `VideoLibraryScreen.kt` | 删除 `showRecent` / `recentOffset`、`VideoBody` 的 `showRecent` 参数、`VideoGrid` 的 `recent`/`showRecent` 参数、`RecentSection`、`RecentVideoCard`；删除 `LazyRow`、`LinearProgressIndicator`、`formatDuration` 等随之无用的 import |
| `VideoLibraryScreen.kt`（滚动） | `LaunchedEffect(query)` 的复位逻辑不变；`currentGroup` 改为 `gridState.firstVisibleItemIndex / VIDEO_GROUP_SIZE`；分组跳转 `scrollToItem(target)`（去掉 `+offset`） |
| `GetVideoLibraryUseCase` / `VideoRepository.observeRecent()` | **保留**（数据仍需要，且后续可复用于“最近观看”二级页） |
| `VideoLibraryViewModelTest.kt` | 删除/改写与 `recent` 相关的断言 |

#### 影响与验收

- 进入视频页：首屏第一行即视频卡片（无“继续观看”标题与横滑卡片）；
- 分组跳转/当前组高亮仍准确（`50` 个一组）；
- 搜索为空/非空切换时滚动复位行为不变；
- 观看 3 个视频后返回列表：**列表无任何“继续观看”痕迹**，但直接点开这 3 个视频仍从上次位置续播（`getProgress` 生效）。

### 11.4 R4：播放器底部控制层三层重构

#### 目标布局（D18）

```
横屏
┌──────────────────────────────────────────────────────────────┐
│ ←  文件名.mp4（可点：弹全名 + 复制）                            │
│                                                              │
│                        [ 视频画面 ]                           │
│                                                              │
│  8:21/23:05                                  ← ① 时间行（仅左）│
│  ▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁●▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁     ← ② 细进度条│
│  [⏸] [⏭]                              [1.0x] [⟳]  ← ③ 按钮行    │
└──────────────────────────────────────────────────────────────┘
竖屏：同结构，底栏整体上移出导航条安全区，左右按钮仍贴边。
```

#### ① 时间行

- 文案：`"${formatDuration(positionMs)}/${formatDuration(durationMs)}"`（**无空格**，如 `8:21/23:05`；时长未知时 `--:--/--:--`）。
- 样式：13sp、`Color.White.copy(alpha = 0.9f)`、Normal 字重；开启等宽数字（`fontFeatureSettings = "tnum"`）避免秒数跳动时宽度抖。
- 位置：仅左侧，与下方左侧图标视觉左缘对齐（`padding(start = 4.dp)`）；行高 18dp；右侧留空。
- 拖动进度条时**实时显示草稿位置**（如 `8:35/23:05`），抬手后才真正 seek。

#### ② 进度条（`PlayerProgressBar`）

自定义 `Canvas + pointerInput`，**不使用 Material3 `Slider`**（其 4dp 轨道与 20dp thumb 环无法压到目标规格）。

| 项目 | 常规态 | 拖动态 |
|------|--------|--------|
| 轨道高度 | 3dp（圆角 2dp） | 4.5dp |
| 已播颜色 | `Color.White` | `Color.White` |
| 未播颜色 | `Color.White.copy(alpha = 0.28f)` | 同左 |
| 光标 | 实心白圆直径 **10dp**，无外环/无阴影 | 14dp |
| 边界 | 进度为 0 或时长未知时不画光标 | 同左 |

- 触控：整条可点区域高 **28dp**（`Modifier.height(28.dp)`，Canvas 垂直居中绘制）；点击任意位置直接 seek；横向拖动用 `detectHorizontalDragGestures`，内部维护草稿值，`onDragEnd` 才回调 `onSeekTo`（沿用现有“草稿”策略，避免高频 seek）。
- 事件消费：进度条必须 consume 横向拖动手势，避免同一次拖动又触发全屏“左右滑快退/快进”。
- 无障碍：`semantics { contentDescription = "播放进度"; progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f); setProgress { ... } }`。
- RTL：本项目无 RTL 需求，固定 `LayoutDirection.Ltr` 绘制。

#### ③ 按钮行

| 区域 | 控件 | 规格与行为 |
|------|------|-----------|
| 左 | 播放/暂停 | `IconButton` 48dp，图标 26dp；播放中 `Icons.Filled.Pause`、暂停 `PlayArrow`、播放结束 `Replay`（点击从头播） |
| 左 | 下一个视频 | `IconButton` 48dp，`Icons.Filled.SkipNext` 26dp；有下一个 → 纯白可用；无下一个 → `White.copy(alpha = 0.35f)` 且 `enabled = false`（无涟漪、点击无效） |
| 右 | 倍速 | `TextButton`，宽约 52dp、高 40dp，14sp；循环 `0.5 → 1.0 → 1.5 → 2.0`，文案 `0.5x / 1.0x / 1.5x / 2.0x` |
| 右 | 旋转屏幕 | `IconButton` 48dp，`Icons.Filled.ScreenRotation` 26dp；仅本次播放临时覆盖，不写偏好 |

- 布局：`Row(fillMaxWidth, SpaceBetween)`，内部左、右各一个 `Row`；组内间距 0（靠 48dp 命中区自然撑开），最右图标右侧额外 4dp。
- **删除**：`-15s` / `+15s` 两个 `TextButton`、常量 `SEEK_STEP_MS`；`PlayerControls` 的 `onSeekBy` 参数随之移除（`VideoPlayerViewModel.onSeekBy` 保留，供手势 / 未来复用）。
- 容器：底部渐变 `Transparent → Black(0.78)`，`windowInsetsPadding(WindowInsets.systemBars)`，`padding(horizontal = 12.dp, vertical = 8.dp)`；层间距：时间→进度条 2dp，进度条→按钮 0dp。
- 自动隐藏：保持 3s（播放中），任一按钮交互后重置计时；暂停时不自动隐藏（沿用 §9.5）。

#### “下一个视频”的数据与行为（D19）

**顺序定义**：以“视频页当前搜索词 + 当前排序”得到的列表为准，当前视频在列表中的下一项。

1. **路由传参**：`video_player/{videoKeyId}?query={query}`；列表页跳转时把当前搜索框文本 `Uri.encode` 传入（可空，缺省 `""`）；随机按钮进入时传空（全库）。
2. **播放页计算**：`VideoPlayerViewModel` 新增注入 `GetVideoLibraryUseCase`，并读取 `SavedStateHandle["query"]`：

```kotlin
private val playQueue: StateFlow<List<VideoSummary>> = combine(
    getVideoLibrary(),
    flowOf(queryParam),
) { data, q ->
    val keywords = parseSearchKeywords(q)
    data.videos.filter { matchesAllKeywords(it.key.fileName, keywords) }
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

val nextVideo: StateFlow<VideoSummary?> = combine(playQueue, currentKey) { list, cur ->
    val i = list.indexOfFirst { it.key == cur }
    if (i in 0 until list.lastIndex) list[i + 1] else null
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
```

3. **当前 key 改为可变**：`private val key` → `MutableStateFlow<VideoKey?>`；抽取 `loadVideo(key)`：`getVideo → getProgress → setMediaItem → prepare → seekTo(start) → play`，并重置 `ended = false`、`boostSpeed = null`、显式 `setPlaybackSpeed(speed)`。
4. **点击行为**：`onPlayNext()` → 先 `persistProgress(force = true)` 落库当前视频 → `loadVideo(next.key)`（页内切换，不重建页面、不重新导航）。
5. **失败跳过**：下一个文件已被外部删除时（`getVideo` 失败）自动尝试再下一个，最多连续跳过 3 个，全部失败提示「下一个视频不可用」。
6. **边界**：当前视频不在列表内（如随机进入且搜索词非空）时 `indexOfFirst = -1` → 按钮置灰（不猜测语义）。播放结束态下按钮仍可用。

---

### 11.5 R5：设置页“默认阅读方式”去掉英文

- `SettingsScreen.modeLabel()`：
  - `ReaderMode.WEBTOON`：`下拉式（Webtoon）` → **`下拉式`**；
  - `ReaderMode.PAGER`：`左右翻页（Pager）` → **`左右翻页`**。
- 标题“漫画阅读方式”“默认阅读方式”不变；`RadioButton` 选中态与点击整行行为不变。
- 全仓检查：`Webtoon` / `Pager` 仅剩代码注释与类名（`WebtoonViewer` / `PagerViewer`），阅读器 UI 无其它英文文案，无需改动。

---

### 11.6 R6：设置页新增“视频播放方式 → 默认视频朝向”

#### 偏好与模型（D21）

```kotlin
// :core:model
enum class VideoOrientationMode { PORTRAIT, LANDSCAPE, AUTO }

// :core:domain PreferencesRepository
fun observeVideoOrientationMode(): Flow<VideoOrientationMode>
suspend fun setVideoOrientationMode(mode: VideoOrientationMode)
```

- DataStore key：`video_orientation`（int，存 ordinal）；**缺省/越界 → `PORTRAIT`**（用户口径：“选择竖屏则和当前情况相同”）。

#### 设置页 UI

位置：`视频库目录` 块之后、`关于` 之前，新增：

```
───────────────────────────────────────────
视频播放方式
 • 默认视频朝向                      竖屏
───────────────────────────────────────────
关于
```

- 点击行弹 `AlertDialog`：标题「默认视频朝向」，三个 `RadioButton`：**竖屏 / 横屏 / 根据视频比例**（顺序同行内枚举），默认选中竖屏；底部「关闭」。选择即写入偏好并关闭（与“默认阅读方式”交互一致）。副文案：竖屏「进入播放页保持竖屏」、横屏「进入播放页立即横屏」、根据比例「横向视频横屏，竖向视频竖屏」（不放括号补充说明）。
- `SettingsUiState` 新增 `videoOrientation: VideoOrientationMode = PORTRAIT`；`combine` 增加 `observeVideoOrientationMode()`；新增 `onVideoOrientationChange(mode)`。

#### 播放页应用规则

新增 `VideoRepository.readDimensions(key): Result<VideoDimensions>`（`:core:model` 定义 `data class VideoDimensions(width: Int, height: Int, rotationDegrees: Int)`，由 `VideoRepositoryImpl` 调 `source.readMeta` 映射，domain 层不依赖 `:source:video`）。

纯函数（可单测）：

```kotlin
internal fun orientationRequest(mode: VideoOrientationMode, dims: VideoDimensions?): Int = when (mode) {
    VideoOrientationMode.PORTRAIT -> SCREEN_ORIENTATION_SENSOR_PORTRAIT
    VideoOrientationMode.LANDSCAPE -> SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    VideoOrientationMode.AUTO -> {
        val (w, h) = dims?.let {
            if (it.rotationDegrees % 180 == 90) it.height to it.width else it.width to it.height
        } ?: (0 to 0)
        if (w > 0 && h > 0 && w >= h) SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }
}
```

| 偏好 | 视频宽高（含旋转矫正） | 生效方向 |
|------|------------------------|----------|
| 竖屏 | 忽略 | `SENSOR_PORTRAIT` |
| 横屏 | 忽略 | `SENSOR_LANDSCAPE` |
| 根据视频比例 | 宽 ≥ 高 | `SENSOR_LANDSCAPE` |
| 根据视频比例 | 宽 < 高 | `SENSOR_PORTRAIT` |
| 根据视频比例 | 读取失败 / 宽高为 0 | `SENSOR_PORTRAIT`（兜底） |

**时序（关键）**：进入播放页 → VM 读取偏好与 `VideoDimensions`（仅 `AUTO` 模式才调用 `readDimensions`，其余模式跳过 IO）→ 确定方向 → **再** `setMediaItem/prepare/play`。方向切换发生在 `Loading` 阶段，避免“先竖屏出画面再旋转”的跳变。

**优先级**：
1. 方向请求以「视频 key」为粒度下发：`VideoOrientationRequest(keyId, orientation)`，每个视频首次装载时应用一次；
2. 同一视频内的手动旋转不会被覆盖——屏幕用 `rememberSaveable(orientedKeyId)` 记住已应用的 key，config change 后不会重复应用；
3. 切“下一个视频”时 key 变化，按新视频重新判定方向（横长视频 → 横屏、竖长视频 → 竖屏；固定模式则为偏好方向）；
4. 退出播放页：`onDispose` 恢复 `SCREEN_ORIENTATION_UNSPECIFIED`，不改系统“自动旋转”开关。

#### 测试

- 偏好：缺省/越界 → `PORTRAIT`；写入后 `observe` 立即返回新值；
- `orientationRequest()`：`1920×1080/0° → 横`；`1080×1920/0° → 竖`；`1920×1080/90° → 竖`（手机竖拍）；`1920×1080/270° → 竖`；`dims = null → 竖`；
- 手动旋转后同一视频内不被覆盖；切换“下一个视频”按新视频重新应用（原实现用一次性布尔哨兵，切换视频时被跳过，已修正）；
- 设置项切换后，**新进入**播放页生效（已在播放页内的当前视频不强行改变）。

---

### 11.7 涉及文件清单（R1~R6）

| 模块 | 文件 | 变更 |
|------|------|------|
| `:core:common` | `util/SearchKeywords.kt`（新）+ `SearchKeywordsTest.kt`（新） | 分词与 AND 匹配 |
| `:core:model` | `VideoOrientationMode.kt`（新）、`VideoDimensions.kt`（新） | 偏好枚举与视频宽高 |
| `:core:domain` | `PreferencesRepository.kt` | +`observeVideoOrientationMode` / `setVideoOrientationMode` |
| `:core:domain` | `VideoRepository.kt` | +`readDimensions(key)` |
| `:core:data` | `prefs/PreferencesRepositoryImpl.kt` | 新增 `video_orientation` key 与默认值 |
| `:core:data` | `repository/VideoRepositoryImpl.kt` | 删除顺序反转 + 重试 + `readDimensions` 映射 |
| `:source:video` | `VideoSource.kt` | +`awaitFileIdle(absPath, timeoutMs)`（默认空实现） |
| `:source:video` | `LocalVideoSource.kt` | in-flight 计数；`purgeMediaStore` 不再 `scanFile` |
| `:feature:library` | `LibraryViewModel.kt`、`LibraryScreen.kt` | 多关键词过滤；placeholder 文案 |
| `:feature:video` | `VideoLibraryViewModel.kt` | 多关键词过滤；`Ready` 去 `recent`；删除失败文案 |
| `:feature:video` | `VideoLibraryScreen.kt` | 删除“继续观看”一切 UI/偏移；跳转带 `query` |
| `:feature:video` | `VideoPlayerViewModel.kt` | `nextVideo`、`onPlayNext`、`currentKey` 可变、`releasePlayer()`、朝向应用 |
| `:feature:video` | `VideoPlayerScreen.kt` | `releasePlayer`、按偏好设置方向、控制层新参数 |
| `:feature:video` | `components/PlayerControls.kt` | 三层重构 + 自定义 `PlayerProgressBar` |
| `:feature:video` | `components/VideoDrawer.kt` | 搜索 placeholder |
| `:feature:settings` | `SettingsScreen.kt` / `SettingsViewModel.kt` | 去英文；新增“视频播放方式”分区与状态 |
| `:app` | `navigation/ComicNestNavHost.kt` | 播放路由新增可空 `query` 参数 |
| 文档 | `README.md`、`docs/DESIGN_v2.md` | 功能说明与本节 |

### 11.8 测试与验收（本轮）

| 类型 | 内容 |
|------|------|
| 单测 | `SearchKeywordsTest`（6 例）；`OrientationRequestTest`（6 例）；`VideoLibraryViewModelTest` 补多关键词过滤、删 `recent` 断言；`VideoUseCaseRulesTest` 若无影响保持 |
| 构建 | `:core:common:testDebugUnitTest`、`:core:data:testDebugUnitTest`、`:feature:video:testDebugUnitTest`、`:feature:settings:compileDebugKotlin`、`:app:assembleDebug/Release` |
| 手工（R1） | 书架输入 `胡桃 芙宁娜` → 命中 `xx胡桃xx芙宁娜`；视频抽屉同；全角空格/多空格同样生效 |
| 手工（R2） | ① 普通删除一次成功；② 刚看完返回后立刻删除一次成功；③ 缩略图正在生成时删除一次成功 |
| 手工（R3） | 视频页无“继续观看”；连看 3 个后仍无；点开依旧续播 |
| 手工（R4） | 横/竖屏底栏三层位置正确；末个视频“下一个”置灰；倍速循环与旋转按钮可用；±15s 已消失 |
| 手工（R6） | 三选项分别生效；AUTO 下横长/竖长视频分别进入对应方向；手动旋转后不被自动覆盖；退出后方向不残留 |

### 11.9 风险与边界

- 删除重试最坏增加 ~200ms（在 IO 线程，不阻塞 UI，删除按钮无 loading 态）；
- `readDimensions` 增加一次文件打开，仅 `AUTO` 模式使用，且发生在 Loading 阶段；
- 自定义进度条需自行维护无障碍与命中区，已在上文约束；
- 移除“继续观看”后首屏列表位置上移，分组跳转/恢复滚动位置需回归；
- 本轮**不做**：搜索高亮/搜索历史、“最近观看”二级页、进度条缩略图预览、画中画/后台播放、播放列表管理。
- 版本：`2.2.0` / `versionCode 6`（D22）。

---

*（DESIGN v2 完。实施时若与 `docs/demand_v2.txt` 或本文 §0 的 V1~V14 冲突，以 §0 为准；与 v1 `DESIGN.md` 冲突时，漫画侧以 v1 为准，视频侧以本文为准；v2.2 迭代以 §11 为准。）*
