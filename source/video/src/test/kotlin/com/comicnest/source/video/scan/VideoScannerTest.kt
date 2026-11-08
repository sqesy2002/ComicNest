package com.comicnest.source.video.scan

import com.comicnest.core.model.VideoRoot
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 视频扫描规则单测（DESIGN v2 §3.4 / V9）：一级、白名单、非隐藏、非空、自然序。 */
class VideoScannerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun file(name: String, bytes: Int = 8): File =
        temp.newFile(name).apply { writeBytes(ByteArray(bytes)) }

    @Test
    fun `只保留一级目录下的白名单文件`() {
        file("a.mp4")
        file("b.MKV")
        file("c.txt")
        file("d.avi")
        file(".hidden.mp4")
        file("empty.mp4", bytes = 0)
        val sub = temp.newFolder("sub")
        File(sub, "nested.mp4").writeBytes(ByteArray(8))

        val names = VideoScanner.listVideoFiles(temp.root).map { it.name }
        assertEquals(listOf("a.mp4", "b.MKV"), names)
    }

    @Test
    fun `结果按自然序排列`() {
        file("ep10.mp4")
        file("ep2.mp4")
        file("ep1.mp4")

        val names = VideoScanner.listVideoFiles(temp.root).map { it.name }
        assertEquals(listOf("ep1.mp4", "ep2.mp4", "ep10.mp4"), names)
    }

    @Test
    fun `空目录与不可读目录返回空列表`() {
        assertTrue(VideoScanner.listVideoFiles(temp.root).isEmpty())
        assertTrue(VideoScanner.listVideoFiles(File(temp.root, "missing")).isEmpty())
    }

    @Test
    fun `摘要使用根 id 与文件名构造 key`() {
        val target = file("movie.mp4")
        val root = VideoRoot.of(temp.root.absolutePath, "Movies", 123L)
        val summary = VideoScanner.toSummary(root, target, importedAt = 456L)

        assertEquals(root.id, summary.key.rootId)
        assertEquals("movie.mp4", summary.key.fileName)
        assertEquals(target.absolutePath, summary.absPath)
        assertEquals(456L, summary.importedAt)
        assertEquals(target.length(), summary.sizeBytes)
        assertEquals(target.lastModified(), summary.lastModified)
        assertEquals("movie", summary.displayName.substringBeforeLast('.'))
    }
}
