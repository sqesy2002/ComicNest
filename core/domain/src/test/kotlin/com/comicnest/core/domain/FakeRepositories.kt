package com.comicnest.core.domain

import com.comicnest.core.domain.repository.CollectionRepository
import com.comicnest.core.domain.repository.LibraryRepository
import com.comicnest.core.domain.repository.PreferencesRepository
import com.comicnest.core.domain.repository.ProgressRepository
import com.comicnest.core.domain.repository.VideoRepository
import com.comicnest.core.model.Collection
import com.comicnest.core.model.ComicKey
import com.comicnest.core.model.ComicPageRef
import com.comicnest.core.model.ComicSummary
import com.comicnest.core.model.LibraryRoot
import com.comicnest.core.model.ReaderMode
import com.comicnest.core.model.ReadProgress
import com.comicnest.core.model.RecentVideo
import com.comicnest.core.model.SortMode
import com.comicnest.core.model.VideoDimensions
import com.comicnest.core.model.VideoKey
import com.comicnest.core.model.VideoOrientationMode
import com.comicnest.core.model.VideoProgress
import com.comicnest.core.model.VideoRoot
import com.comicnest.core.model.VideoSortMode
import com.comicnest.core.model.VideoSummary
import com.comicnest.core.model.VideoThumbnailState
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
    val videoSortFlow = MutableStateFlow(VideoSortMode.NAME)
    val videoOrientationFlow = MutableStateFlow(VideoOrientationMode.PORTRAIT)
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

    override fun observeVideoOrientation(): Flow<VideoOrientationMode> = videoOrientationFlow
    override suspend fun setVideoOrientation(mode: VideoOrientationMode) {
        videoOrientationFlow.value = mode
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

/** 视频域假仓储：记录入参结果，供 UseCase 阈值/委托规则单测。 */
class FakeVideoRepository : VideoRepository {
    val rootsFlow = MutableStateFlow<List<VideoRoot>>(emptyList())
    val videosFlow = MutableStateFlow<List<VideoSummary>>(emptyList())
    val recentFlow = MutableStateFlow<List<RecentVideo>>(emptyList())
    val savedProgress = mutableListOf<VideoProgress>()
    val deleted = mutableListOf<VideoKey>()
    val generated = mutableListOf<VideoKey>()
    val renamed = mutableListOf<Pair<VideoKey, String>>()
    var deleteResult: Result<Unit> = Result.success(Unit)
    var generateResult: Result<Unit> = Result.success(Unit)
    var renameResult: Result<VideoKey>? = null
    var progressResult: VideoProgress? = null

    override fun observeRoots(): Flow<List<VideoRoot>> = rootsFlow

    override suspend fun addRoot(path: String): Result<VideoRoot> =
        Result.success(VideoRoot.of(path, path.substringAfterLast('/'), 0L))

    override suspend fun removeRoot(rootId: String): Result<Unit> = Result.success(Unit)

    override fun observeVideos(sort: VideoSortMode): Flow<List<VideoSummary>> = videosFlow

    override suspend fun refresh(): Result<Int> = Result.success(videosFlow.value.size)

    override suspend fun getVideo(key: VideoKey): Result<VideoSummary> =
        videosFlow.value.firstOrNull { it.key == key }?.let { Result.success(it) }
            ?: Result.failure(NoSuchElementException("no video"))

    override suspend fun generateThumbnail(key: VideoKey): Result<Unit> {
        generated += key
        return generateResult
    }

    override suspend fun saveThumbnail(key: VideoKey, path: String, durationMs: Long?): Result<Unit> =
        Result.success(Unit)

    override suspend fun markThumbnailFailed(key: VideoKey): Result<Unit> = Result.success(Unit)

    override suspend fun deleteVideo(key: VideoKey): Result<Unit> {
        deleted += key
        return deleteResult
    }

    override suspend fun readDimensions(key: VideoKey): Result<VideoDimensions> =
        Result.failure(NoSuchElementException("no dimensions"))

    override suspend fun renameVideo(key: VideoKey, newFileName: String): Result<VideoKey> {
        renamed += key to newFileName
        return renameResult ?: Result.success(VideoKey(key.rootId, newFileName))
    }

    override suspend fun saveProgress(key: VideoKey, positionMs: Long, durationMs: Long): Result<Unit> {
        savedProgress += VideoProgress(key.id, positionMs, durationMs, 0L)
        return Result.success(Unit)
    }

    override suspend fun getProgress(key: VideoKey): VideoProgress? = progressResult

    override fun observeRecent(): Flow<List<RecentVideo>> = recentFlow
}

/** 视频摘要工厂（缩略图/进度默认值）。 */
fun video(name: String, rootId: String = "vr"): VideoSummary =
    VideoSummary(
        key = VideoKey(rootId, name),
        absPath = "/v/$name",
        sizeBytes = 1L,
        lastModified = 1L,
        importedAt = 0L,
        durationMs = null,
        thumbPath = null,
        thumbState = VideoThumbnailState.PENDING,
    )
