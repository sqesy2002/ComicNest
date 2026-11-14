package com.comicnest.feature.video.viewmodel

import com.comicnest.core.model.VideoKey
import com.comicnest.core.model.VideoSummary
import com.comicnest.core.model.VideoThumbnailState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** 选集列表构建规则单测（DESIGN v2 §12）。 */
class PlayerPlaylistTest {

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

    private val videos = (1..40).map { video("v$it.mp4") }

    @Test
    fun `完整保留列表并定位当前项下标`() {
        val playlist = buildPlaylist(videos, videos[20].key)

        assertEquals(40, playlist.items.size)
        assertEquals(videos[20], playlist.items[20])
        assertEquals(20, playlist.currentIndex)
    }

    @Test
    fun `首位与末位同样全量展示`() {
        val head = buildPlaylist(videos, videos.first().key)
        assertEquals(40, head.items.size)
        assertEquals(0, head.currentIndex)

        val tail = buildPlaylist(videos, videos.last().key)
        assertEquals(40, tail.items.size)
        assertEquals(39, tail.currentIndex)
    }

    @Test
    fun `当前视频不在列表中时全量展示且无当前项`() {
        val missing = buildPlaylist(
            videos,
            VideoKey(rootId = "root", fileName = "missing.mp4"),
        )
        assertEquals(40, missing.items.size)
        assertEquals(-1, missing.currentIndex)

        val noCurrent = buildPlaylist(videos, null)
        assertEquals(40, noCurrent.items.size)
        assertEquals(-1, noCurrent.currentIndex)
    }

    @Test
    fun `直接复用传入的列表实例`() {
        val playlist = buildPlaylist(videos, videos[3].key)

        assertSame(videos, playlist.items)
    }

    @Test
    fun `空列表返回空选集`() {
        val playlist = buildPlaylist(emptyList(), null)

        assertEquals(emptyList<VideoSummary>(), playlist.items)
        assertEquals(-1, playlist.currentIndex)
    }
}
