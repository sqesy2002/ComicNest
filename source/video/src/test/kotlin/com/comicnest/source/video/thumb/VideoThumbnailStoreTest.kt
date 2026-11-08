package com.comicnest.source.video.thumb

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 缩略图缓存单测（DESIGN v2 §3.6）：键随 size/mtime 变化、原子写、孤儿清理、容量控制。 */
class VideoThumbnailStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store(): VideoThumbnailStore = VideoThumbnailStore(temp.newFolder("thumbs"))

    @Test
    fun `缓存键随 size 或 mtime 变化`() {
        val store = store()
        val a = store.pathFor("root::a.mp4", 10L, 100L)
        val same = store.pathFor("root::a.mp4", 10L, 100L)
        val newSize = store.pathFor("root::a.mp4", 11L, 100L)
        val newMtime = store.pathFor("root::a.mp4", 10L, 101L)

        assertEquals(a.name, same.name)
        assertNotEquals(a.name, newSize.name)
        assertNotEquals(a.name, newMtime.name)
        assertTrue(a.name.endsWith(".jpg"))
    }

    @Test
    fun `写入后文件存在且内容一致`() {
        val store = store()
        val target = store.pathFor("k", 1L, 1L)
        val bytes = byteArrayOf(1, 2, 3, 4)
        store.write(target, bytes)

        assertTrue(target.isFile)
        assertTrue(target.readBytes().contentEquals(bytes))
        assertFalse(File(store.directory, target.name + ".tmp").exists())
    }

    @Test
    fun `删除幂等且忽略空路径`() {
        val store = store()
        val target = store.write(store.pathFor("k", 1L, 1L), byteArrayOf(9))
        store.delete(target.absolutePath)
        store.delete(target.absolutePath)
        store.delete(null)
        store.delete("  ")
        assertFalse(target.exists())
    }

    @Test
    fun `孤儿清理保留有效项与新鲜文件`() {
        val store = store()
        val keep = store.write(store.pathFor("keep", 1L, 1L), byteArrayOf(1))
        val orphan = store.write(store.pathFor("orphan", 1L, 1L), byteArrayOf(1))
        val fresh = store.write(store.pathFor("fresh", 1L, 1L), byteArrayOf(1))

        val now = System.currentTimeMillis()
        orphan.setLastModified(now - VideoThumbnailStore.ORPHAN_MIN_AGE_MS - 1_000L)
        fresh.setLastModified(now)

        store.cleanupOrphans(validPaths = setOf(keep.absolutePath), now = now)

        assertTrue(keep.exists())
        assertFalse(orphan.exists())
        assertTrue(fresh.exists())
    }

    @Test
    fun `迁移缩略图到新 key 路径且保留内容`() {
        val store = store()
        val bytes = byteArrayOf(7, 8, 9)
        val old = store.write(store.pathFor("root::old.mp4", 10L, 100L), bytes)

        val moved = store.move(old.absolutePath, "root::new.mp4", 10L, 100L)

        assertEquals(store.pathFor("root::new.mp4", 10L, 100L).absolutePath, moved)
        assertFalse(old.exists())
        assertTrue(File(moved!!).readBytes().contentEquals(bytes))
    }

    @Test
    fun `迁移忽略空路径与不存在的文件`() {
        val store = store()
        assertNull(store.move(null, "k", 1L, 1L))
        assertNull(store.move("  ", "k", 1L, 1L))
        assertNull(store.move(File(temp.root, "missing.jpg").absolutePath, "k", 1L, 1L))
    }

    @Test
    fun `容量超限时删到目标以下`() {
        val store = store()
        val bytes = ByteArray(100)
        val oldest = store.write(store.pathFor("a", 1L, 1L), bytes)
        val middle = store.write(store.pathFor("b", 1L, 1L), bytes)
        val newest = store.write(store.pathFor("c", 1L, 1L), bytes)
        oldest.setLastModified(1_000L)
        middle.setLastModified(2_000L)
        newest.setLastModified(3_000L)

        store.enforceCapacity(maxBytes = 250L, targetBytes = 150L)

        assertFalse(oldest.exists())
        assertFalse(middle.exists())
        assertTrue(newest.exists())
    }
}
