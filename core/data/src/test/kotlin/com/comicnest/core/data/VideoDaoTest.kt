package com.comicnest.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.comicnest.core.data.db.ComicNestDatabase
import com.comicnest.core.data.db.entity.VideoCacheEntity
import com.comicnest.core.data.db.entity.VideoProgressEntity
import com.comicnest.core.data.db.entity.VideoRootEntity
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 视频 DAO 单测（in-memory，Robolectric）：
 * 单根整表替换 / 根级联删除 / 观看记录 LRU=3（DESIGN v2 §3.5 / V8）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VideoDaoTest {

    private lateinit var db: ComicNestDatabase
    private val directExecutor: Executor = Executor { it.run() }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ComicNestDatabase::class.java)
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun cache(
        name: String,
        rootId: String = "r",
        sizeBytes: Long = 1L,
        lastModified: Long = 1L,
        importedAt: Long = 0L,
        thumbState: Int = 0,
        thumbPath: String? = null,
    ) = VideoCacheEntity(
        videoKeyId = "$rootId::$name",
        rootId = rootId,
        fileName = name,
        absPath = "/storage/$rootId/$name",
        sizeBytes = sizeBytes,
        lastModified = lastModified,
        importedAt = importedAt,
        durationMs = null,
        thumbPath = thumbPath,
        thumbState = thumbState,
    )

    private fun progress(keyId: String, updatedAt: Long) =
        VideoProgressEntity(keyId, positionMs = 20_000L, durationMs = 60_000L, updatedAt = updatedAt)

    @Test
    fun `replaceRoot 整体替换单根数据`() = runTest {
        val dao = db.videoDao()
        dao.insertAll(listOf(cache("old.mp4"), cache("other.mp4", rootId = "r2")))

        dao.replaceRoot("r", listOf(cache("new.mp4"), cache("new2.mp4")))

        assertEquals(2, dao.getByRoot("r").size)
        assertNull(dao.getById("r::old.mp4"))
        assertEquals(1, dao.getByRoot("r2").size)
    }

    @Test
    fun `deleteByRoot 只清理目标根`() = runTest {
        val dao = db.videoDao()
        dao.insertAll(listOf(cache("a.mp4"), cache("b.mp4", rootId = "r2")))

        dao.deleteByRoot("r")

        assertEquals(0, dao.getByRoot("r").size)
        assertEquals(1, dao.getByRoot("r2").size)
    }

    @Test
    fun `updateThumb 回写缩略图与时长`() = runTest {
        val dao = db.videoDao()
        dao.insertAll(listOf(cache("a.mp4")))

        dao.updateThumb(
            keyId = "r::a.mp4",
            thumbPath = "/thumbs/a.jpg",
            thumbState = 1,
            durationMs = 90_000L,
            lastModified = 777L,
        )

        val row = dao.getById("r::a.mp4")
        assertNotNull(row)
        assertEquals("/thumbs/a.jpg", row!!.thumbPath)
        assertEquals(1, row.thumbState)
        assertEquals(90_000L, row.durationMs)
        assertEquals(777L, row.lastModified)
    }

    @Test
    fun `观察列表按导入时间倒序`() = runTest {
        val dao = db.videoDao()
        dao.insertAll(
            listOf(
                cache("a.mp4", importedAt = 10L),
                cache("b.mp4", importedAt = 30L),
                cache("c.mp4", importedAt = 20L),
            ),
        )

        val names = dao.observeAllByImportedDesc().first().map { it.fileName }
        assertEquals(listOf("b.mp4", "c.mp4", "a.mp4"), names)
    }

    @Test
    fun `观看记录保留最近三条`() = runTest {
        val dao = db.videoProgressDao()
        dao.saveAndEvict(progress("p1", 1_000L), keep = 3)
        dao.saveAndEvict(progress("p2", 2_000L), keep = 3)
        dao.saveAndEvict(progress("p3", 3_000L), keep = 3)
        dao.saveAndEvict(progress("p4", 4_000L), keep = 3)

        assertEquals(3, dao.count())
        assertNull(dao.getById("p1"))
        val recent = dao.observeRecent(3).first().map { it.videoKeyId }
        assertEquals(listOf("p4", "p3", "p2"), recent)
    }

    @Test
    fun `重复观看同一视频不会新增行`() = runTest {
        val dao = db.videoProgressDao()
        dao.saveAndEvict(progress("p1", 1_000L), keep = 3)
        dao.saveAndEvict(progress("p1", 5_000L), keep = 3)

        assertEquals(1, dao.count())
        assertEquals(5_000L, dao.getById("p1")!!.updatedAt)
    }

    @Test
    fun `移除根时级联清理观看记录`() = runTest {
        db.videoDao().insertAll(listOf(cache("a.mp4"), cache("b.mp4", rootId = "r2")))
        val progressDao = db.videoProgressDao()
        progressDao.saveAndEvict(progress("r::a.mp4", 1_000L), keep = 3)
        progressDao.saveAndEvict(progress("r2::b.mp4", 2_000L), keep = 3)

        progressDao.deleteByRoot("r")

        assertEquals(1, progressDao.count())
        assertNull(progressDao.getById("r::a.mp4"))
        assertNotNull(progressDao.getById("r2::b.mp4"))
    }

    @Test
    fun `重命名迁移缓存主键与观看记录`() = runTest {
        val videoDao = db.videoDao()
        val progressDao = db.videoProgressDao()
        videoDao.insertAll(listOf(cache("old.mp4")))
        progressDao.saveAndEvict(progress("r::old.mp4", 1_000L), keep = 3)

        videoDao.renameCache("r::old.mp4", cache("new.mp4"))

        assertNull(videoDao.getById("r::old.mp4"))
        assertNotNull(videoDao.getById("r::new.mp4"))
        assertNull(progressDao.getById("r::old.mp4"))
        assertEquals(20_000L, progressDao.getById("r::new.mp4")?.positionMs)
    }

    @Test
    fun `视频根同路径不重复导入`() = runTest {
        val rootDao = db.videoRootDao()
        val path = "/storage/emulated/0/Movies"
        rootDao.insert(VideoRootEntity("id1", path, "Movies", 1L))
        val second = rootDao.insert(VideoRootEntity("id2", path, "Movies2", 2L))

        assertEquals(-1L, second)
        assertEquals("id1", rootDao.getByPath(path)?.id)
        assertEquals(1, rootDao.getAllOnce().size)
    }
}
