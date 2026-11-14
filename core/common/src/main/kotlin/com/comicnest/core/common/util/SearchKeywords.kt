package com.comicnest.core.common.util

/** 关键词分隔：任意空白（半角空格、制表符、换行等）+ 全角空格 U+3000。 */
private val KEYWORD_SEPARATOR = Regex("[\\s\\u3000]+")

/** 单个搜索词最多参与匹配的关键词数（防止粘贴长文本造成无效遍历）。 */
private const val MAX_KEYWORDS = 8

/**
 * 搜索词拆分为关键词列表（DESIGN v2 §11.1 R1）：空白串返回空列表（等价于不搜索）。
 * 多关键词之间为 AND 语义，顺序无关；连续空白与全角空格都视为分隔符。
 */
fun parseSearchKeywords(query: String): List<String> =
    query.trim()
        .split(KEYWORD_SEPARATOR)
        .filter { it.isNotEmpty() }
        .take(MAX_KEYWORDS)

/** 文本是否同时包含全部关键词（忽略大小写）；关键词为空视为全部命中。 */
fun matchesAllKeywords(text: String, keywords: List<String>): Boolean =
    keywords.isEmpty() || keywords.all { text.contains(it, ignoreCase = true) }
