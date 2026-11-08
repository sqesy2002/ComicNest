package com.comicnest.core.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 视频格式与时长工具单测（DESIGN v2 V9 / §3.1）。 */
class VideoFormatsTest {

    @Test
    fun `白名单七种格式均为真`() {
        listOf("a.mp4", "a.m4v", "a.mkv", "a.webm", "a.ts", "a.mov", "a.3gp").forEach {
            assertTrue("$it 应受支持", isSupportedVideo(it))
        }
    }

    @Test
    fun `大写扩展名同样识别`() {
        assertTrue(isSupportedVideo("MOVIE.MP4"))
        assertTrue(isSupportedVideo("CLIP.Mkv"))
    }

    @Test
    fun `白名单外或无扩展名为假`() {
        listOf("a.avi", "a.flv", "a.wmv", "notes.txt", "noext", "x.", "folder.name/noext").forEach {
            assertFalse("$it 不应被判为视频", isSupportedVideo(it))
        }
    }

    @Test
    fun `支持集合为七项且无重复`() {
        assertEquals(7, supportedVideoExtensions.size)
        assertTrue(supportedVideoExtensions.contains("mp4"))
    }

    @Test
    fun `时长格式化-空或非正为占位`() {
        assertEquals("--:--", formatDuration(null))
        assertEquals("--:--", formatDuration(0L))
        assertEquals("--:--", formatDuration(-1_000L))
    }

    @Test
    fun `时长格式化-分秒与小时`() {
        assertEquals("00:05", formatDuration(5_000L))
        assertEquals("01:05", formatDuration(65_000L))
        assertEquals("59:59", formatDuration(3_599_000L))
        assertEquals("1:01:01", formatDuration(3_661_000L))
    }

    @Test
    fun `md5稳定且为32位十六进制`() {
        val digest = md5Hex("/v/a.mp4|1|2")
        assertEquals(32, digest.length)
        assertEquals(digest, md5Hex("/v/a.mp4|1|2"))
        assertTrue(digest.all { it in "0123456789abcdef" })
    }
}
