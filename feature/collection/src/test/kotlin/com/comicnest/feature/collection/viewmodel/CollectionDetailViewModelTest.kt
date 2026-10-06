package com.comicnest.feature.collection.viewmodel

import app.cash.turbine.test
import androidx.lifecycle.SavedStateHandle
import com.comicnest.core.domain.repository.CollectionRepository
import com.comicnest.core.domain.usecase.ManageCollectionUseCase
import com.comicnest.core.model.Collection
import com.comicnest.core.model.ComicKey
import com.comicnest.core.model.ComicSummary
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CollectionDetailViewModel 状态合成与移除/删除单测（DESIGN §4）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionDetailViewModelTest {

    private val repo = mockk<CollectionRepository>(relaxed = true)
    private val manage = mockk<ManageCollectionUseCase>(relaxed = true)

    private val key = ComicKey("r", "c")

    private val summary = ComicSummary(key, null, 1, 0, 0)

    private fun viewModel(id: Long = 5L) =
        CollectionDetailViewModel(SavedStateHandle(mapOf(CollectionDetailViewModel.ARG_ID to id)), repo, manage)

    @Test
    fun `合成名称与漫画为 Ready`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            every { repo.observeCollections() } returns flowOf(listOf(Collection(5, "我的夹", 0)))
            every { repo.observeEntries(5) } returns flowOf(listOf(summary))
            val vm = viewModel()
            advanceUntilIdle()
            val s = vm.uiState.value
            assertTrue(s is CollectionDetailUiState.Ready)
            assertEquals("我的夹", (s as CollectionDetailUiState.Ready).name)
            assertEquals(1, s.comics.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `有收藏夹但无漫画为 Empty`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            every { repo.observeCollections() } returns flowOf(listOf(Collection(5, "我的夹", 0)))
            every { repo.observeEntries(5) } returns flowOf(emptyList())
            val vm = viewModel()
            advanceUntilIdle()
            assertEquals(CollectionDetailUiState.Empty, vm.uiState.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `收藏夹不存在为 NotFound`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            every { repo.observeCollections() } returns flowOf(listOf(Collection(9, "别的", 0)))
            every { repo.observeEntries(5) } returns flowOf(emptyList())
            val vm = viewModel()
            advanceUntilIdle()
            assertEquals(CollectionDetailUiState.NotFound, vm.uiState.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `移除漫画调用 removeComic`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            every { repo.observeCollections() } returns flowOf(listOf(Collection(5, "我的夹", 0)))
            every { repo.observeEntries(5) } returns flowOf(listOf(summary))
            coEvery { manage.removeComic(5, key) } returns Result.success(Unit)
            val vm = viewModel()
            advanceUntilIdle()
            vm.onRemoveComic(key)
            advanceUntilIdle()
            coVerify(exactly = 1) { manage.removeComic(5, key) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `删除收藏夹成功后发出返回事件`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            every { repo.observeCollections() } returns flowOf(listOf(Collection(5, "我的夹", 0)))
            every { repo.observeEntries(5) } returns flowOf(listOf(summary))
            coEvery { manage.delete(5) } returns Result.success(Unit)
            val vm = viewModel()
            advanceUntilIdle()
            vm.onDelete()
            advanceUntilIdle()
            vm.events.test {
                assertTrue(awaitItem() is CollectionDetailEvent.NavigateBack)
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
