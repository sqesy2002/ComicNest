package com.comicnest.core.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 多关键词搜索工具单测（DESIGN v2 §11.1 R1）。 */
class SearchKeywordsTest {

    @Test
    fun `空格分隔多关键词按AND命中且顺序无关`() {
        val keywords = parseSearchKeywords("胡桃 芙宁娜")
        assertEquals(listOf("胡桃", "芙宁娜"), keywords)
        assertTrue(matchesAllKeywords("xx胡桃xx芙宁娜", keywords))
        assertTrue(matchesAllKeywords("芙宁娜-胡桃.jpg", keywords))
    }

    @Test
    fun `只命中单个关键词时不匹配`() {
        val keywords = parseSearchKeywords("胡桃 芙宁娜")
        assertFalse(matchesAllKeywords("xx胡桃xx", keywords))
        assertFalse(matchesAllKeywords("芙宁娜", keywords))
    }

    @Test
    fun `连续空白与全角空格作为同一组分隔符`() {
        assertEquals(
            listOf("胡桃", "芙宁娜", "钟离"),
            parseSearchKeywords("  胡桃\t\t芙宁娜\u3000\u3000钟离  "),
        )
    }

    @Test
    fun `全空白输入等价于不搜索`() {
        assertTrue(parseSearchKeywords("").isEmpty())
        assertTrue(parseSearchKeywords("   \t\u3000 ").isEmpty())
        assertTrue(matchesAllKeywords("任意名称", emptyList()))
    }

    @Test
    fun `匹配忽略大小写`() {
        val keywords = parseSearchKeywords("KaMi  saTo")
        assertTrue(matchesAllKeywords("kamisato_ayaka.mp4", keywords))
    }

    @Test
    fun `关键词最多取前八个`() {
        val keywords = parseSearchKeywords("a b c d e f g h i")
        assertEquals(8, keywords.size)
        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g", "h"), keywords)
    }
}
