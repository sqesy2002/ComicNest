package com.comicnest.core.domain.usecase.video

import com.comicnest.core.domain.FakePreferencesRepository
import com.comicnest.core.domain.FakeVideoRepository
import com.comicnest.core.domain.video
import com.comicnest.core.model.VideoSortMode
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 视频域 UseCase 规则单测（DESIGN v2 §3.3 / V8 / V12）。 */
class VideoUseCaseRulesTest {

    // ---- SaveVideoProgress：阈值 10s / duration 非正 / 夹取 ----
    @Test
    fun `进度-不足十秒不写入`() = runTest {
        val repo = FakeVideoRepository()
        val useCase = SaveVideoProgressUseCase(repo)
        useCase(video("a.mp4").key, 9_999L, 60_000L).getOrThrow()
        assertTrue(repo.savedProgress.isEmpty())
    }

    @Test
    fun `进度-时长非正不写入`() = runTest {
        val repo = FakeVideoRepository()
        SaveVideoProgressUseCase(repo)(video("a.mp4").key, 30_000L, 0L).getOrThrow()
        assertTrue(repo.savedProgress.isEmpty())
    }

    @Test
    fun `进度-正常写入`() = runTest {
        val repo = FakeVideoRepository()
        SaveVideoProgressUseCase(repo)(video("a.mp4").key, 30_000L, 60_000L).getOrThrow()
        assertEquals(1, repo.savedProgress.size)
        assertEquals(30_000L, repo.savedProgress.first().positionMs)
        assertEquals(60_000L, repo.savedProgress.first().durationMs)
    }

    @Test
    fun `进度-超出时长夹取到末尾`() = runTest {
        val repo = FakeVideoRepository()
        SaveVideoProgressUseCase(repo)(video("a.mp4").key, 99_000L, 60_000L).getOrThrow()
        assertEquals(60_000L, repo.savedProgress.first().positionMs)
    }

    // ---- RandomVideo：空库失败 / 命中集合 ----
    @Test
    fun `随机-空库返回失败`() = runTest {
        val repo = FakeVideoRepository()
        assertTrue(RandomVideoUseCase(repo)(Random(1)).isFailure)
    }

    @Test
    fun `随机-命中现有视频`() = runTest {
        val repo = FakeVideoRepository()
        repo.videosFlow.value = listOf(video("a.mp4"), video("b.mp4"), video("c.mp4"))
        val key = RandomVideoUseCase(repo)(Random(7)).getOrThrow()
        assertTrue(listOf("a.mp4", "b.mp4", "c.mp4").contains(key.fileName))
    }

    // ---- AddVideoRoot：空白拒绝且不落库 ----
    @Test
    fun `添加视频根-空白失败`() = runTest {
        val repo = FakeVideoRepository()
        assertTrue(AddVideoRootUseCase(repo)("  ").isFailure)
    }

    @Test
    fun `添加视频根-非空委托仓储`() = runTest {
        val repo = FakeVideoRepository()
        val root = AddVideoRootUseCase(repo)("/storage/emulated/0/Movies").getOrThrow()
        assertEquals("Movies", root.displayName)
    }

    // ---- EnsureVideoThumbnail：成功/失败透传 ----
    @Test
    fun `缩略图-成功后返回真`() = runTest {
        val repo = FakeVideoRepository()
        assertTrue(EnsureVideoThumbnailUseCase(repo)(video("a.mp4").key))
        assertEquals(1, repo.generated.size)
    }

    @Test
    fun `缩略图-失败后返回假`() = runTest {
        val repo = FakeVideoRepository()
        repo.generateResult = Result.failure(IllegalStateException("decode"))
        assertFalse(EnsureVideoThumbnailUseCase(repo)(video("a.mp4").key))
    }

    // ---- RenameVideo：只改主名、保留扩展名 ----
    @Test
    fun `重命名-保留扩展名并委托仓储`() = runTest {
        val repo = FakeVideoRepository()
        val oldKey = video("旧名.mp4").key
        val newKey = RenameVideoUseCase(repo)(oldKey, "新名").getOrThrow()
        assertEquals("新名.mp4", newKey.fileName)
        assertEquals(listOf(oldKey to "新名.mp4"), repo.renamed)
    }

    @Test
    fun `重命名-非法名称不触及仓储`() = runTest {
        val repo = FakeVideoRepository()
        assertTrue(RenameVideoUseCase(repo)(video("a.mp4").key, "a/b").isFailure)
        assertTrue(repo.renamed.isEmpty())
    }

    @Test
    fun `重命名-名称未变化视为成功`() = runTest {
        val repo = FakeVideoRepository()
        val key = video("a.mp4").key
        assertEquals(key, RenameVideoUseCase(repo)(key, "a").getOrThrow())
        assertTrue(repo.renamed.isEmpty())
    }

    // ---- GetVideoLibrary：四流合并 ----
    @Test
    fun `视频库数据合并排序与继续观看`() = runTest {
        val repo = FakeVideoRepository()
        val prefs = FakePreferencesRepository()
        repo.videosFlow.value = listOf(video("a.mp4"), video("b.mp4"))
        prefs.videoSortFlow.value = VideoSortMode.IMPORT_TIME
        val data = GetVideoLibraryUseCase(repo, prefs)().first()
        assertEquals(2, data.videos.size)
        assertEquals(VideoSortMode.IMPORT_TIME, data.sortMode)
        assertTrue(data.roots.isEmpty())
        assertTrue(data.recent.isEmpty())
    }
}
