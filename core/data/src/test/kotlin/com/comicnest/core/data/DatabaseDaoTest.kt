package com.comicnest.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.comicnest.core.data.db.ComicNestDatabase
import com.comicnest.core.data.db.entity.CollectionEntity
import com.comicnest.core.data.db.entity.CollectionEntryEntity
import com.comicnest.core.data.db.entity.ComicCacheEntity
import com.comicnest.core.data.db.entity.ReadProgressEntity
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.first
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

/** Room DAO 单测（in-memory，Robolectric）：LRU=5 淘汰 / 多对多+级联 / replaceAll 原子性（DESIGN §4）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DatabaseDaoTest {

    private lateinit var db: ComicNestDatabase

    /** 直接执行器：使 Room 查询同步跑在测试线程，避免 Robolectric 下的线程调度问题。 */
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

    private fun comic(keyId: String, rootId: String = "r", name: String = keyId) =
        ComicCacheEntity(
            comicKeyId = keyId,
            rootId = rootId,
            comicName = name,
            coverPath = null,
            imageCount = 1,
            importedAt = 0L,
            lastModified = 0L,
        )

    // ---- ProgressDao：LRU=5 淘汰 ----
    @Test
    fun `保存第6条进度后仅保留最近5条`() = runTest {
        val dao = db.progressDao()
        // 依次写入 6 条，updatedAt 递增
        repeat(6) { i ->
            dao.saveAndEvict(
                ReadProgressEntity(
                    comicKeyId = "k$i",
                    pageIndex = i,
                    totalPages = 10,
                    updatedAt = 1_000L + i,
                ),
                keep = 5,
            )
        }
        assertEquals(5, dao.count())
        // 最早写入的 k0 应被淘汰
        assertNull(dao.getProgress("k0"))
        // 最近 5 条 k1..k5 保留
        for (i in 1..5) {
            assertTrue("应保留 k$i", dao.getProgress("k$i") != null)
        }
    }

    @Test
    fun `重复保存同一漫画不增长行数`() = runTest {
        val dao = db.progressDao()
        repeat(3) { i ->
            dao.saveAndEvict(ReadProgressEntity("same", i, 10, 2_000L + i), keep = 5)
        }
        assertEquals(1, dao.count())
        assertEquals(2, dao.getProgress("same")?.pageIndex)
    }

    // ---- CollectionDao：多对多 + 级联删除 ----
    @Test
    fun `一部漫画可加入多个收藏夹`() = runTest {
        val col = db.collectionDao()
        val idA = col.insertCollection(CollectionEntity(0, "A", 1))
        val idB = col.insertCollection(CollectionEntity(0, "B", 2))
        db.comicDao().insertAll(listOf(comic("m1")))
        col.insertEntry(CollectionEntryEntity(idA, "m1"))
        col.insertEntry(CollectionEntryEntity(idB, "m1"))

        val owners = col.observeCollectionsOfComic("m1").first()
        assertEquals(2, owners.size)
    }

    @Test
    fun `删除收藏夹级联清理其关联项`() = runTest {
        val col = db.collectionDao()
        val idA = col.insertCollection(CollectionEntity(0, "A", 1))
        db.comicDao().insertAll(listOf(comic("m1")))
        col.insertEntry(CollectionEntryEntity(idA, "m1"))

        col.deleteCollectionById(idA)

        assertTrue(col.getAllEntryKeys().isEmpty())
    }

    @Test
    fun `重复加入同一收藏夹幂等`() = runTest {
        val col = db.collectionDao()
        val idA = col.insertCollection(CollectionEntity(0, "A", 1))
        db.comicDao().insertAll(listOf(comic("m1")))
        col.insertEntry(CollectionEntryEntity(idA, "m1"))
        col.insertEntry(CollectionEntryEntity(idA, "m1")) // IGNORE
        assertEquals(1, col.getAllEntryKeys().size)
    }

    // ---- 失效清理：指向不存在漫画的 entry 被删除 ----
    @Test
    fun `清理失效收藏仅删除指向不存在漫画的项`() = runTest {
        val col = db.collectionDao()
        val comicDao = db.comicDao()
        val idA = col.insertCollection(CollectionEntity(0, "A", 1))
        comicDao.insertAll(listOf(comic("valid")))
        col.insertEntry(CollectionEntryEntity(idA, "valid"))
        col.insertEntry(CollectionEntryEntity(idA, "gone")) // 缓存中不存在

        // 模拟 data 层 cleanupInvalid 逻辑
        val valid = comicDao.getAllKeys().toSet()
        val invalid = col.getAllEntryKeys().filterNot { it in valid }
        val removed = col.deleteEntriesByKeys(invalid)

        assertEquals(1, removed)
        assertEquals(listOf("valid"), col.getAllEntryKeys())
    }

    // ---- ComicDao：replaceAll 原子性 ----
    @Test
    fun `replaceAll 整表重建旧数据不残留`() = runTest {
        val dao = db.comicDao()
        dao.replaceAll(listOf(comic("old1"), comic("old2")))
        assertEquals(2, dao.observeByName().first().size)

        dao.replaceAll(listOf(comic("new1")))
        val after = dao.observeByName().first()
        assertEquals(1, after.size)
        assertEquals("new1", after.first().comicKeyId)
        assertNull(dao.getById("old1"))
    }
}
