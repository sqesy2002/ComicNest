# ComicNest 系统设计文档（DESIGN）

> 版本：v0.3（§10 增量需求：首页搜索框 + 大列表分组跳转均**收敛至左侧抽屉**、添加目录入口收敛、阅读器收藏入口；v0.2 曾把搜索/分组置于顶栏，v0.3 因顶栏拥挤改为抽屉承载）
> 上游依据：`docs/PRD.md`（需求与已确认决策 D1~D14）、`docs/demand.txt`
> 目标读者：开发实施者。本文档应做到"决策完备"，实施者无需再做架构级决策。
> 图标素材：`docs/assets/icon/ic/ComicNest.jpg`（512×512 JPG，已确认可用，处理见 §6.4）

---

## 1. 技术选型与总体方案

### 1.1 选型结论（对 PRD 的细化与一处工程化修正）

PRD 结论为"以 Mihon 为基线裁剪改造"。DESIGN 在保持其精神（复用 Mihon 已验证组件、Apache-2.0 合规、保留联网扩展点）的前提下，做一处**工程化修正**：

- **不整体 fork-build Mihon 巨型工程**（其 AGP 9.4.1 / Kotlin 2.4.20 / compileSdk 37 工具链过新、模块过多、含大量本期非目标代码，构建与裁剪风险高）。
- 改为：**新建一个受控的多模块 Gradle 工程 `ComicNest`**，采用**稳定工具链**，并**移植（port）** Mihon 中经核对的关键组件（自然序比较、`ImageUtil` 格式识别、Webtoon/Pager 阅读器思路、Coil 配置与 Material3 主题思路），移植时保留 Apache-2.0 版权与 NOTICE 归属。
- 收益：构建可靠（普通机器可出 APK）、体积小、边界清晰、可扩展（保留 `source:api` 抽象）、许可证合规。

| 维度 | 选择 | 说明 |
|------|------|------|
| 语言 | Kotlin | 全 Kotlin |
| UI | Jetpack Compose + Material 3 | 现代美观；中文文案 |
| 最低/目标 SDK | `minSdk=26`（Android 8.0）/ `compileSdk=targetSdk=35` | D9；35 为稳定版 |
| 构建工具链 | Gradle 8.11+、AGP 8.7.3、Kotlin 2.0.21、JDK 17 | 稳定、普及率高，降低出包风险 |
| Compose BOM | 2024.12.01 | 稳定 |
| 图片加载 | Coil 2.7.0（+ `coil-gif`） | 支持 jpg/png/webp/bmp；**gif 动图**经 `ImageDecoderDecoder`/`GifDecoder`；内存/磁盘缓存 |
| 持久化 | Room 2.6.1（关系/收藏/进度/库根）+ DataStore 1.1（偏好） | 可靠、可迁移 |
| DI | Hilt 2.51.1（KSP） | 标准、可扩展、易测试 |
| 导航 | androidx.navigation.compose 2.8.x | 单 Activity + Compose 导航 |
| 列表 | `LazyVerticalGrid`（自带无限滚动） | D4，无需 Paging 库 |
| 文件访问 | `java.io.File` 绝对路径 + `MANAGE_EXTERNAL_STORAGE` | D2；不使用 SAF/UniFile（简化、避免授权持久化复杂度） |
| 异步 | kotlinx-coroutines | IO 密集扫描/解码在 `Dispatchers.IO` |
| 签名 | 开发侧生成正式 keystore（`keystore/`，`.gitignore` 排除） | D13 |
| 许可证 | Apache-2.0 组件移植，保留 NOTICE | 合规 |

### 1.2 总体架构

分层 + 多模块。依赖方向严格单向：**feature → domain → (data / source)**；**core** 为公共底座；**app** 仅做组装。

```
┌────────────────────────────────────────────────────────────┐
│ :app  （Application / MainActivity / 导航图 / DI 组装 / 签名）│
├────────────────────────────────────────────────────────────┤
│ :feature:library   :feature:reader   :feature:collection   │
│ :feature:settings  （UI + ViewModel，Compose）              │
├────────────────────────────────────────────────────────────┤
│ :core:domain  （Repository 接口 + UseCase 用例，纯 Kotlin）  │
├───────────────────────┬────────────────────────────────────┤
│ :core:data            │ :source:api  →  :source:local      │
│ （Room/DataStore 实现）│ （Source 抽象）   （本地目录扫描）    │
├────────────────────────────────────────────────────────────┤
│ :core:model（纯数据模型）  :core:common（工具）  :core:ui（主题/组件/图片）│
└────────────────────────────────────────────────────────────┘
```

**关键设计原则**

1. **Source 抽象（扩展性，PRD-AC13）**：所有"漫画从哪来"都经 `ComicSource` 接口。本期唯一实现 `LocalComicSource`；未来联网检索只需新增一个 `RemoteComicSource` 实现并注册，UI/用例层零改动。
2. **只读语义**：扫描/阅读全程只读用户文件；App 仅向私有目录写缩略图缓存与 DB。
3. **状态即数据（可靠性）**：每个 feature 用 `StateFlow<UiState>` 暴露密封状态（Idle/Loading/Ready/Error），UI 只渲染状态，杜绝空指针与竞态崩溃。
4. **边界防御**：所有文件 IO 与解码均包裹 `runCatching`，异常降级为占位/提示，绝不崩溃（D11）。
5. **唯一键**：`ComicKey = rootId + "::" + comicName`（内部，D10）；展示名仅 `comicName`。

### 1.3 关键数据流

- **扫描流**：`LibraryRoot(绝对路径)` → `LocalComicSource.scanLibrary()` → `List<ComicSummary>`（含封面=按名最小图）→ `LibraryRepository` 缓存入库 → `feature:library` 以 `Flow` 观察 → `LazyVerticalGrid` 无限滚动渲染。
- **阅读流**：`ComicKey` → `LocalComicSource.getPages()`（自然序）→ `ReaderViewModel` → `WebtoonViewer`/`PagerViewer` 渲染；翻页回调 → `SaveProgressUseCase`（LRU=5）。
- **收藏流**：`CollectionRepository` 多对多表；库刷新后 `CleanupInvalidCollectionsUseCase` 校验指向有效性并自动清理（D7）。

---

## 2. 模块划分与功能边界

| 模块 | 职责（做什么） | 边界（不做什么） | 依赖 |
|------|----------------|------------------|------|
| `:app` | Application 初始化、Hilt 入口、单 Activity、全局导航图、签名配置、图标/资源装配 | 不含业务逻辑 | 全部 feature + core |
| `:core:model` | 纯数据类/枚举（Comic、Collection、Progress、Sort、Mode…） | 无逻辑、无 Android 依赖 | 无 |
| `:core:common` | 自然序比较、扩展名/格式判断、协程调度器封装、`Result` 封装、日志 | 不依赖 model/domain | 无 |
| `:core:domain` | Repository **接口** + UseCase **用例**（编排业务规则：LRU 进度、随机、失效清理） | 不实现 IO、不依赖 Android UI | model, common |
| `:core:data` | Room 实体/DAO/DB、DataStore 偏好、Repository **实现** | 不做业务编排（编排在 domain UseCase） | domain, model, common |
| `:core:ui` | Material3 主题、通用 Compose 组件（封面卡、错误占位、加载态）、Coil 图片加载封装 | 不含具体业务页面 | model, common |
| `:source:api` | `ComicSource` 抽象接口 + 分页/模型契约 | 不实现任何具体来源 | model, common |
| `:source:local` | 本地目录扫描、封面选取、格式识别、页列表（移植 Mihon 思路） | 不持久化、不做 UI | source:api, model, common |
| `:feature:library` | 主页总览：库根管理、扫描触发、网格+排序+无限滚动、随机入口 | 不实现阅读器/收藏 UI | domain, ui, model |
| `:feature:reader` | 阅读器：Webtoon/Pager 双模式、进度读写、页加载与错误占位 | 不做库管理 | domain, ui, model |
| `:feature:collection` | 收藏夹列表/详情/CRUD、加入/移除漫画、失效提示 | 不做扫描 | domain, ui, model |
| `:feature:settings` | 库根增删、阅读偏好、关于/版本 | 不做核心业务 | domain, ui, model |

**模块交互方式（统一约定）**

- feature 的 ViewModel 通过 **构造函数注入 UseCase**（Hilt），调用 UseCase 得到 `Flow`/`suspend` 结果，映射为 `UiState` 暴露给 Compose。
- UseCase 只依赖 **Repository 接口**（`:core:domain`），由 Hilt 绑定 `:core:data` 的实现。
- `:source:local` 通过 `ComicSource` 接口被 `:core:data` 的 `LibraryRepository` 实现调用（`LibraryRepository` 聚合"扫描结果 + DB 缓存"）。
- 跨 feature 跳转（如 library→reader、collection→reader）通过 **导航路由字符串**（`:app` 定义），feature 间不直接依赖。

---

## 3. 各模块详细设计

> 约定：包根 `com.comicnest`。所有 `suspend` IO 在 `Dispatchers.IO`；所有返回外部数据的函数以 `Result<T>` 或密封状态返回，不抛裸异常。

### 3.1 `:core:model`（纯数据模型）

| 类型 | 字段 | 说明 |
|------|------|------|
| `ComicKey` | `rootId: String`, `comicName: String` | 值对象；`val id = "$rootId::$comicName"`（D10 内部唯一键）；`displayName = comicName` |
| `LibraryRoot` | `id: String`(=路径哈希), `path: String`(绝对), `displayName: String`, `addedAt: Long` | 一个导入的库根目录 |
| `ComicSummary` | `key: ComicKey`, `coverPath: String?`, `imageCount: Int`, `importedAt: Long`, `lastModified: Long` | 主页网格项；封面=按名最小图（D3） |
| `ComicPageRef` | `comicKey: ComicKey`, `index: Int`, `fileName: String`, `absPath: String`, `isAnimated: Boolean` | 单页引用；`isAnimated` 用于 gif 动图解码 |
| `Collection` | `id: Long`, `name: String`, `createdAt: Long` | 收藏夹 |
| `CollectionEntry` | `collectionId: Long`, `comicKeyId: String` | 多对多关联（D7） |
| `ReadProgress` | `comicKeyId: String`, `pageIndex: Int`, `totalPages: Int`, `updatedAt: Long` | LRU=5（D6） |
| `SortMode` | `NAME`, `IMPORT_TIME` | 主页排序（D：名称默认/导入时间） |
| `ReaderMode` | `WEBTOON`, `PAGER` | 阅读模式（默认 WEBTOON） |

### 3.2 `:core:common`（工具）

| 函数/类 | 签名 | 功能 |
|---------|------|------|
| `naturalOrderCompare` | `fun naturalOrderCompare(a: String, b: String): Int` | 移植 Mihon `compareToCaseInsensitiveNaturalOrder`：数字段按数值比较，保证 `001`<`002`、`2`<`10`（AC4） |
| `isSupportedImage` | `fun isSupportedImage(name: String): Boolean` | 扩展名白名单：jpg/jpeg/png/webp/gif/bmp（+可选 avif/heif） |
| `isAnimatedExt` | `fun isAnimatedExt(name: String): Boolean` | gif（及 animated webp 判定入口） |
| `IoDispatcher` | `@Qualifier`/提供 `CoroutineDispatcher` | 统一 IO 调度，便于测试替换 |
| `runCatchingIo` | `inline fun <T> runCatchingIo(block: () -> T): Result<T>` | 包裹 IO，捕获 `Throwable`→`Result.failure`，记日志 |
| `AppLog` | `object AppLog { fun d/i/e(tag, msg, t?) }` | 统一日志（release 可关） |

### 3.3 `:core:domain`（接口 + 用例）

**Repository 接口（由 `:core:data` 实现）**

| 接口 | 方法（参数 → 返回） | 承担功能 |
|------|---------------------|----------|
| `LibraryRepository` | `observeRoots(): Flow<List<LibraryRoot>>` | 观察已导入库根 |
| | `addRoot(path: String): Result<LibraryRoot>` | 校验目录存在/可读后新增库根 |
| | `removeRoot(rootId: String): Result<Unit>` | 移除库根（连带失效其漫画缓存） |
| | `observeComics(sort: SortMode): Flow<List<ComicSummary>>` | 观察漫画总览（按排序） |
| | `refresh(): Result<Int>` | 触发全量重扫，返回漫画数；写缓存 |
| | `getComic(key: ComicKey): Result<ComicSummary>` | 单部详情 |
| | `getPages(key: ComicKey): Result<List<ComicPageRef>>` | 该漫画自然序页列表 |
| `CollectionRepository` | `observeCollections(): Flow<List<Collection>>` | 收藏夹列表 |
| | `createCollection(name: String): Result<Collection>` | 新建（重名校验） |
| | `renameCollection(id: Long, name: String): Result<Unit>` | 重命名 |
| | `deleteCollection(id: Long): Result<Unit>` | 删除（连带关联） |
| | `observeEntries(collectionId: Long): Flow<List<ComicSummary>>` | 收藏夹内漫画 |
| | `addComic(collectionId: Long, key: ComicKey): Result<Unit>` | 加入（幂等） |
| | `removeComic(collectionId: Long, key: ComicKey): Result<Unit>` | 移除 |
| | `collectionsOf(key: ComicKey): Flow<List<Collection>>` | 某漫画所属收藏夹（多对多） |
| | `cleanupInvalid(): Result<Int>` | 校验指向有效性，删除失效 entry，返回清理数（D7） |
| `ProgressRepository` | `getProgress(key: ComicKey): ReadProgress?` | 取进度（无则 null→从第一页） |
| | `saveProgress(key: ComicKey, pageIndex: Int, total: Int): Result<Unit>` | 保存并执行 **LRU=5 淘汰**（D6） |
| | `observeRecent(): Flow<List<ReadProgress>>` | 最近阅读（≤5） |
| `PreferencesRepository` | `observeSortMode(): Flow<SortMode>` / `setSortMode(m)` | 主页排序偏好 |
| | `observeReaderMode(): Flow<ReaderMode>` / `setReaderMode(m)` | 阅读模式偏好 |

**UseCase（业务编排，纯 Kotlin，可单测）**

| UseCase | 依赖 | `operator fun invoke(...)` 参数 → 返回 | 规则 |
|---------|------|----------------------------------------|------|
| `ScanLibraryUseCase` | LibraryRepository | `(): Result<Int>` | 调 `refresh()`；完成后触发 `CleanupInvalidCollectionsUseCase` |
| `GetLibraryUseCase` | LibraryRepository, PreferencesRepository | `(): Flow<LibraryUiData>` | 合并 comics + sortMode |
| `AddLibraryRootUseCase` | LibraryRepository | `(path: String): Result<LibraryRoot>` | 校验绝对路径存在/是目录/可读 |
| `RandomComicUseCase` | LibraryRepository | `(): Result<ComicKey>` | 在**全部已导入漫画**中均匀随机（D5）；空库返回 failure |
| `OpenComicUseCase` | LibraryRepository, ProgressRepository | `(key): Result<OpenComicData(pages, startIndex)>` | 取自然序页 + 进度起点（命中 LRU 则续读，否则 0） |
| `SaveProgressUseCase` | ProgressRepository | `(key, pageIndex, total): Result<Unit>` | 委托 repository 的 LRU 保存 |
| `ManageCollectionUseCase` | CollectionRepository | 聚合 create/rename/delete/add/remove | 统一入口，便于 UI 调用 |
| `CleanupInvalidCollectionsUseCase` | CollectionRepository, LibraryRepository | `(): Result<Int>` | 以当前有效 ComicKey 集合校验 entry，删除失效（D7） |

### 3.4 `:source:api` 与 `:source:local`

**`ComicSource` 抽象（扩展点，AC13）**

| 成员 | 签名 | 功能 |
|------|------|------|
| `id` | `val id: String` | 来源唯一标识（local="local"） |
| `label` | `val label: String` | 展示名（"本地目录"） |
| `scanLibrary` | `suspend fun scanLibrary(root: LibraryRoot): Result<List<ComicSummary>>` | 扫描一个库根，产出漫画列表（含封面） |
| `getPages` | `suspend fun getPages(key: ComicKey, root: LibraryRoot): Result<List<ComicPageRef>>` | 产出自然序页列表 |
| `search`（default 未实现） | `suspend fun search(query: String, page: Int): Result<List<ComicSummary>>` | **为未来联网预留**；本地实现抛 `UnsupportedOperationException` 或返回空 |

**`LocalComicSource`（实现）内部类/函数**

| 类/函数 | 参数 → 返回 | 功能/规则 |
|---------|-------------|-----------|
| `scanLibrary(root)` | 见上 | 列 `root.path` 下**一级子目录**（跳过隐藏/非目录）作为漫画；对每个漫画目录取封面与图片数；并发（`async` 限并发）映射；按 D1 两级模型 |
| `pickCover(dir)` | `(File): String?` | 目录内 `isSupportedImage` 文件中**按 `naturalOrderCompare` 最小**者（D3）；无则 null |
| `listPages(dir)` | `(File): List<ComicPageRef>` | 目录内受支持图片按自然序排序，index 递增；标记 `isAnimated` |
| `FormatGuard` | `(File): Boolean` | 目录为空/无有效图→该漫画被跳过（D11） |

> 移植来源：自然序与格式识别移植自 Mihon `core/common`（`compareToCaseInsensitiveNaturalOrder`、`ImageUtil` 思路），保留 NOTICE 归属；层级模型按 D1 改为两级、封面按 D3 改为"最小名"、访问按 D2 改为绝对路径 `File`。

### 3.5 `:core:data`（持久化与 Repository 实现）

**Room 实体/表**

| 表 | 主键 | 字段 | 索引/约束 |
|----|------|------|-----------|
| `library_root` | `id` | path, displayName, addedAt | path UNIQUE |
| `comic_cache` | `comicKeyId` | rootId, comicName, coverPath, imageCount, importedAt, lastModified | rootId 索引；refresh 时整表重建 |
| `collection` | `id`(auto) | name, createdAt | name UNIQUE |
| `collection_entry` | (collectionId, comicKeyId) | — | 外键级联删除 collection；comicKeyId 索引 |
| `read_progress` | `comicKeyId` | pageIndex, totalPages, updatedAt | 保存时保证行数≤5（LRU） |

**DAO 关键方法**

| DAO | 方法 | 说明 |
|-----|------|------|
| `RootDao` | `observeAll(): Flow<List<RootEntity>>`；`insert`；`deleteById` | 库根 CRUD |
| `ComicDao` | `observeAll(sortColumn): Flow<...>`；`replaceAll(list)`（事务）；`getById` | 漫画缓存；`replaceAll` 保证扫描原子性 |
| `CollectionDao` | 收藏夹与 entry 的多对多查询/插入/删除 | `@Transaction` 保证一致性 |
| `ProgressDao` | `upsert(p)`；`evictOldest(keep=5)`；`observeRecent(limit=5)` | **LRU 淘汰在 upsert 同事务内执行** |

**Repository 实现要点**

- `LibraryRepositoryImpl`：聚合 `ComicSource`（注入 `LocalComicSource`）+ `ComicDao`。`refresh()` = 对每个 root 调 `source.scanLibrary` → 合并 → `comicDao.replaceAll` → 返回总数；全程 `runCatchingIo`。
- `ProgressRepositoryImpl.saveProgress`：`upsert` + `evictOldest(5)` 同事务（D6）。
- `CollectionRepositoryImpl.cleanupInvalid`：读当前 `comic_cache` 的 key 集合 → 删除不在集合内的 entry → 返回清理数（D7）。
- `PreferencesRepositoryImpl`：DataStore `preferencesDataStore`，key：`sort_mode`、`reader_mode`。

### 3.6 `:core:ui`（主题/组件/图片）

| 组件/函数 | 参数 | 功能 |
|-----------|------|------|
| `ComicNestTheme` | `darkTheme: Boolean` | Material3 主题（中文字体回退）；可选动态取色 |
| `ComicCoverCard` | `summary: ComicSummary, onClick` | 封面图（Coil）+ 名称；封面 null 时显示占位图 |
| `ComicImage` | `path: String, isAnimated: Boolean, modifier` | Coil 加载；`isAnimated` 时用动图解码器（gif 播放，D8）；失败显示错误占位（D11） |
| `LoadingState` / `ErrorState` | `msg, onRetry?` | 通用加载/错误占位 |
| `ImageLoaderFactory`（app 提供） | — | Coil 配置：内存/磁盘缓存、`coil-gif` 解码器、IO 线程池 |

### 3.7 各 feature 详细设计

**`:feature:library`（主页总览）**

- `LibraryViewModel`：注入 `GetLibraryUseCase`、`ScanLibraryUseCase`、`AddLibraryRootUseCase`、`RandomComicUseCase`、`PreferencesRepository`。
  - `UiState` 密封类：`Loading` / `Ready(comics, sortMode, roots)` / `Empty` / `Error(msg)`。
  - 事件：`onRefresh()`、`onSortChange(mode)`、`onAddRoot(path)`、`onRandom()`（成功→导航 reader）、`onOpenComic(key)`。
- `LibraryScreen`：`LazyVerticalGrid`（无限滚动 D4）+ 顶部排序切换 + 随机按钮 + 库根管理入口；首进无库根→引导添加。
- 权限：首次进入请求 `MANAGE_EXTERNAL_STORAGE`（D2）；未授权显示引导。

**`:feature:reader`（阅读器）**

- `ReaderViewModel`：注入 `OpenComicUseCase`、`SaveProgressUseCase`、`PreferencesRepository`。
  - `UiState`：`Loading` / `Ready(pages, startIndex, mode)` / `Error`。
  - 事件：`onPageChanged(index)`（节流后调 SaveProgress）、`onToggleMode()`（WEBTOON↔PAGER，写偏好）。
- `ReaderScreen`：根据 `mode` 组合 `WebtoonViewer` 或 `PagerViewer`；顶部栏显示 名称/页码/模式切换/收藏入口。
- `WebtoonViewer`（Compose `LazyColumn`）：垂直连续滚动，项= `ComicImage`；默认为本模式。
- `PagerViewer`（Compose `HorizontalPager`）：左右翻页；页= `ComicImage`（fit 屏幕）。
- 进度：`onPageChanged` 节流（如 800ms）→ `SaveProgressUseCase`（LRU=5）。

**`:feature:collection`（收藏夹）**

- `CollectionListViewModel` / `CollectionDetailViewModel`：注入 `ManageCollectionUseCase`、`CollectionRepository`。
  - 列表页：收藏夹网格 + 新建对话框；详情页：该夹内漫画网格 + 移除 + 重命名/删除菜单。
  - 失效提示：进入列表时触发 `cleanupInvalid`，若清理数>0 显示 Snackbar 提示（D7）。
- 加入收藏：在 library/reader 的漫画长按菜单→`collectionsOf(key)` 勾选多夹→`addComic`（多对多 D7）。

**`:feature:settings`**

- 库根管理（列表/增/删）、阅读偏好（默认模式）、关于（版本/开源声明）。

**`:app`**

- `ComicNestApp`(Application, `@HiltAndroidApp`)：初始化 Coil `ImageLoaderFactory`。
- `MainActivity`(`@AndroidEntryPoint`)：`setContent { ComicNestTheme { ComicNestNavHost() } }`。
- `ComicNestNavHost`：路由 `library`(start)、`reader/{comicKeyId}`、`collections`、`collection/{id}`、`settings`。
- 签名：`app/build.gradle.kts` `signingConfigs.release` 读 `keystore/`（属性从 `keystore.properties`，该文件 gitignore）。

### 3.8 模块交互时序（关键路径）

1. **首次启动**：MainActivity → 权限检查 → LibraryScreen → 无 root→引导 → 用户输入绝对路径 → `AddLibraryRootUseCase` → `LibraryRepository.addRoot` → 成功后 `ScanLibraryUseCase` → `LocalComicSource.scanLibrary` → `comicDao.replaceAll` → `observeComics` Flow 发射 → 网格渲染。
2. **打开漫画**：网格点击 → 导航 `reader/{key}` → `OpenComicUseCase(key)` → pages+startIndex → ReaderScreen 渲染 → 翻页 → `SaveProgressUseCase`（LRU）。
3. **随机**：library 顶栏 → `RandomComicUseCase` → key → 导航 reader。
4. **收藏**：长按漫画 → 对话框列 `collectionsOf` → 勾选 → `addComic`；库刷新后 `cleanupInvalid` 自动清理失效（D7）。

---

## 4. 各模块测试方案与单模块验收标准

> 测试框架：JUnit5 + kotlinx-coroutines-test + MockK（单测）；Compose UI Test + Espresso（仪器）；Room 用 in-memory DB。

| 模块 | 测试方案 | 单模块验收标准 |
|------|----------|----------------|
| `:core:common` | 单测 `naturalOrderCompare`（001<002、2<10、中文、混合）；`isSupportedImage` 六格式+非法 | 排序用例全绿；六格式判定正确 |
| `:core:domain` | 单测 UseCase（用 fake repository）：RandomComic（空库 failure、均匀性）、OpenComic（有/无进度起点）、CleanupInvalid（删除失效） | LRU/随机/清理规则与 D5/D6/D7 一致 |
| `:core:data` | Room in-memory 单测：ProgressDao **LRU=5 淘汰**、CollectionDao 多对多/级联、ComicDao replaceAll 原子性 | 插入第 6 条进度后仅剩最近 5 条；级联删除正确 |
| `:source:local` | 单测（临时目录 fixture）：两级扫描、封面=最小名、空目录跳过、自然序页列表、隐藏目录跳过 | 扫描/封面/排序与 D1/D3/D11 一致 |
| `:core:ui` | Compose 预览 + 截图测试（封面卡/占位/错误态） | 各状态可渲染不崩 |
| `:feature:*` | ViewModel 单测（fake usecase）：状态流转 Loading→Ready/Error；UI 测试关键交互 | 状态机正确；交互事件映射正确 |
| `:feature:reader` | 单测进度节流；UI 测试模式切换 WEBTOON↔PAGER | 切换后页码保持；进度按 LRU 保存 |

**单模块通用验收**：模块编译通过、其单测全绿、无 lint error（warning 可接受）、对外接口与 §3 定义一致。

## 5. 开发顺序（模块推进次序）

> 原则：先底座后业务、先数据后 UI、每阶段可独立编译+单测。对应 PRD 里程碑 M0~M5。

| 阶段 | 内容（模块） | 出口条件 |
|------|--------------|----------|
| **S0 工程骨架** | Gradle 多模块初始化、版本目录、Hilt/Compose/Room/Coil 接入、签名配置、图标装配、`MANAGE_EXTERNAL_STORAGE` 声明 | `:app` 可出 Debug APK 并安装启动（空壳） |
| **S1 底座** | `:core:model`、`:core:common`（自然序/格式/Result）、`:core:ui` 主题 | common 单测全绿 |
| **S2 数据层** | `:core:data`（Room 表/DAO/偏好）+ `:core:domain` 接口与 UseCase（含 LRU/随机/清理） | data+domain 单测全绿（LRU/多对多/原子性） |
| **S3 本地源** | `:source:api` + `:source:local`（扫描/封面/页列表） | source 单测全绿（两级/封面/自然序） |
| **S4 主页** | `:feature:library`（权限引导、库根管理、网格+排序+无限滚动、随机） | 实机可导入目录并展示封面网格 |
| **S5 阅读器** | `:feature:reader`（Webtoon/Pager、进度 LRU、gif 动图） | 实机两种模式可读、进度正确 |
| **S6 收藏夹** | `:feature:collection`（CRUD、多对多、失效清理） | 实机收藏/移除/清理正确 |
| **S7 设置与打磨** | `:feature:settings`、中文化、Material3 美化、性能优化（大目录） | 中文 UI、大目录流畅无 OOM |
| **S8 交付** | Release 签名、keystore 生成与交付、集成验收、构建说明 | 正式 APK 实机安装运行，AC1~15 全过 |

---

## 6. 项目目录规则与文件树规划

### 6.1 目录规则

- 模块命名：`:core:*`（底座）、`:source:*`（来源）、`:feature:*`（业务页）、`:app`（壳）。
- 包结构：`com.comicnest.<module>.<layer>`；feature 内分 `ui/`（Composable+Screen）、`viewmodel/`。
- 资源：字符串仅 `values/strings.xml`（中文，D12）；图标在 `app/src/main/res/mipmap-*` 与 `drawable`。
- 密钥：`keystore/` 目录 + `keystore.properties`，**均加入 `.gitignore`**（D13）。
- 文档：`docs/`（PRD/DESIGN/素材）；参考代码在 `Reference Code/`（不参与构建）。

### 6.2 文件树（含每个文件内容规划）

```
ComicNest/
├─ settings.gradle.kts            # 模块清单：:app :core:* :source:* :feature:*；仓库 google/mavenCentral
├─ build.gradle.kts               # 根：插件别名 apply false
├─ gradle/libs.versions.toml      # 版本目录：AGP8.7.3 Kotlin2.0.21 ComposeBOM Room Coil Hilt Nav 等
├─ gradle.properties              # jvmargs、android.useAndroidX、kotlin code style
├─ .gitignore                     # 排除 keystore/、keystore.properties、build/、.idea/
├─ keystore/                      # （生成后）comicnest-release.jks【gitignore】
├─ keystore.properties            # storeFile/Password/keyAlias/keyPassword【gitignore】
├─ NOTICE                         # Apache-2.0 移植归属声明（Mihon 组件）
├─ docs/                          # PRD.md / DESIGN.md / demand.txt / assets/icon/ic/ComicNest.jpg
├─ Reference Code/                # 参考源码（不构建）
├─ app/
│  ├─ build.gradle.kts            # application；minSdk26/target35；signingConfigs.release 读 keystore.properties；Hilt/Compose
│  └─ src/main/
│     ├─ AndroidManifest.xml      # MANAGE_EXTERNAL_STORAGE；Application/MainActivity 声明；label=ComicNest
│     ├─ kotlin/com/comicnest/app/
│     │  ├─ ComicNestApp.kt        # @HiltAndroidApp；Coil ImageLoaderFactory 初始化
│     │  ├─ MainActivity.kt        # @AndroidEntryPoint；setContent{Theme{NavHost}}
│     │  └─ navigation/ComicNestNavHost.kt # 路由：library/reader/{id}/collections/collection/{id}/settings
│     └─ res/                     # mipmap-*(生成图标)、drawable(占位图)、values/strings.xml(中文)、themes
├─ core/
│  ├─ model/src/main/kotlin/com/comicnest/core/model/
│  │  ├─ ComicKey.kt  ├─ LibraryRoot.kt  ├─ ComicSummary.kt  ├─ ComicPageRef.kt
│  │  ├─ Collection.kt  ├─ CollectionEntry.kt  ├─ ReadProgress.kt  ├─ Enums.kt(SortMode/ReaderMode)
│  ├─ common/src/main/kotlin/com/comicnest/core/common/
│  │  ├─ util/NaturalOrder.kt     # naturalOrderCompare（移植 Mihon）
│  │  ├─ util/ImageFormats.kt     # isSupportedImage / isAnimatedExt
│  │  ├─ coroutine/Dispatchers.kt # IoDispatcher qualifier
│  │  ├─ result/SafeIo.kt         # runCatchingIo
│  │  └─ log/AppLog.kt
│  │  └─ src/test/...             # NaturalOrderTest、ImageFormatsTest
│  ├─ domain/src/main/kotlin/com/comicnest/core/domain/
│  │  ├─ repository/{LibraryRepository,CollectionRepository,ProgressRepository,PreferencesRepository}.kt
│  │  └─ usecase/{ScanLibrary,GetLibrary,AddLibraryRoot,RandomComic,OpenComic,SaveProgress,ManageCollection,CleanupInvalidCollections}UseCase.kt
│  │  └─ src/test/...             # 各 UseCase 单测（fake repo）
│  ├─ data/src/main/kotlin/com/comicnest/core/data/
│  │  ├─ db/{ComicNestDatabase.kt, entity/*.kt, dao/*.kt}
│  │  ├─ prefs/PreferencesRepositoryImpl.kt
│  │  ├─ repository/{LibraryRepositoryImpl,CollectionRepositoryImpl,ProgressRepositoryImpl}.kt
│  │  └─ di/DataModule.kt         # Hilt 绑定 Repository 接口→实现；提供 Database/DAO
│  │  └─ src/test/...             # Dao 单测（in-memory Room，LRU/级联/原子）
│  └─ ui/src/main/kotlin/com/comicnest/core/ui/
│     ├─ theme/{Theme.kt,Color.kt,Type.kt}
│     ├─ component/{ComicCoverCard.kt,ComicImage.kt,States.kt}
│     └─ image/CoilSetup.kt       # ImageLoader 配置（coil-gif）
├─ source/
│  ├─ api/src/main/kotlin/com/comicnest/source/api/ComicSource.kt
│  └─ local/src/main/kotlin/com/comicnest/source/local/
│     ├─ LocalComicSource.kt      # scanLibrary/getPages
│     ├─ scan/DirectoryScanner.kt # 两级扫描/隐藏跳过/空目录跳过
│     ├─ cover/CoverPicker.kt     # pickCover（最小名）
│     └─ di/SourceModule.kt       # 绑定 ComicSource→LocalComicSource
│     └─ src/test/...             # 扫描/封面/自然序 单测（临时目录）
└─ feature/
   ├─ library/src/main/kotlin/com/comicnest/feature/library/
   │  ├─ ui/{LibraryScreen.kt, components/RootPickerDialog.kt}
   │  └─ viewmodel/LibraryViewModel.kt
   ├─ reader/src/main/kotlin/com/comicnest/feature/reader/
   │  ├─ ui/{ReaderScreen.kt}
   │  ├─ viewer/{WebtoonViewer.kt,PagerViewer.kt,Viewer.kt}
   │  └─ viewmodel/ReaderViewModel.kt
   ├─ collection/src/main/kotlin/com/comicnest/feature/collection/
   │  ├─ ui/{CollectionListScreen.kt,CollectionDetailScreen.kt,AddToCollectionDialog.kt}
   │  └─ viewmodel/{CollectionListViewModel.kt,CollectionDetailViewModel.kt}
   └─ settings/src/main/kotlin/com/comicnest/feature/settings/
      ├─ ui/SettingsScreen.kt
      └─ viewmodel/SettingsViewModel.kt
```

### 6.3 每个文件内容要点

- 各 `*ViewModel.kt`：`StateFlow<UiState>` + 事件函数；注入 UseCase；不直接碰 IO。
- 各 `*Screen.kt`：纯 Compose 渲染 UiState；回调 ViewModel 事件；不含业务判断。
- 各 `*RepositoryImpl.kt`：实现接口；`runCatchingIo` 包裹；调 DAO/Source。
- 各 `*UseCase.kt`：单一业务规则；纯 Kotlin；可注入 fake 测试。
- `di/*Module.kt`：Hilt `@Binds`/`@Provides`，接口→实现。

### 6.4 图标处理（已提供 512×512 JPG）

- 源：`docs/assets/icon/ic/ComicNest.jpg`。
- legacy 图标：直接缩放生成 `mipmap-mdpi(48)/hdpi(72)/xhdpi(96)/xxhdpi(144)/xxxhdpi(192)` 的 `ic_launcher.png` 与 `ic_launcher_round.png`。
- 自适应图标：`mipmap-anydpi-v26/ic_launcher.xml`，`foreground` = 原图内缩（安全区 66%）图层，`background` = 采样浅蓝纯色（约 `#5EB2F6`）；避免 JPG 无透明导致的白角问题。
- 生成方式：构建期脚本或一次性用工具产出提交进 `res/`（不依赖运行时）。

---

## 7. 集成/联调测试与整体验收

### 7.1 集成/联调测试

| 场景 | 步骤 | 预期 |
|------|------|------|
| IT1 端到端导入→阅读 | 安装→授权→添加库根→扫描→点开漫画→翻页→返回 | 网格出现、封面正确、阅读流畅、进度保存 |
| IT2 排序+无限滚动 | 大量漫画（≥500）滚动+切换排序 | 流畅、顺序正确、无 OOM |
| IT3 双模式+进度 LRU | 读 6 本不同漫画→回看第 1、6 本 | 最近 5 本续读；第 6 本（被挤出）从第一页 |
| IT4 收藏多对多+失效 | 建 2 夹、一漫画入 2 夹→删除源目录→刷新 | 多夹可见；失效提示+自动清理 |
| IT5 随机 | 多次点击随机 | 均落在已导入漫画内；空库提示 |
| IT6 六格式+gif | 各格式图片实机打开 | 均显示；gif 播放动画 |
| IT7 重名漫画 | 两库根同名漫画 | 并列展示、互不干扰、各自封面/页正确 |
| IT8 签名安装 | `apksigner verify` + 实机侧载 | 正式签名；不触发"测试版拒装" |

### 7.2 整体验收标准

- 逐条满足 PRD **AC1~AC15**（含 LRU=5、gif 动图、多对多收藏、重名唯一键、绝对路径、无限滚动、中文、正式签名 APK）。
- 全部模块单测绿；集成 IT1~IT8 通过；大目录性能达标（滚动≥55fps、无 OOM）。
- 交付物：`ComicNest-release.apk` + `keystore/` + 构建/签名说明 + NOTICE。

---

## 8. 异常处理与状态流转

### 8.1 异常处理总则

- **不崩**：所有文件 IO/解码/DB 操作包裹 `runCatchingIo`；异常→`Result.failure`→UiState.Error/占位，绝不抛到 UI 线程。
- **降级**：封面缺失→占位图；单页解码失败→错误占位图并跳过；目录失效→提示+可移除。
- **日志**：统一 `AppLog`，release 关闭 verbose。

### 8.2 分场景异常策略

| 场景 | 检测 | 处理 |
|------|------|------|
| 未授予全部文件访问 | 权限检查 | 引导页→跳系统设置；未授权不扫描 |
| 库根路径不存在/被删 | 扫描前 `File.exists/isDirectory` | 该 root 标记失效；主页提示+允许移除（D11） |
| 漫画目录空/无有效图 | 扫描时计数=0 | 跳过不展示（D11） |
| 单图损坏 | Coil 解码失败 | 错误占位图，跳过该页，不崩（D11） |
| 文件名非数字 | 自然序比较 | 按自然序/字典序稳定排序（D11） |
| 重名漫画 | ComicKey 含 rootId | 并存互不干扰（D10） |
| 收藏指向失效 | cleanupInvalid | 提示+自动删除 entry（D7） |
| 进度超出总页数 | 打开时校验 | 夹取到 [0,total-1] 或回 0 |
| DB 迁移/损坏 | Room 回调 | 降级重建空库（保留用户文件） |

### 8.3 状态流转（密封状态机）

**Library UiState**：`Idle → Loading → Ready(comics) | Empty | Error`；`onRefresh` 触发 `Loading→Ready`。
**Reader UiState**：`Loading → Ready(pages,startIndex,mode) | Error`；`onToggleMode` 在 Ready 内切换 mode 且保持页码。
**Collection UiState**：`Loading → Ready(list) | Empty`；失效清理后 `Ready` + 一次性 Snackbar 事件。
**权限状态**：`Unknown → Granted | Denied`；Denied→引导子状态。

> 所有状态以 `StateFlow` 单一来源暴露；一次性事件（Snackbar/导航）用 `Channel`/`SharedFlow` 避免重放。

---

## 9. 前端 UI 设计（页面与布局）

> 基座：Jetpack Compose + Material 3；单 Activity + 底部导航 + NavHost。全中文文案（D12）。深浅色自适应，可选动态取色（Material You）。触控目标 ≥ 48dp，遵循 4dp 栅格。

### 9.1 全局导航骨架

顶层 `Scaffold` 承载 **底部导航栏（NavigationBar）** 三入口；**阅读器为全屏页，隐藏底栏**。

```
┌───────────────────────────────┐
│        [ 页面内容区 ]           │
│                               │
├───────────────────────────────┤
│   书架        收藏        设置   │  ← NavigationBar（3 项）
└───────────────────────────────┘
```

| 底部入口 | 路由 | 对应 feature |
|----------|------|--------------|
| 书架 | `library`（start） | `:feature:library` |
| 收藏 | `collections` | `:feature:collection` |
| 设置 | `settings` | `:feature:settings` |
| （全屏，不入底栏） | `reader/{comicKeyId}`、`collection/{id}` | reader / collection 详情 |

### 9.2 视觉规范（Design Tokens）

| 项 | 规格 |
|----|------|
| 主题色 | Material3 语义色（primary/surface…）；背景 `surface`，卡片 `surfaceContainer` |
| 网格封面比例 | **2:3**（竖版漫画封面），圆角 12dp |
| 网格列 | `GridCells.Adaptive(minSize = 108.dp)`（手机约 3 列，平板自动增列） |
| 卡片间距 | 横/纵 8dp；页面外边距 12dp |
| 顶栏 | `TopAppBar`，标题 20sp；操作图标 24dp |
| 字体 | 系统默认中文字体回退；正文 14sp，卡片标题 13sp 单行省略 |
| 占位 | 封面缺失=书本矢量占位；错误=断图图标 + "无法显示" |
| 动效 | Material Motion 淡入/共享元素（可选）；扫描时顶部线性进度 |

### 9.3 首页 / 书架（LibraryScreen）

```
┌──────────────────────────────────────────┐
│ ComicNest        [排序⇅] [随机] [刷新]   │  ← TopAppBar + 操作
├──────────────────────────────────────────┤
│ 目录A · 目录F ▾ (共 128 部)                 │  ← 库根概览/筛选（可选）
│ ┌────┐ ┌────┐ ┌────┐                       │
│ │封面│ │封面│ │封面│   ← ComicCoverCard     │
│ │漫画│ │漫画│ │漫画│      (2:3 + 名称)       │
│ └────┘ └────┘ └────┘                       │
│ ┌────┐ ┌────┐ ┌────┐   … 无限滚动          │
│ │    │ │    │ │    │                       │
│ └────┘ └────┘ └────┘                       │
├──────────────────────────────────────────┤
│   书架        收藏        设置              │
└──────────────────────────────────────────┘
```

- **顶栏**：标题 "ComicNest"；动作：排序切换（名称/导入时间，弹 `DropdownMenu`）、🎲 随机、⟳ 刷新（触发 `ScanLibraryUseCase`）。
- **内容**：`LazyVerticalGrid(Adaptive(108dp))` **无限滚动**（D4）；每格 `ComicCoverCard`（封面=按名最小图 D3，下叠名称单行省略）。点击→`reader/{key}`；**长按**→弹出菜单（收藏到…、详情）。
- **状态呈现**：
  - `Empty`（无库根）→ 居中引导插画 + 按钮 "添加漫画目录"（→ `RootPickerDialog`）；
  - 未授权 `MANAGE_EXTERNAL_STORAGE` → 引导页 + "去开启全部文件访问"按钮（D2）；
  - `Loading` → 顶部线性进度条 + 骨架格；
  - `Error` → 错误占位 + "重试"。

### 9.4 阅读器（ReaderScreen，全屏）

```
下拉式（Webtoon，默认）          左右翻页（Pager）
┌────────────────────┐        ┌────────────────────┐
│ ← 漫画名  12/240 ⚙♡ │(自动隐) │ ← 漫画名 12/240 ⚙♡ │
│  ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒  │        │ ┌────────────────┐ │
│  ▒ 连续长图 ▒      │        │ │  单页 fit 屏幕  │ │
│  ▒ 向下滚动 ▒      │        │ └──────────────── │
│  ▒▒▒▒▒▒▒▒▒▒▒▒▒▒  │        │  ◀ 点击翻页 ▶       │
│  …(无缝拼接)       │        │                    │
└────────────────────┘        └────────────────────┘
        底部：页码指示器 / 进度条（轻点中央显隐系统栏与顶栏）
```

- **顶栏**（可隐）：返回、漫画名、`当前页/总页`、模式切换（Webtoon↔Pager）、收藏入口（♡）。
- **Webtoon**：`LazyColumn` 连续长图，图片按屏宽等比、无缝拼接（默认，D：下拉式）。
- **Pager**：`HorizontalPager` 左右翻页，单页 fit（翻页模式）。
- **gif 动图**：`ComicImage(isAnimated=true)` 播放动画（D8）；损坏页显示错误占位并跳过（D11）。
- **进度**：进入定位到 `startIndex`（LRU=5 命中则续读，否则第一页 D6）；翻页节流保存。
- **交互**：轻点中央显隐系统栏/顶栏；切模式保持当前页码。

### 9.5 收藏夹列表（CollectionListScreen）

```
┌──────────────────────────────────────────┐
│ 我的收藏                                    │
│ ┌────────┐ ────────┐ ────────┐          │
│ │追更中   │ │神作     │ │待阅     │  ← 卡片 │
│ │12 部    │ │5 部     │ │30 部    │  计数   │
│ └────────┘ └────────┘ └────────┘          │
│                              ( + )  ← FAB 新建
├──────────────────────────────────────────┤
│   书架        收藏        设置              │
└──────────────────────────────────────────┘
```

- 网格/列表展示各收藏夹（名称 + 漫画数）；`FAB(+)` 新建（弹命名 `AlertDialog`）。
- 卡片菜单：重命名 / 删除（D7 增删改）；进入详情→`collection/{id}`。
- 进入时若 `cleanupInvalid` 清理数>0 → 顶部 `Snackbar` "已清理 N 个失效收藏"（D7）。

### 9.6 收藏夹详情（CollectionDetailScreen）

- 复用首页 `ComicCoverCard` 网格；每格角标/长按菜单支持 **从本夹移除**；点击→阅读器。
- 空态："还没有漫画，去书架长按收藏吧" + 跳书架按钮。

### 9.7 加入收藏对话框（AddToCollectionDialog）

- 长按漫画触发；列出全部收藏夹 + 复选框（**多对多**，当前所属默认勾选 D7）；底部"+ 新建收藏夹"；确认→`addComic`/`removeComic`。

### 9.8 设置页（SettingsScreen）

```
┌──────────────────────────────────────────┐
│ 设置                                        │
│ 漫画库目录                                  │
│  • 目录A (/storage/…/A)        [移除]       │
│  • 目录F (/storage/…/F)        [移除]       │
│  [+ 添加目录]                               │
│ 阅读                                        │
│  • 默认阅读方式        [下拉式 ▾]            │
│ 关于                                        │
│  • 版本 1.0.0     • 开源许可                 │
├──────────────────────────────────────────┤
│   书架        收藏        设置              │
└──────────────────────────────────────────┘
```

- 分组列表（`LazyColumn` + `ListItem`）：**库根管理**（增/删/失效标记红色提示）、**阅读偏好**（默认模式下拉）、**关于**（版本/开源声明）。

### 9.9 添加目录对话框（RootPickerDialog）

- 提供两种录入：**手输绝对路径**（`OutlinedTextField` + 校验存在/是目录）或调起系统**目录选择器**回填路径；确认→`AddLibraryRootUseCase`→成功触发扫描。

### 9.10 组件与页面映射

| 页面/组件 | 复用 `:core:ui` | 关键 Compose 构件 |
|-----------|-----------------|-------------------|
| 网格单元 | `ComicCoverCard`、`ComicImage` | `LazyVerticalGrid`、`AsyncImage` |
| 阅读器 | `ComicImage`、`States` | `LazyColumn` / `HorizontalPager` |
| 加载/错误/空态 | `LoadingState`/`ErrorState`/引导页 | `Scaffold`、`LinearProgressIndicator` |
| 对话框 | — | `AlertDialog`、`ModalBottomSheet`（长按菜单） |
| 全局 | `ComicNestTheme`、`NavigationBar` | `Scaffold` + `NavHost` |

> 说明：所有页面仅渲染 ViewModel 暴露的 `UiState`，交互回调 ViewModel 事件；UI 层不含业务判断（与 §3.7 一致）。

---

## 10. 增量需求 v0.2（首页搜索 / 分组跳转 / 添加目录入口收敛 / 阅读器收藏）

> 本节为 v0.2 增量设计，上游依据用户 2026-10 提出的四点修改。改动**集中在 `:feature:library`、`:feature:reader`、`:core:ui` 展示层**，**不触碰数据层/领域层/扫描排序逻辑**，以最小化对既有可用功能（书架列表、阅读、LRU 进度、收藏失效清理）的回归风险。实施后须做 §10.6 回归测试。

### 10.1 添加目录入口收敛（点①）

> **关键核实（旧包 vs 源码不一致）**：用户实机截图右下角存在一个蓝色“添加目录”悬浮按钮（FAB），但**当前磁盘源码的 `LibraryScreen.kt` 已不再包含该 FAB**（全文 + 全库不忽略搜索均证实：`LibraryScreen` 无任何 `FloatingActionButton`，`Scaffold` 未传 `floatingActionButton`；“添加目录”文本仅剩设置页 `SettingsScreen` 的一个列表项）。时间戳佐证：`app-debug.apk` 构建于 23:57:13，而 `LibraryScreen.kt` 最后修改于 23:58:28（晚约 1 分钟）——即**用户安装的是旧 APK，该 FAB 在之后的一次源码改动中已被删除**。

- **净结论**：点①的诉求（有漫画时不显“添加目录”按钮）**在源码层已满足**；用户仍看见是因为未重新构建/安装。本项目**无需为此新增删除动作**，只需：(a) 重新构建并安装，使旧 FAB 消失；(b) 在新增搜索框改造顶栏时，**确保不再把“添加目录”入口带回 `Ready` 页**。
- 目标形态：书架 `Ready`（有漫画）顶栏仅「☰（打开抽屉）· ComicNest · 排序/随机/刷新」，**无“添加目录”、无搜索框、无分组按钮**；“添加漫画目录”仅在 `Empty`（首页无漫画）时居中显示。
- 添加入口共**仅两处**：首页无漫画时的居中引导（现有 `EmptyState`/自建居中引导 + `DirectoryPickerDialog`）、设置页（现有 `SettingsScreen` 库根管理）。

### 10.2 首页搜索框（点②，v0.3：入左侧抽屉）

**交互/布局**
- v0.2 曾把搜索框置于顶栏，导致顶栏拥挤且输入框发灰不可点。v0.3 改为：顶栏最左新增 **☰ 汉堡键**（`IconButton` + `Icons.Filled.Menu`），点击打开 `ModalNavigationDrawer` 左侧抽屉；**搜索框置于抽屉顶部**（标准 `OutlinedTextField`，前导🔍、尾部✕清除、`singleLine`、圆角 24dp、`fillMaxWidth`），可正常聚焦输入。
- 顶栏恢复标题 “ComicNest”（不再被搜索框占用），右侧仅「排序/随机/刷新」。

**逻辑（ViewModel，纯内存过滤，不改数据层）**
- `LibraryViewModel` 新增 `private val _query = MutableStateFlow("")` 与对外 `query: StateFlow<String>`；新增事件 `onQueryChange(text: String)`。
- 渲染：将 `getLibrary()` 流与 `_query` `combine`，对 `LibraryUiData.comics` 按 `key.displayName.contains(query, ignoreCase = true)` 过滤（自然序/时间序由上游流保证，过滤不改变相对顺序）。`trim` 后空串视为全量。
- 状态：搜索后命中 0 条不进入 `Empty`（避免误显“添加入口”），而是进入 `NoResult(query)` 在网格区显示“无匹配漫画”提示（复用 `EmptyState` 但不带“添加目录”动作）。
- 输入即过滤（抽屉保持展开，网格在背后实时更新）。
- **生命周期**：`query` 为**纯 UI 临时态**，不写库/不写 DataStore，离开页面（ViewModel 销毁）即重置；不与其他页共享。规模：当前库约 600 部，内存过滤开销可忽，无需新增 DB 查询或 UseCase。

### 10.3 大列表分组跳转（点③，方案 A：v0.3 入左侧抽屉）

背景：库内≈600 部，纯手工往下翻耗时长。需在“按名称/按导入时间”排序下快速定位。

**方案 A — 组跳转（保持单一连续列表），v0.3 移到抽屉**
- **分组固定按“全库总数”划分**（与搜索无关，用户确认）：`LibraryViewModel` 额外暴露 `comicCount: StateFlow<Int>`（= `data.comics.size`，不受 `query` 过滤影响）。抽屉搜索框下方按 `GROUP_SIZE = 100` 用 `comicCount` 实时切分，自上而下依次渲染 `第1组 1–100`、`第2组 101–200`…（`ceil(total/100)` 项）。
- 仅当 `comicCount > GROUP_SIZE` 时显示分组区；不足一组时显示“漫画不足 100 本，无需分组”。
- 每项为可点击行（`GroupRow`）：当前所在组以 `Check` 图标 + `secondaryContainer` 高亮（`gridState.firstVisibleItemIndex / GROUP_SIZE` 派生，仅在搜索为空时高亮）。点击某组 → `pendingGroupTarget = i * GROUP_SIZE`；若搜索框非空先 `onQueryChange("")` 回到全量；`scope.launch { drawerState.close() }` 关抽屉；`LaunchedEffect(pendingGroupTarget, state)` 在列表就绪后 `gridState.scrollToItem(target)`（受清空搜索后的全量列表驱动，故落点准确）。
- **实现前提**：将 `ComicGrid` 内部 `LazyGridState` 提升（hoist）为可外部持有的 `rememberLazyGridState()`，以便 `scrollToItem`。
- **生命周期**：组边界不预生成/不入库，基于 `comicCount` 实时计算；sort 变化不改总数故组数稳定；query 变化**不影响分组区**（分组按全库）。滚动到组顶后列表仍为连续无限滚动。

> 因“分组按全库、搜索只过滤背面网格”两者语义独立，故**点组时清空搜索**以保证 `scrollToItem` 落在全库第 N 本位置，语义自洽。

### 10.4 阅读器 ♡ 收藏入口（点④）

现状：`ReaderScreen` 顶栏 ♡ 为空回调（“收藏入口在 S6 接入”）。本节补齐。

**ViewModel（`ReaderViewModel`）**
- 新增注入 `CollectionRepository` + `ManageCollectionUseCase`（`comicKey` 已具备）。
- 新增对外状态：`isFavorite: StateFlow<Boolean>`（该漫画是否属于任一收藏夹，驱动图标形态）、`favorites: StateFlow<FavoritesUiState?>`（对话框数据）。
- 新增事件：`onFavoriteClick()` — 以 `combine(observeCollections(), collectionsOf(key))` 构建 `FavoritesUiState` 并展示；`onToggleFavorite(collectionId, add)`（复用 `ManageCollectionUseCase.addComic/removeComic`）；`onDismissFavorites()`（取消订阅 job）。首次订阅时即回读一次以初始化 `isFavorite`。

**UI**
- 顶栏按钮图标按 `isFavorite` 切换 `FavoriteBorder`（未收藏）↔`Favorite`（已收藏）；点击弹“加入收藏夹”多选对话框（多对多、可勾选加入/移出，数据与书架同源、指向式不复制文件）。

**共享组件抽取（为 DRY 且低回归）**
- 将收藏多选对话框与状态从 `:feature:library` 上移至 `:core:ui`：新增纯 UI `FavoritesDialog(state, onToggle, onDismiss)` 与数据载体 `FavoritesUiState(key, collections, selectedIds)`（入参用 `:core:model` 的 `Collection`/`ComicKey`，不引入业务依赖）。书架与阅读器共用。
- `FavoritesState`（原在 `feature:library.viewmodel`）合并/重命名为 `FavoritesUiState`；`LibraryViewModel` 与 `LibraryScreen` 相应改为引用 `:core:ui` 类型。**书架长按收藏流程行为不变**，仅改类型/导入路径（属回归必测项）。

### 10.5 涉及文件清单

| 模块 | 文件 | 变更 |
|------|------|------|
| `:feature:library` | `viewmodel/LibraryViewModel.kt` | 新增 `_query`/`onQueryChange`、内存过滤、`NoResult` 渲染；新增 `comicCount`（全库总数，供抽屉分组）；`FavoritesState`→`FavoritesUiState` |
| `:feature:library` | `ui/LibraryScreen.kt` | 顶栏改为☰+ComicNest+排序/随机/刷新；新增 `ModalNavigationDrawer` 左侧抽屉（顶部搜索框 + 全库分组跳转列表）；hoist `LazyGridState`；`Ready` 不加添加入口；空结果 `NoResult` 提示 |
| `:feature:library` | `ui/components/AddToCollectionDialog.kt` | 迁移至 `:core:ui`（或删除并改引用 `FavoritesDialog`） |
| `:feature:reader` | `viewmodel/ReaderViewModel.kt` | 注入收藏依赖；`isFavorite`/`favorites`/相关事件 |
| `:feature:reader` | `ui/ReaderScreen.kt` | ♡ 图标形态切换 + 弹 `FavoritesDialog` |
| `:core:ui` | `component/FavoritesDialog.kt`（新） | 收藏多选共享对话框 + `FavoritesUiState` |
| `:core:ui` | `component/SearchField`（可内联于 library） | 顶栏搜索输入框（若复用则置 `:core:ui`） |

> 数据层（Room/DAO/Repository）、领域层（UseCase）、扫描与排序（`ComicDao.observeByName`/自然序）**均不改**。

### 10.6 回归影响与测试

- **单元测试**：`LibraryViewModel` 搜索过滤（命中/未命中/大小写不敏感/空串）与组划分/跳转索引计算（边界：恰好 100、101、搜索后≤100）；`ReaderViewModel` 收藏 toggle 与 `isFavorite` 流转（用 fake repo）。
- **UI/手工回归**（重点验证“原有功能不变”）：
  - 书架：无漫画时居中“添加目录”引导仍可用；**有漫画时顶栏无添加入口**；排序切换（名称/时间）正常；随机/刷新正常；无限滚动不回归。
  - 搜索：输入即时过滤、清空恢复全量；无匹配时不误弹“添加目录”引导。
  - 组跳转：>100 时选择器可见；选择某组滚动到对应位置；≤100 时自动隐藏。
  - 收藏：书架长按弹框勾选加入/移出（与改造前一致）；阅读器♡点击弹同一框、图标随状态切换；一处收藏后另一处可见已收藏（数据同源）。
  - 阅读/进度：打开定位、Webtoon↔Pager 切换保持页码、LRU=5 进度（本次改动不应影响）。
- **构建**：改动后需重新出 Debug；若交付同步重新出 Release 签名包（历史：Release 曾为旧版）。

---

*（DESIGN v0.2 完。实施时若与 PRD 冲突，以 PRD 决策 D1~D14 为准；本文档负责"如何实现"。）*
