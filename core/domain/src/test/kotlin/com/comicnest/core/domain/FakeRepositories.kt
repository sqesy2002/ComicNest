package com.comicnest.core.domain

import com.comicnest.core.domain.repository.CollectionRepository
import com.comicnest.core.domain.repository.LibraryRepository
import com.comicnest.core.domain.repository.PreferencesRepository
import com.comicnest.core.domain.repository.ProgressRepository
import com.comicnest.core.model.Collection
import com.comicnest.core.model.ComicKey
import com.comicnest.core.model.ComicPageRef
import com.comicnest.core.model.ComicSummary
import com.comicnest.core.model.LibraryRoot
import com.comicnest.core.model.ReaderMode
import com.comicnest.core.model.ReadProgress
import com.comicnest.core.model.SortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

fun comic(key: String, name: String = key): ComicSummary =
    ComicSummary(
        key = ComicKey(rootId = key, comicName = name),
        coverPath = null,
        imageCount = 1,
        importedAt = 0L,
        lastModified = 0L,
    )

fun page(index: Int, keyId: String): ComicPageRef =
    ComicPageRef(
        comicKey = ComicKey.fromId(keyId),
        index = index,
        fileName = "p$index",
        absPath = "/x/p$index.png",
        isAnimated = false,
    )

class FakeLibraryRepository : LibraryRepository {
    val rootsFlow = MutableStateFlow<List<LibraryRoot>>(emptyList())
    val comicsFlow = MutableStateFlow<List<ComicSummary>>(emptyList())
    var pagesResult: Result<List<ComicPageRef>> = Result.success(emptyList())
    var refreshResult: Result<Int> = Result.success(0)
    var addRootResult: Result<LibraryRoot> = Result.failure(IllegalStateException("unset"))
    val addedPaths = mutableListOf<String>()
    var refreshCalls = 0

    override fun observeRoots(): Flow<List<LibraryRoot>> = rootsFlow
    override suspend fun addRoot(path: String): Result<LibraryRoot> {
        addedPaths += path
        return addRootResult
    }

    override suspend fun removeRoot(rootId: String): Result<Unit> = Result.success(Unit)
    override fun observeComics(sort: SortMode): Flow<List<ComicSummary>> = comicsFlow
    override suspend fun refresh(): Result<Int> {
        refreshCalls++
        return refreshResult
    }

    override suspend fun getComic(key: ComicKey): Result<ComicSummary> =
        comicsFlow.value.firstOrNull { it.key == key }?.let { Result.success(it) }
            ?: Result.failure(NoSuchElementException("no comic"))

    override suspend fun getPages(key: ComicKey): Result<List<ComicPageRef>> = pagesResult
}

class FakeProgressRepository : ProgressRepository {
    val recentFlow = MutableStateFlow<List<ReadProgress>>(emptyList())
    val stored = mutableMapOf<String, ReadProgress>()
    var progressToReturn: ReadProgress? = null
    val saveCalls = mutableListOf<ReadProgress>()

    override suspend fun getProgress(key: ComicKey): ReadProgress? = progressToReturn

    override suspend fun saveProgress(key: ComicKey, pageIndex: Int, total: Int): Result<Unit> {
        val p = ReadProgress(key.id, pageIndex, total, System.currentTimeMillis())
        saveCalls += p
        stored[key.id] = p
        return Result.success(Unit)
    }

    override fun observeRecent(): Flow<List<ReadProgress>> = recentFlow
}

class FakePreferencesRepository : PreferencesRepository {
    val sortFlow = MutableStateFlow(SortMode.NAME)
    val readerFlow = MutableStateFlow(ReaderMode.WEBTOON)
    override fun observeSortMode(): Flow<SortMode> = sortFlow
    override suspend fun setSortMode(mode: SortMode) {
        sortFlow.value = mode
    }

    override fun observeReaderMode(): Flow<ReaderMode> = readerFlow
    override suspend fun setReaderMode(mode: ReaderMode) {
        readerFlow.value = mode
    }
}

class FakeCollectionRepository : CollectionRepository {
    val collectionsFlow = MutableStateFlow<List<Collection>>(emptyList())
    val entriesFlow = MutableStateFlow<List<ComicSummary>>(emptyList())
    var createResult: Result<Collection> = Result.success(Collection(1, "c", 0))
    var cleanupResult: Result<Int> = Result.success(0)
    var cleanupCalls = 0
    val added = mutableListOf<Pair<Long, String>>()
    val removed = mutableListOf<Pair<Long, String>>()

    override fun observeCollections(): Flow<List<Collection>> = collectionsFlow
    override suspend fun createCollection(name: String): Result<Collection> = createResult
    override suspend fun renameCollection(id: Long, name: String): Result<Unit> = Result.success(Unit)
    override suspend fun deleteCollection(id: Long): Result<Unit> = Result.success(Unit)
    override fun observeEntries(collectionId: Long): Flow<List<ComicSummary>> = entriesFlow
    override suspend fun addComic(collectionId: Long, key: ComicKey): Result<Unit> {
        added += collectionId to key.id
        return Result.success(Unit)
    }

    override suspend fun removeComic(collectionId: Long, key: ComicKey): Result<Unit> {
        removed += collectionId to key.id
        return Result.success(Unit)
    }

    override fun collectionsOf(key: ComicKey): Flow<List<Collection>> = collectionsFlow
    override suspend fun cleanupInvalid(): Result<Int> {
        cleanupCalls++
        return cleanupResult
    }
}
