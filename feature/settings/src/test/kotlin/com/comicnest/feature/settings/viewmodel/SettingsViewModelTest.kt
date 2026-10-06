package com.comicnest.feature.settings.viewmodel

import app.cash.turbine.test
import com.comicnest.core.domain.repository.LibraryRepository
import com.comicnest.core.domain.repository.PreferencesRepository
import com.comicnest.core.domain.usecase.AddLibraryRootUseCase
import com.comicnest.core.domain.usecase.ScanLibraryUseCase
import com.comicnest.core.model.LibraryRoot
import com.comicnest.core.model.ReaderMode
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
import org.junit.Before
import org.junit.Test

/** SettingsViewModel 状态合成与库根增删、模式读写单测（DESIGN §4）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val libRepo = mockk<LibraryRepository>(relaxed = true)
    private val prefs = mockk<PreferencesRepository>(relaxed = true)
    private val addRoot = mockk<AddLibraryRootUseCase>()
    private val scan = mockk<ScanLibraryUseCase>(relaxed = true)

    private val root = LibraryRoot.of("/x", "目录X", 0)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { libRepo.observeRoots() } returns flowOf(listOf(root))
        every { prefs.observeReaderMode() } returns flowOf(ReaderMode.WEBTOON)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SettingsViewModel(libRepo, prefs, addRoot, scan)

    @Test
    fun `合成库根与阅读模式状态`() = runTest {
        every { prefs.observeReaderMode() } returns flowOf(ReaderMode.PAGER)
        viewModel().uiState.test {
            // stateIn(WhileSubscribed) 在 Unconfined 下会与初值合流，等到库根就绪再断言
            var s = awaitItem()
            while (s.roots.isEmpty()) s = awaitItem()
            assertEquals(1, s.roots.size)
            assertEquals(ReaderMode.PAGER, s.readerMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `添加库根成功后触发重扫`() = runTest {
        coEvery { addRoot("/x") } returns Result.success(root)
        viewModel().onAddRoot("/x")
        coVerify(exactly = 1) { addRoot("/x") }
        coVerify(exactly = 1) { scan() }
    }

    @Test
    fun `添加库根失败不触发重扫`() = runTest {
        coEvery { addRoot("/bad") } returns Result.failure(IllegalStateException("路径无效"))
        viewModel().onAddRoot("/bad")
        coVerify(exactly = 0) { scan() }
    }

    @Test
    fun `移除库根调用仓储 removeRoot`() = runTest {
        coEvery { libRepo.removeRoot(root.id) } returns Result.success(Unit)
        viewModel().onRemoveRoot(root)
        coVerify(exactly = 1) { libRepo.removeRoot(root.id) }
    }

    @Test
    fun `改变默认阅读模式写入偏好`() = runTest {
        viewModel().onReaderModeChange(ReaderMode.PAGER)
        coVerify(exactly = 1) { prefs.setReaderMode(ReaderMode.PAGER) }
    }
}
