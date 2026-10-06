package com.comicnest.source.local

import com.comicnest.core.model.ComicKey
import com.comicnest.core.model.LibraryRoot
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** LocalComicSource 单测（临时目录 fixture）：两级扫描/封面最小名/空目录跳过/隐藏跳过/自然序页/gif 标记（DESIGN §4）。 */
class LocalComicSourceTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val source = LocalComicSource()

    private fun mkComic(rootDir: File, name: String, images: List<String>): File {
        val dir = File(rootDir, name).apply { mkdirs() }
        images.forEach { File(dir, it).writeText("img") }
        return dir
    }

    private fun rootOf(rootDir: File): LibraryRoot = LibraryRoot.of(rootDir.absolutePath, rootDir.name, 0L)

    @Test
    fun `扫描-两级目录且跳过隐藏目录与散落文件`() = runTest {
        val rootDir = folder.root
        mkComic(rootDir, "ComicA", listOf("p001.png", "p002.png", "p010.png"))
        mkComic(rootDir, "ComicB", listOf("a.jpg", "b.png"))
        // 隐藏目录（含图）应被忽略
        mkComic(rootDir, ".secret", listOf("x.png"))
        // 根目录下的散落文件不是漫画
        File(rootDir, "readme.txt").writeText("note")

        val comics = source.scanLibrary(rootOf(rootDir)).getOrThrow()
        assertEquals(setOf("ComicA", "ComicB"), comics.map { it.key.comicName }.toSet())
    }

    @Test
    fun `封面-取自然序最小名图片`() = runTest {
        val rootDir = folder.root
        mkComic(rootDir, "ComicA", listOf("p002.png", "p001.png", "p010.png"))

        val comic = source.scanLibrary(rootOf(rootDir)).getOrThrow().single()
        assertTrue("封面应为 p001.png，实得 ${comic.coverPath}", comic.coverPath!!.endsWith("p001.png"))
        assertEquals(3, comic.imageCount)
    }

    @Test
    fun `空目录与无有效图目录被跳过`() = runTest {
        val rootDir = folder.root
        mkComic(rootDir, "Empty", emptyList())
        mkComic(rootDir, "OnlyTxt", listOf("note.txt"))
        mkComic(rootDir, "Valid", listOf("001.png"))

        val comics = source.scanLibrary(rootOf(rootDir)).getOrThrow()
        assertEquals(listOf("Valid"), comics.map { it.key.comicName })
    }

    @Test
    fun `页列表-自然序且索引递增`() = runTest {
        val rootDir = folder.root
        val dir = mkComic(rootDir, "ComicA", listOf("p010.png", "p002.png", "p001.png"))
        val root = rootOf(rootDir)
        val key = ComicKey(root.id, "ComicA")

        val pages = source.getPages(key, root).getOrThrow()
        assertEquals(listOf("p001.png", "p002.png", "p010.png"), pages.map { it.fileName })
        assertEquals(listOf(0, 1, 2), pages.map { it.index })
        assertTrue(pages.all { it.absPath.startsWith(dir.absolutePath) })
    }

    @Test
    fun `gif 页被标记为动图`() = runTest {
        val rootDir = folder.root
        mkComic(rootDir, "ComicGif", listOf("001.png", "002.gif"))
        val root = rootOf(rootDir)
        val pages = source.getPages(ComicKey(root.id, "ComicGif"), root).getOrThrow()

        val gif = pages.single { it.fileName == "002.gif" }
        val png = pages.single { it.fileName == "001.png" }
        assertTrue(gif.isAnimated)
        assertTrue(!png.isAnimated)
    }

    @Test
    fun `库根不存在返回失败`() = runTest {
        val missing = File(folder.root, "does-not-exist")
        assertTrue(source.scanLibrary(rootOf(missing)).isFailure)
    }
}
