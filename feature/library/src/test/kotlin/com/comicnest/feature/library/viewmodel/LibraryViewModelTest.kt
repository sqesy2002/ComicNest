package com.comicnest.feature.library.viewmodel

import app.cash.turbine.test
import com.comicnest.core.domain.model.LibraryUiData
import com.comicnest.core.domain.repository.CollectionRepository
import com.comicnest.core.domain.repository.PreferencesRepository
import com.comicnest.core.domain.usecase.AddLibraryRootUseCase
import com.comicnest.core.domain.usecase.GetLibraryUseCase
import com.comicnest.core.domain.usecase.ManageCollectionUseCase
import com.comicnest.core.domain.usecase.RandomComicUseCase
import com.comicnest.core.domain.usecase.ScanLibraryUseCase
import com.comicnest.core.model.Collection
import com.comicnest.core.model.ComicKey
import com.comicnest.core.model.ComicSummary
import com.comicnest.core.model.LibraryRoot
import com.comicnest.core.model.SortMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** LibraryViewModel 状态机与事件映射单测（DESIGN §4）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val getLibrary = mockk<GetLibraryUseCase>()
    private val scan = mockk<ScanLibraryUseCase>()
    private val addRoot = mockk<AddLibraryRootUseCase>()
    private val random = mockk<RandomComicUseCase>()
    private val prefs = mockk<PreferencesRepository>(relaxed = true)
    private val collectionRepo = mockk<CollectionRepository>(relaxed = true)
    private val manage = mockk<ManageCollectionUseCase>(relaxed = true)

    private fun summary(id: String) = ComicSummary(
        key = ComicKey(id, id),
        coverPath = null,
        imageCount = 1,
        importedAt = 0,
        lastModified = 0,
    )

    private fun viewModel() = LibraryViewModel(getLibrary, scan, addRoot, random, prefs, collectionRepo, manage)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `首进无库根为 Empty 引导态`() = runTest {
        every { getLibrary() } returns flowOf(LibraryUiData(emptyList(), SortMode.NAME, emptyList()))
        val vm = viewModel()
        vm.uiState.test {
            val s = awaitItem()
            assertTrue(s is LibraryUiState.Empty && s.firstRun)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `有漫画为 Ready`() = runTest {
        val comics = listOf(summary("A"), summary("B"))
        val roots = listOf(LibraryRoot.of("/x", "x", 0))
        every { getLibrary() } returns flowOf(LibraryUiData(comics, SortMode.NAME, roots))
        val vm = viewModel()
        vm.uiState.test {
            val s = awaitItem()
            assertTrue(s is LibraryUiState.Ready)
            assertEquals(2, (s as LibraryUiState.Ready).comics.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `随机成功发出导航事件`() = runTest {
        every { getLibrary() } returns flowOf(LibraryUiData(emptyList(), SortMode.NAME, emptyList()))
        coEvery { random() } returns Result.success(ComicKey("r", "c"))
        val vm = viewModel()
        vm.events.test {
            vm.onRandom()
            val e = awaitItem()
            assertTrue(e is LibraryEvent.NavigateToReader)
            assertEquals("r::c", (e as LibraryEvent.NavigateToReader).comicKeyId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `随机空库发出提示事件`() = runTest {
        every { getLibrary() } returns flowOf(LibraryUiData(emptyList(), SortMode.NAME, emptyList()))
        coEvery { random() } returns Result.failure(NoSuchElementException("书库为空，请先导入漫画"))
        val vm = viewModel()
        vm.events.test {
            vm.onRandom()
            val e = awaitItem()
            assertTrue(e is LibraryEvent.Message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `添加库根成功触发扫描`() = runTest {
        every { getLibrary() } returns flowOf(LibraryUiData(emptyList(), SortMode.NAME, emptyList()))
        coEvery { addRoot("/x") } returns Result.success(LibraryRoot.of("/x", "x", 0))
        coEvery { scan() } returns Result.success(3)
        val vm = viewModel()
        vm.events.test {
            vm.onAddRoot("/x")
            // 先"已添加"，后"扫描完成"
            assertEquals(true, (awaitItem() as LibraryEvent.Message).text.contains("已添加"))
            assertEquals(true, (awaitItem() as LibraryEvent.Message).text.contains("扫描完成"))
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { scan() }
    }

    @Test
    fun `添加库根失败仅提示不扫描`() = runTest {
        every { getLibrary() } returns flowOf(LibraryUiData(emptyList(), SortMode.NAME, emptyList()))
        coEvery { addRoot("/bad") } returns Result.failure(IllegalStateException("路径无效"))
        val vm = viewModel()
        vm.events.test {
            vm.onAddRoot("/bad")
            assertTrue((awaitItem() as LibraryEvent.Message).text.contains("添加失败"))
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { scan() }
    }

    @Test
    fun `长按漫画加载收藏选择态`() = runTest {
        every { getLibrary() } returns flowOf(LibraryUiData(emptyList(), SortMode.NAME, emptyList()))
        every { collectionRepo.observeCollections() } returns flowOf(listOf(Collection(1, "追更", 0)))
        every { collectionRepo.collectionsOf(any()) } returns flowOf(emptyList())
        val vm = viewModel()
        vm.onLongPressComic(ComicKey("r", "c"))
        vm.favorites.test {
            val s = awaitItem()
            assertNotNull(s)
            assertEquals(1, s!!.collections.size)
            assertTrue(s.selected.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `勾选收藏夹触发加入`() = runTest {
        every { getLibrary() } returns flowOf(LibraryUiData(emptyList(), SortMode.NAME, emptyList()))
        val key = ComicKey("r", "c")
        every { collectionRepo.observeCollections() } returns flowOf(listOf(Collection(7, "神作", 0)))
        every { collectionRepo.collectionsOf(key) } returns flowOf(emptyList())
        coEvery { manage.addComic(7, key) } returns Result.success(Unit)
        val vm = viewModel()
        vm.onLongPressComic(key)
        vm.onToggleFavorite(7, add = true)
        coVerify(exactly = 1) { manage.addComic(7, key) }
    }
}
