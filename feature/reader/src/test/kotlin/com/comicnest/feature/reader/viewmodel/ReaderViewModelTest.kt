package com.comicnest.feature.reader.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.comicnest.core.domain.model.OpenComicData
import com.comicnest.core.domain.repository.CollectionRepository
import com.comicnest.core.domain.repository.PreferencesRepository
import com.comicnest.core.domain.usecase.ManageCollectionUseCase
import com.comicnest.core.domain.usecase.OpenComicUseCase
import com.comicnest.core.domain.usecase.SaveProgressUseCase
import com.comicnest.core.model.Collection
import com.comicnest.core.model.ComicKey
import com.comicnest.core.model.ComicPageRef
import com.comicnest.core.model.ReaderMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** ReaderViewModel 状态机、进度节流与模式切换单测（DESIGN §4）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private val open = mockk<OpenComicUseCase>()
    private val save = mockk<SaveProgressUseCase>()
    private val prefs = mockk<PreferencesRepository>(relaxed = true)
    private val collectionRepo = mockk<CollectionRepository>(relaxed = true)
    private val manage = mockk<ManageCollectionUseCase>(relaxed = true)

    private val key = ComicKey("r", "c") // id = "r::c"

    private fun pages(n: Int) = (0 until n).map {
        ComicPageRef(comicKey = key, index = it, fileName = "$it.png", absPath = "/x/$it.png", isAnimated = false)
    }

    private fun viewModel(): ReaderViewModel {
        every { collectionRepo.collectionsOf(any()) } returns flowOf(emptyList())
        return ReaderViewModel(
            SavedStateHandle(mapOf(ReaderViewModel.ARG_COMIC_KEY_ID to key.id)),
            open,
            save,
            prefs,
            collectionRepo,
            manage,
        )
    }

    @Test
    fun `打开成功进入 Ready 并定位 startIndex`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            every { prefs.observeReaderMode() } returns flowOf(ReaderMode.WEBTOON)
            coEvery { open(key) } returns Result.success(OpenComicData(pages(5), startIndex = 3))
            val vm = viewModel()
            advanceUntilIdle()
            val s = vm.uiState.value
            assertTrue(s is ReaderUiState.Ready)
            assertEquals(5, (s as ReaderUiState.Ready).pages.size)
            assertEquals(3, vm.currentPage.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `打开失败进入 Error`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            every { prefs.observeReaderMode() } returns flowOf(ReaderMode.WEBTOON)
            coEvery { open(key) } returns Result.failure(NoSuchElementException("该漫画没有可读页面"))
            val vm = viewModel()
            advanceUntilIdle()
            assertTrue(vm.uiState.value is ReaderUiState.Error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `翻页节流只保存最新一次进度`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            every { prefs.observeReaderMode() } returns flowOf(ReaderMode.WEBTOON)
            coEvery { open(key) } returns Result.success(OpenComicData(pages(5), startIndex = 0))
            coEvery { save(any(), any(), any()) } returns Result.success(Unit)
            val vm = viewModel()
            advanceUntilIdle()

            vm.onPageChanged(2)
            vm.onPageChanged(3)
            // 尚未到节流窗口，不应落盘
            coVerify(exactly = 0) { save(any(), any(), any()) }
            advanceTimeBy(ReaderViewModel.THROTTLE_MS + 1)
            advanceUntilIdle()

            coVerify(exactly = 1) { save(key, 3, 5) }
            assertEquals(3, vm.currentPage.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `越界页码不保存进度`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            every { prefs.observeReaderMode() } returns flowOf(ReaderMode.WEBTOON)
            coEvery { open(key) } returns Result.success(OpenComicData(pages(5), startIndex = 0))
            val vm = viewModel()
            advanceUntilIdle()

            vm.onPageChanged(99)
            advanceTimeBy(ReaderViewModel.THROTTLE_MS + 1)
            advanceUntilIdle()

            coVerify(exactly = 0) { save(any(), any(), any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `切换模式写回偏好为 PAGER`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            every { prefs.observeReaderMode() } returns flowOf(ReaderMode.WEBTOON)
            coEvery { open(key) } returns Result.success(OpenComicData(pages(5), startIndex = 0))
            val captured = slot<ReaderMode>()
            val vm = viewModel()
            advanceUntilIdle()

            vm.onToggleMode()
            advanceUntilIdle()

            coVerify(exactly = 1) { prefs.setReaderMode(capture(captured)) }
            assertEquals(ReaderMode.PAGER, captured.captured)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `♡ 收藏态随所属收藏夹非空变 true`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            every { prefs.observeReaderMode() } returns flowOf(ReaderMode.WEBTOON)
            coEvery { open(key) } returns Result.success(OpenComicData(pages(5), startIndex = 0))
            val vm = viewModel()
            // 构造后覆盖具体 key 桩（viewModel() 内的 any() 桩会先置空），再驱动 init 采集
            every { collectionRepo.collectionsOf(key) } returns flowOf(listOf(Collection(1, "神作", 0)))
            advanceUntilIdle()
            assertTrue(vm.isFavorite.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `onToggleFavorite 触发加入收藏`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            every { prefs.observeReaderMode() } returns flowOf(ReaderMode.WEBTOON)
            coEvery { open(key) } returns Result.success(OpenComicData(pages(5), startIndex = 0))
            coEvery { manage.addComic(7, key) } returns Result.success(Unit)
            val vm = viewModel()
            advanceUntilIdle()

            vm.onToggleFavorite(7, add = true)
            advanceUntilIdle()

            coVerify(exactly = 1) { manage.addComic(7, key) }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
