package com.comicnest.feature.collection.viewmodel

import app.cash.turbine.test
import com.comicnest.core.domain.repository.CollectionRepository
import com.comicnest.core.domain.usecase.CleanupInvalidCollectionsUseCase
import com.comicnest.core.domain.usecase.ManageCollectionUseCase
import com.comicnest.core.model.Collection
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

/** CollectionListViewModel 状态机、失效清理提示与 CRUD 单测（DESIGN §4）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionListViewModelTest {

    private val repo = mockk<CollectionRepository>(relaxed = true)
    private val manage = mockk<ManageCollectionUseCase>(relaxed = true)
    private val cleanup = mockk<CleanupInvalidCollectionsUseCase>(relaxed = true)

    private fun viewModel() = CollectionListViewModel(repo, manage, cleanup)

    @Test
    fun `观察列表映射 Ready`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            every { repo.observeCollections() } returns flowOf(listOf(Collection(1, "追更", 0)))
            coEvery { cleanup() } returns Result.success(0)
            val vm = viewModel()
            advanceUntilIdle()
            assertTrue(vm.uiState.value is CollectionListUiState.Ready)
            assertEquals(1, (vm.uiState.value as CollectionListUiState.Ready).collections.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `空列表为 Empty`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            every { repo.observeCollections() } returns flowOf(emptyList())
            coEvery { cleanup() } returns Result.success(0)
            val vm = viewModel()
            advanceUntilIdle()
            assertEquals(CollectionListUiState.Empty, vm.uiState.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `进入触发清理并在清理数大于零时提示`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            every { repo.observeCollections() } returns flowOf(emptyList())
            coEvery { cleanup() } returns Result.success(3)
            val vm = viewModel()
            advanceUntilIdle()
            vm.events.test {
                val e = awaitItem()
                assertTrue((e as CollectionListEvent.Message).text.contains("已清理 3"))
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `新建收藏夹调用 create 并去空白`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            every { repo.observeCollections() } returns flowOf(emptyList())
            coEvery { cleanup() } returns Result.success(0)
            coEvery { manage.create("神作") } returns Result.success(Collection(2, "神作", 0))
            val vm = viewModel()
            advanceUntilIdle()
            vm.onCreate("  神作 ")
            advanceUntilIdle()
            coVerify(exactly = 1) { manage.create("神作") }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `空名称不创建`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            every { repo.observeCollections() } returns flowOf(emptyList())
            coEvery { cleanup() } returns Result.success(0)
            val vm = viewModel()
            advanceUntilIdle()
            vm.onCreate("   ")
            advanceUntilIdle()
            coVerify(exactly = 0) { manage.create(any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
