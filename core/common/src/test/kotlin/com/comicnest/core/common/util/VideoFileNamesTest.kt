package com.comicnest.core.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 视频重命名规则单测：主名/扩展名拆分、非法字符、长度与扩展名保留。 */
class VideoFileNamesTest {

    @Test
    fun `主名与扩展名拆分`() {
        assertEquals("a.b", VideoFileNames.baseNameOf("a.b.mp4"))
        assertEquals("mp4", VideoFileNames.extensionOf("a.b.mp4"))
        assertEquals("movie", VideoFileNames.baseNameOf("movie"))
        assertEquals("", VideoFileNames.extensionOf("movie"))
        assertEquals(".hidden", VideoFileNames.baseNameOf(".hidden"))
        assertEquals("", VideoFileNames.extensionOf(".hidden"))
    }

    @Test
    fun `合法名称去首尾空白`() {
        assertEquals("新名字", VideoFileNames.validateBaseName("  新名字  ").getOrThrow())
    }

    @Test
    fun `空名称被拒绝`() {
        assertTrue(VideoFileNames.validateBaseName("   ").isFailure)
    }

    @Test
    fun `非法字符被拒绝`() {
        listOf("a/b", "a\\b", "a:b", "a*b", "a?b", "a\"b", "a<b", "a>b", "a|b", "a\nb")
            .forEach { name ->
                assertTrue("应拒绝：$name", VideoFileNames.validateBaseName(name).isFailure)
            }
    }

    @Test
    fun `点结尾与纯点被拒绝`() {
        assertTrue(VideoFileNames.validateBaseName("name.").isFailure)
        assertTrue(VideoFileNames.validateBaseName(".").isFailure)
        assertTrue(VideoFileNames.validateBaseName("..").isFailure)
    }

    @Test
    fun `超长名称被拒绝`() {
        val ok = "a".repeat(VideoFileNames.MAX_BASE_LENGTH)
        val tooLong = "a".repeat(VideoFileNames.MAX_BASE_LENGTH + 1)
        assertTrue(VideoFileNames.validateBaseName(ok).isSuccess)
        assertTrue(VideoFileNames.validateBaseName(tooLong).isFailure)
    }

    @Test
    fun `拼接文件名时保留原扩展名`() {
        assertEquals("新名.mp4", VideoFileNames.fileNameFor("新名", "old.mkv.mp4"))
        assertEquals("新名", VideoFileNames.fileNameFor("新名", "noext"))
        assertEquals("a.b.mkv", VideoFileNames.fileNameFor("a.b", "x.mkv"))
        assertFalse(VideoFileNames.fileNameFor("新名", "a.mp4").startsWith("."))
    }
}
