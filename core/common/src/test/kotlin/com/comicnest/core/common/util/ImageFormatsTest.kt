package com.comicnest.core.common.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 图片格式识别单测（DESIGN §4：六格式 + 非法；gif 动图）。 */
class ImageFormatsTest {

    @Test
    fun `六种受支持格式均为真`() {
        listOf("a.jpg", "a.jpeg", "a.png", "a.webp", "a.gif", "a.bmp").forEach {
            assertTrue("$it 应受支持", isSupportedImage(it))
        }
    }

    @Test
    fun `大写扩展名同样识别`() {
        assertTrue(isSupportedImage("IMG.JPG"))
        assertTrue(isSupportedImage("IMG.PNG"))
    }

    @Test
    fun `可选格式识别`() {
        assertTrue(isSupportedImage("x.avif"))
        assertTrue(isSupportedImage("x.heif"))
    }

    @Test
    fun `非法或无扩展名返回假`() {
        listOf("notes.txt", "archive.zip", "noext", ".gifignore", "video.mp4").forEach {
            assertFalse("$it 不应被判为图片", isSupportedImage(it))
        }
    }

    @Test
    fun `仅点号无实际后缀视为无扩展名`() {
        assertFalse(isSupportedImage("trailingdot."))
    }

    @Test
    fun `路径中的点不误判为扩展名`() {
        // 目录名含点、文件无扩展名
        assertFalse(isSupportedImage("com.company.folder/file"))
        assertTrue(isSupportedImage("com.company.folder/file.png"))
    }

    @Test
    fun `动图判定`() {
        assertTrue(isAnimatedExt("a.gif"))
        assertFalse(isAnimatedExt("a.png"))
        assertFalse(isAnimatedExt("a.jpg"))
    }
}
