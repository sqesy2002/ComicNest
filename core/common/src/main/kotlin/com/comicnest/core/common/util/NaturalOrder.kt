package com.comicnest.core.common.util

/**
 * 自然序比较（移植自 Mihon `compareToCaseInsensitiveNaturalOrder` 思路，Apache-2.0，见 NOTICE）。
 *
 * 数字段按数值比较、其余按字符（忽略大小写）比较，保证：
 * - `001` < `002`
 * - `2` < `10`
 * 供漫画页列表与封面选取排序使用（AC4）。
 */
fun naturalOrderCompare(a: String, b: String): Int {
    val s1 = a.lowercase()
    val s2 = b.lowercase()
    var i = 0
    var j = 0
    while (i < s1.length && j < s2.length) {
        val c1 = s1[i]
        val c2 = s2[j]
        val dig1 = c1.isDigit()
        val dig2 = c2.isDigit()
        if (dig1 && dig2) {
            // 收集两段完整数字串，按数值比较
            var end1 = i
            while (end1 < s1.length && s1[end1].isDigit()) end1++
            var end2 = j
            while (end2 < s2.length && s2[end2].isDigit()) end2++
            val cmp = compareNumericSegment(s1.substring(i, end1), s2.substring(j, end2))
            if (cmp != 0) return cmp
            i = end1
            j = end2
        } else {
            if (c1 != c2) return c1.code - c2.code
            i++
            j++
        }
    }
    // 前缀相同则较短者在前
    return (s1.length - i) - (s2.length - j)
}

/**
 * 比较两个纯数字段：先按去前导零后的长度、再按字典（等长即数值序），
 * 数值相等时以原始长度（前导零多者视为更短内容，稳定次序）打破平局。
 */
private fun compareNumericSegment(num1: String, num2: String): Int {
    val t1 = num1.trimStart('0')
    val t2 = num2.trimStart('0')
    if (t1.length != t2.length) return t1.length - t2.length
    val lexical = t1.compareTo(t2)
    if (lexical != 0) return lexical
    return num1.length - num2.length
}
