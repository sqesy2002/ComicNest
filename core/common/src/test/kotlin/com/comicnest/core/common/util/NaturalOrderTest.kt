package com.comicnest.core.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 自然序比较单测（DESIGN §4：001<002、2<10、中文、混合）。 */
class NaturalOrderTest {

    private fun assertLess(a: String, b: String) {
        assertTrue("期望 $a < $b，实得 ${naturalOrderCompare(a, b)}", naturalOrderCompare(a, b) < 0)
        assertTrue("期望 $b > $a，实得 ${naturalOrderCompare(b, a)}", naturalOrderCompare(b, a) > 0)
    }

    @Test
    fun `前导零数字段按数值序但保留宽度次序`() {
        assertLess("001", "002")
        assertLess("page001", "page002")
    }

    @Test
    fun `多位数字按数值比较而非字典序`() {
        assertLess("2", "10")
        assertLess("chapter2", "chapter10")
        assertLess("image9.jpg", "image10.jpg")
    }

    @Test
    fun `忽略大小写`() {
        assertEquals(0, naturalOrderCompare("ABC", "abc"))
        assertLess("a2", "B10")
    }

    @Test
    fun `中文与非数字稳定排序`() {
        assertLess("第一话", "第一话续")
        // 数字混合中文
        assertLess("第2话", "第10话")
    }

    @Test
    fun `相等字符串返回零`() {
        assertEquals(0, naturalOrderCompare("same.png", "same.png"))
    }

    @Test
    fun `列表整体自然序排序正确`() {
        val input = listOf("page10.png", "page2.png", "page1.png", "page100.png")
        val sorted = input.sortedWith { a, b -> naturalOrderCompare(a, b) }
        assertEquals(
            listOf("page1.png", "page2.png", "page10.png", "page100.png"),
            sorted,
        )
    }
}
