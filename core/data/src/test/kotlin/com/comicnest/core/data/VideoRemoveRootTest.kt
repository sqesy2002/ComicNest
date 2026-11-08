package com.comicnest.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.comicnest.core.data.db.ComicNestDatabase
import com.comicnest.core.data.db.entity.VideoCacheEntity
import com.comicnest.core.data.db.entity.VideoProgressEntity
import com.comicnest.core.data.db.entity.VideoRootEntity
import com.comicnest.core.data.repository.VideoRepositoryImpl
import com.comicnest.core.model.VideoKey
import com.comicnest.core.model.VideoRoot
import com.comicnest.core.model.VideoSummary
import com.comicnest.core.model.VideoThumbnailState
import com.comicnest.source.video.ThumbnailResult
import com.comicnest.source.video.VideoMeta
import com.comicnest.source.video.VideoSource
import java.util.concurrent.Executor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 移除视频库根的单测（in-memory Room + 记录型 [VideoSource]）：
 * 该根下视频的缩略图文件（中间帧）、观看记录、缓存行与根记录必须一并清理，
 * 避免留下无人引用的孤儿缩略图。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VideoRemoveRootTest {

    private lateinit var db: ComicNestDatabase
    private val directExecutor: Executor = Executor { it.run() }
    private val source = RecordingVideoSource()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ComicNestDatabase::class.java)
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            db.videoRootDao().insert(VideoRootEntity("r", "/storage/movies", "movies", 0L))
            db.videoRootDao().insert(VideoRootEntity("r2", "/storage/other", "other", 0L))
            db.videoDao().insertAll(
                listOf(
                    cache("a.mp4", rootId = "r", thumbPath = "/thumbs/a.jpg"),
                    cache("b.mp4", rootId = "r", thumbPath = null),
                    cache("c.mp4", rootId = "r2", thumbPath = "/thumbs/c.jpg"),
                ),
            )
            db.videoProgressDao().upsert(VideoProgressEntity("r::a.mp4", 1_000L, 5_000L, 1L))
            db.videoProgressDao().upsert(VideoProgressEntity("r2::c.mp4", 2_000L, 6_000L, 2L))
        }
    }

    @After
    fun tearDown() = db.close()

    private fun cache(name: String, rootId: String, thumbPath: String?) = VideoCacheEntity(
        videoKeyId = "$rootId::$name",
        rootId = rootId,
        fileName = name,
        absPath = "/storage/$rootId/$name",
        sizeBytes = 1L,
        lastModified = 1L,
        importedAt = 0L,
        durationMs = null,
        thumbPath = thumbPath,
        thumbState = if (thumbPath != null) {
            VideoThumbnailState.READY.ordinal
        } else {
            VideoThumbnailState.PENDING.ordinal
        },
    )

    @Test
    fun `移除目录时同时删除缩略图、观看记录与缓存`() = runTest {
        val repo = VideoRepositoryImpl(source, db.videoRootDao(), db.videoDao(), db.videoProgressDao())

        val result = repo.removeRoot("r")

        assertTrue(result.isSuccess)
        // 该根下所有视频的缩略图路径都交给 VideoSource 删除（无图条目传 null，幂等）
        assertEquals(listOf("/thumbs/a.jpg", null), source.deletedThumbs)
        assertNull(db.videoDao().getById("r::a.mp4"))
        assertNull(db.videoDao().getById("r::b.mp4"))
        assertNull(db.videoProgressDao().getById("r::a.mp4"))
        assertNull(db.videoRootDao().getById("r"))
        // 其它根不受影响
        assertEquals(1, db.videoDao().getByRoot("r2").size)
        assertEquals(2_000L, db.videoProgressDao().getById("r2::c.mp4")?.positionMs)
        assertEquals("/thumbs/c.jpg", db.videoDao().getById("r2::c.mp4")?.thumbPath)
    }

    /** 只记录删除动作，其余能力在移除根时不会用到。 */
    private class RecordingVideoSource : VideoSource {
        val deletedThumbs = mutableListOf<String?>()

        override val id: String = "fake"
        override val label: String = "fake"

        override suspend fun scanLibrary(root: VideoRoot): Result<List<VideoSummary>> =
            Result.success(emptyList())

        override suspend fun readMeta(absPath: String): Result<VideoMeta> =
            Result.failure(UnsupportedOperationException())

        override suspend fun ensureThumbnail(
            key: VideoKey,
            absPath: String,
            sizeBytes: Long,
            lastModified: Long,
        ): Result<ThumbnailResult> = Result.failure(UnsupportedOperationException())

        override fun deleteThumbnail(path: String?) {
            deletedThumbs += path
        }

        override suspend fun renameFile(oldPath: String, newPath: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override fun moveThumbnail(
            oldPath: String?,
            newKey: VideoKey,
            sizeBytes: Long,
            lastModified: Long,
        ): String? = null

        override fun cleanupThumbnails(validPaths: Collection<String>) = Unit

        override suspend fun purgeMediaStore(absPath: String) = Unit
    }
}
