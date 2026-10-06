package com.comicnest.core.domain.usecase

import com.comicnest.core.domain.FakeCollectionRepository
import com.comicnest.core.domain.FakeLibraryRepository
import com.comicnest.core.domain.FakePreferencesRepository
import com.comicnest.core.domain.FakeProgressRepository
import com.comicnest.core.domain.comic
import com.comicnest.core.domain.page
import com.comicnest.core.model.ComicKey
import com.comicnest.core.model.ReadProgress
import com.comicnest.core.model.SortMode
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 核心 UseCase 规则单测（对齐 D5/D6/D7，DESIGN §4）。 */
class UseCaseRulesTest {

    // ---- RandomComic：空库 failure、均匀落在已导入集合内 ----
    @Test
    fun `随机-空库返回失败`() = runTest {
        val repo = FakeLibraryRepository()
        val result = RandomComicUseCase(repo)(Random(1))
        assertTrue(result.isFailure)
    }

    @Test
    fun `随机-命中已导入漫画`() = runTest {
        val repo = FakeLibraryRepository()
        repo.comicsFlow.value = listOf(comic("A"), comic("B"), comic("C"))
        val key = RandomComicUseCase(repo)(Random(42)).getOrThrow()
        assertTrue(listOf("A", "B", "C").contains(key.rootId))
    }

    // ---- OpenComic：有/无进度起点、越界夹取 ----
    @Test
    fun `打开-无进度从第一页`() = runTest {
        val lib = FakeLibraryRepository()
        val progress = FakeProgressRepository()
        val key = ComicKey("r", "c")
        lib.pagesResult = Result.success(List(5) { page(it, key.id) })
        progress.progressToReturn = null
        val data = OpenComicUseCase(lib, progress)(key).getOrThrow()
        assertEquals(0, data.startIndex)
        assertEquals(5, data.pages.size)
    }

    @Test
    fun `打开-有进度则续读`() = runTest {
        val lib = FakeLibraryRepository()
        val progress = FakeProgressRepository()
        val key = ComicKey("r", "c")
        lib.pagesResult = Result.success(List(10) { page(it, key.id) })
        progress.progressToReturn = ReadProgress(key.id, pageIndex = 7, totalPages = 10, updatedAt = 0)
        val data = OpenComicUseCase(lib, progress)(key).getOrThrow()
        assertEquals(7, data.startIndex)
    }

    @Test
    fun `打开-进度越界夹取到末页`() = runTest {
        val lib = FakeLibraryRepository()
        val progress = FakeProgressRepository()
        val key = ComicKey("r", "c")
        lib.pagesResult = Result.success(List(4) { page(it, key.id) })
        progress.progressToReturn = ReadProgress(key.id, pageIndex = 99, totalPages = 4, updatedAt = 0)
        val data = OpenComicUseCase(lib, progress)(key).getOrThrow()
        assertEquals(3, data.startIndex)
    }

    @Test
    fun `打开-无页面返回失败`() = runTest {
        val lib = FakeLibraryRepository()
        val progress = FakeProgressRepository()
        lib.pagesResult = Result.success(emptyList())
        assertTrue(OpenComicUseCase(lib, progress)(ComicKey("r", "c")).isFailure)
    }

    // ---- SaveProgress：委托仓储并记录 ----
    @Test
    fun `保存进度-委托仓储`() = runTest {
        val progress = FakeProgressRepository()
        val key = ComicKey("r", "c")
        SaveProgressUseCase(progress)(key, 3, 12).getOrThrow()
        assertEquals(1, progress.saveCalls.size)
        assertEquals(3, progress.saveCalls.first().pageIndex)
    }

    // ---- AddLibraryRoot：空白入参 failure ----
    @Test
    fun `添加库根-空白失败`() = runTest {
        val repo = FakeLibraryRepository()
        assertTrue(AddLibraryRootUseCase(repo)("   ").isFailure)
        assertTrue(repo.addedPaths.isEmpty())
    }

    @Test
    fun `添加库根-非空委托仓储`() = runTest {
        val repo = FakeLibraryRepository()
        repo.addRootResult = Result.success(com.comicnest.core.model.LibraryRoot.of("/x", "x", 0))
        assertNotNull(AddLibraryRootUseCase(repo)("/x").getOrNull())
        assertEquals(listOf("/x"), repo.addedPaths)
    }

    // ---- Scan → Cleanup 编排（成功触发清理，失败不触发）----
    @Test
    fun `扫描成功后触发失效清理`() = runTest {
        val lib = FakeLibraryRepository()
        lib.refreshResult = Result.success(8)
        val col = FakeCollectionRepository()
        val scan = ScanLibraryUseCase(lib, CleanupInvalidCollectionsUseCase(col))
        assertEquals(8, scan().getOrThrow())
        assertEquals(1, col.cleanupCalls)
    }

    @Test
    fun `扫描失败不触发清理`() = runTest {
        val lib = FakeLibraryRepository()
        lib.refreshResult = Result.failure(RuntimeException("io"))
        val col = FakeCollectionRepository()
        val scan = ScanLibraryUseCase(lib, CleanupInvalidCollectionsUseCase(col))
        assertTrue(scan().isFailure)
        assertEquals(0, col.cleanupCalls)
    }

    // ---- CleanupInvalid：返回清理数 ----
    @Test
    fun `清理失效返回条数`() = runTest {
        val col = FakeCollectionRepository()
        col.cleanupResult = Result.success(3)
        assertEquals(3, CleanupInvalidCollectionsUseCase(col)().getOrThrow())
    }

    // ---- GetLibrary：合并排序+漫画+库根 ----
    @Test
    fun `书架数据合并三源`() = runTest {
        val lib = FakeLibraryRepository()
        val prefs = FakePreferencesRepository()
        lib.comicsFlow.value = listOf(comic("A"), comic("B"))
        prefs.sortFlow.value = SortMode.IMPORT_TIME
        val data = GetLibraryUseCase(lib, prefs)().first()
        assertEquals(2, data.comics.size)
        assertEquals(SortMode.IMPORT_TIME, data.sortMode)
    }
}
