package com.comicnest.feature.video.viewmodel

import com.comicnest.core.model.VideoKey
import com.comicnest.core.model.VideoSummary
import com.comicnest.core.model.VideoThumbnailState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 「下一个视频」队列规则单测（DESIGN v2 §11.4 R4）。 */
class NextVideoTest {

    private fun video(name: String): VideoSummary = VideoSummary(
        key = VideoKey(rootId = "root", fileName = name),
        absPath = "/v/$name",
        sizeBytes = 1L,
        lastModified = 0L,
        importedAt = 0L,
        durationMs = null,
        thumbPath = null,
        thumbState = VideoThumbnailState.PENDING,
    )

    private val a = video("a.mp4")
    private val b = video("b.mp4")
    private val c = video("c.mp4")
    private val d = video("d.mp4")
    private val queue = listOf(a, b, c, d)

    @Test
    fun `返回队列中的下一个`() {
        assertEquals(b, nextVideoAfter(queue, a.key) { true })
        assertEquals(c, nextVideoAfter(queue, b.key) { true })
    }

    @Test
    fun `末项或不在队列时无下一个`() {
        assertNull(nextVideoAfter(queue, d.key) { true })
        assertNull(nextVideoAfter(queue, null) { true })
        assertNull(nextVideoAfter(queue, VideoKey("root", "missing.mp4")) { true })
    }

    @Test
    fun `跳过失效文件后取下一个可播项`() {
        assertEquals(d, nextVideoAfter(queue, a.key) { it != b && it != c })
    }

    @Test
    fun `连续失效超过三个即视为没有下一个`() {
        val longQueue = listOf(a, b, c, d, video("e.mp4"), video("f.mp4"))
        assertNull(nextVideoAfter(longQueue, a.key) { it == longQueue.last() })
    }

    @Test
    fun `空队列返回空`() {
        assertNull(nextVideoAfter(emptyList(), a.key) { true })
    }
}
