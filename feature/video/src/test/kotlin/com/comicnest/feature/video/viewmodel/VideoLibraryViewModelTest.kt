package com.comicnest.feature.video.viewmodel

import com.comicnest.core.domain.repository.PreferencesRepository
import com.comicnest.core.domain.repository.VideoRepository
import com.comicnest.core.domain.usecase.video.AddVideoRootUseCase
import com.comicnest.core.domain.usecase.video.DeleteVideoUseCase
import com.comicnest.core.domain.usecase.video.EnsureVideoThumbnailUseCase
import com.comicnest.core.domain.usecase.video.GetVideoLibraryUseCase
import com.comicnest.core.domain.usecase.video.RandomVideoUseCase
import com.comicnest.core.domain.usecase.video.RenameVideoUseCase
import com.comicnest.core.domain.usecase.video.RemoveVideoRootUseCase
import com.comicnest.core.domain.usecase.video.ScanVideoLibraryUseCase
import com.comicnest.core.model.RecentVideo
import com.comicnest.core.model.ReaderMode
import com.comicnest.core.model.SortMode
import com.comicnest.core.model.VideoKey
import com.comicnest.core.model.VideoProgress
import com.comicnest.core.model.VideoRoot
import com.comicnest.core.model.VideoSortMode
import com.comicnest.core.model.VideoSummary
import com.comicnest.core.model.VideoThumbnailState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 视频页 ViewModel 状态机与交互单测（DESIGN v2 §3.8 / V3 / V10）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoLibraryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun summary(name: String) = VideoSummary(
        key = VideoKey("vr", name),
        absPath = "/v/$name",
        sizeBytes = 1L,
        lastModified = 1L,
        importedAt = 0L,
        durationMs = null,
        thumbPath = null,
        thumbState = VideoThumbnailState.PENDING,
    )

    private fun root() = VideoRoot.of("/v", "v", 0L)

    private fun viewModel(repo: FakeVideoRepo, prefs: FakePrefs = FakePrefs()) =
        VideoLibraryViewModel(
            getVideoLibrary = GetVideoLibraryUseCase(repo, prefs),
            scanVideoLibrary = ScanVideoLibraryUseCase(repo),
            addVideoRoot = AddVideoRootUseCase(repo),
            removeVideoRoot = RemoveVideoRootUseCase(repo),
            randomVideo = RandomVideoUseCase(repo),
            deleteVideo = DeleteVideoUseCase(repo),
            renameVideo = RenameVideoUseCase(repo),
            ensureThumbnail = EnsureVideoThumbnailUseCase(repo),
            preferencesRepository = prefs,
        )

    @Test
    fun `未添加目录时为空态首次引导`() = runTest(dispatcher) {
        val vm = viewModel(FakeVideoRepo())
        advanceUntilIdle()

        assertEquals(VideoLibraryUiState.Empty(firstRun = true), vm.uiState.value)
        assertEquals(0, vm.totalCount.value)
    }

    @Test
    fun `已添加目录但无视频为空态非首次`() = runTest(dispatcher) {
        val repo = FakeVideoRepo().apply { rootsFlow.value = listOf(root()) }
        val vm = viewModel(repo)
        advanceUntilIdle()

        assertEquals(VideoLibraryUiState.Empty(firstRun = false), vm.uiState.value)
    }

    @Test
    fun `有视频时进入就绪态并统计总数`() = runTest(dispatcher) {
        val repo = FakeVideoRepo().apply {
            rootsFlow.value = listOf(root())
            videosFlow.value = listOf(summary("a.mp4"), summary("b.mp4"))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        val state = vm.uiState.value as VideoLibraryUiState.Ready
        assertEquals(2, state.videos.size)
        assertEquals(2, vm.totalCount.value)
    }

    @Test
    fun `搜索命中按文件名过滤且不区分大小写`() = runTest(dispatcher) {
        val repo = FakeVideoRepo().apply {
            rootsFlow.value = listOf(root())
            videosFlow.value = listOf(summary("Alpha.mp4"), summary("beta.mkv"))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onQueryChange("alpha")
        advanceUntilIdle()

        val state = vm.uiState.value as VideoLibraryUiState.Ready
        assertEquals(1, state.videos.size)
        assertEquals("Alpha.mp4", state.videos.first().key.fileName)
        // 总数不受搜索影响（分组固定按全库划分）
        assertEquals(2, vm.totalCount.value)
    }

    @Test
    fun `搜索无结果时进入无结果态`() = runTest(dispatcher) {
        val repo = FakeVideoRepo().apply {
            rootsFlow.value = listOf(root())
            videosFlow.value = listOf(summary("a.mp4"))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onQueryChange("zzz")
        advanceUntilIdle()

        assertEquals(VideoLibraryUiState.NoResult("zzz"), vm.uiState.value)
    }

    @Test
    fun `长按选择删除并确认后调用仓储`() = runTest(dispatcher) {
        val repo = FakeVideoRepo().apply {
            rootsFlow.value = listOf(root())
            videosFlow.value = listOf(summary("a.mp4"))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        val target = (vm.uiState.value as VideoLibraryUiState.Ready).videos.first()
        vm.onLongPressVideo(target)
        assertEquals(target, vm.actionTarget.value)

        vm.onRequestDelete(target)
        assertNull(vm.actionTarget.value)
        assertEquals(target, vm.pendingDelete.value)

        vm.onConfirmDelete()
        advanceUntilIdle()

        assertNull(vm.pendingDelete.value)
        assertEquals(listOf(target.key), repo.deleted)
    }

    @Test
    fun `长按选择重命名并确认后调用仓储`() = runTest(dispatcher) {
        val repo = FakeVideoRepo().apply {
            rootsFlow.value = listOf(root())
            videosFlow.value = listOf(summary("a.mp4"))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        val target = (vm.uiState.value as VideoLibraryUiState.Ready).videos.first()
        vm.onLongPressVideo(target)
        vm.onRequestRename(target)
        assertNull(vm.actionTarget.value)
        assertEquals(target, vm.pendingRename.value)

        val message = async { vm.events.first() }
        vm.onConfirmRename("新名字")
        advanceUntilIdle()

        assertNull(vm.pendingRename.value)
        assertEquals(listOf(target.key to "新名字.mp4"), repo.renamed)
        assertTrue((message.await() as VideoEvent.Message).text.contains("已重命名为"))
    }

    @Test
    fun `重命名名称为空时提示且不调用仓储`() = runTest(dispatcher) {
        val repo = FakeVideoRepo().apply {
            rootsFlow.value = listOf(root())
            videosFlow.value = listOf(summary("a.mp4"))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        val target = (vm.uiState.value as VideoLibraryUiState.Ready).videos.first()
        vm.onRequestRename(target)

        val message = async { vm.events.first() }
        vm.onConfirmRename("   ")
        advanceUntilIdle()

        assertTrue(repo.renamed.isEmpty())
        assertTrue((message.await() as VideoEvent.Message).text.contains("名称不能为空"))
    }

    @Test
    fun `卡片可见时仅对 PENDING 视频触发缩略图`() = runTest(dispatcher) {
        val repo = FakeVideoRepo().apply { rootsFlow.value = listOf(root()) }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onThumbnailVisible(summary("a.mp4"))
        vm.onThumbnailVisible(summary("a.mp4"))
        advanceUntilIdle()

        assertEquals(1, repo.generated.size)
        assertEquals("a.mp4", repo.generated.first().fileName)
    }

    @Test
    fun `切换排序写入偏好仓储`() = runTest(dispatcher) {
        val repo = FakeVideoRepo().apply {
            rootsFlow.value = listOf(root())
            videosFlow.value = listOf(summary("a.mp4"))
        }
        val prefs = FakePrefs()
        val vm = viewModel(repo, prefs)
        advanceUntilIdle()

        vm.onSortChange(VideoSortMode.IMPORT_TIME)
        advanceUntilIdle()

        assertEquals(VideoSortMode.IMPORT_TIME, prefs.videoSortFlow.value)
    }

    @Test
    fun `生成中状态为 READY 的视频不再触发`() = runTest(dispatcher) {
        val repo = FakeVideoRepo().apply { rootsFlow.value = listOf(root()) }
        val vm = viewModel(repo)
        advanceUntilIdle()

        val ready = summary("a.mp4").copy(
            thumbState = VideoThumbnailState.READY,
            thumbPath = "/thumbs/a.jpg",
        )
        vm.onThumbnailVisible(ready)
        advanceUntilIdle()

        assertTrue(repo.generated.isEmpty())
    }

    private class FakePrefs : PreferencesRepository {
        val sortFlow = MutableStateFlow(SortMode.NAME)
        val readerFlow = MutableStateFlow(ReaderMode.WEBTOON)
        val videoSortFlow = MutableStateFlow(VideoSortMode.NAME)

        override fun observeSortMode(): Flow<SortMode> = sortFlow
        override suspend fun setSortMode(mode: SortMode) {
            sortFlow.value = mode
        }

        override fun observeReaderMode(): Flow<ReaderMode> = readerFlow
        override suspend fun setReaderMode(mode: ReaderMode) {
            readerFlow.value = mode
        }

        override fun observeVideoSortMode(): Flow<VideoSortMode> = videoSortFlow
        override suspend fun setVideoSortMode(mode: VideoSortMode) {
            videoSortFlow.value = mode
        }
    }

    private class FakeVideoRepo : VideoRepository {
        val rootsFlow = MutableStateFlow<List<VideoRoot>>(emptyList())
        val videosFlow = MutableStateFlow<List<VideoSummary>>(emptyList())
        val recentFlow = MutableStateFlow<List<RecentVideo>>(emptyList())
        val deleted = mutableListOf<VideoKey>()
        val generated = mutableListOf<VideoKey>()
        val renamed = mutableListOf<Pair<VideoKey, String>>()

        override fun observeRoots(): Flow<List<VideoRoot>> = rootsFlow

        override suspend fun addRoot(path: String): Result<VideoRoot> =
            Result.success(VideoRoot.of(path, path, 0L))

        override suspend fun removeRoot(rootId: String): Result<Unit> = Result.success(Unit)

        override fun observeVideos(sort: VideoSortMode): Flow<List<VideoSummary>> = videosFlow

        override suspend fun refresh(): Result<Int> = Result.success(videosFlow.value.size)

        override suspend fun getVideo(key: VideoKey): Result<VideoSummary> =
            videosFlow.value.firstOrNull { it.key == key }?.let { Result.success(it) }
                ?: Result.failure(NoSuchElementException("no video"))

        override suspend fun generateThumbnail(key: VideoKey): Result<Unit> {
            generated += key
            return Result.success(Unit)
        }

        override suspend fun saveThumbnail(
            key: VideoKey,
            path: String,
            durationMs: Long?,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun markThumbnailFailed(key: VideoKey): Result<Unit> = Result.success(Unit)

        override suspend fun deleteVideo(key: VideoKey): Result<Unit> {
            deleted += key
            videosFlow.update { list -> list.filterNot { it.key == key } }
            return Result.success(Unit)
        }

        override suspend fun renameVideo(key: VideoKey, newFileName: String): Result<VideoKey> {
            renamed += key to newFileName
            return Result.success(VideoKey(key.rootId, newFileName))
        }

        override suspend fun saveProgress(
            key: VideoKey,
            positionMs: Long,
            durationMs: Long,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun getProgress(key: VideoKey): VideoProgress? = null

        override fun observeRecent(): Flow<List<RecentVideo>> = recentFlow
    }
}
