package com.comicnest.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** 默认视频朝向解析单测（DESIGN v2 §11.6 R6）。 */
class VideoOrientationTest {

    private val landscapeDims = VideoDimensions(width = 1920, height = 1080, rotationDegrees = 0)
    private val portraitDims = VideoDimensions(width = 1080, height = 1920, rotationDegrees = 0)
    private val rotatedLandscape = VideoDimensions(width = 1920, height = 1080, rotationDegrees = 90)

    @Test
    fun `竖屏偏好始终返回竖屏`() {
        assertEquals(
            VideoOrientation.PORTRAIT,
            resolveVideoOrientation(VideoOrientationMode.PORTRAIT, landscapeDims),
        )
    }

    @Test
    fun `横屏偏好始终返回横屏`() {
        assertEquals(
            VideoOrientation.LANDSCAPE,
            resolveVideoOrientation(VideoOrientationMode.LANDSCAPE, portraitDims),
        )
    }

    @Test
    fun `自动模式按摆正后的宽高判定`() {
        assertEquals(
            VideoOrientation.LANDSCAPE,
            resolveVideoOrientation(VideoOrientationMode.AUTO, landscapeDims),
        )
        assertEquals(
            VideoOrientation.PORTRAIT,
            resolveVideoOrientation(VideoOrientationMode.AUTO, portraitDims),
        )
        assertEquals(
            VideoOrientation.PORTRAIT,
            resolveVideoOrientation(VideoOrientationMode.AUTO, rotatedLandscape),
        )
    }

    @Test
    fun `自动模式尺寸缺失或非法回退竖屏`() {
        assertEquals(
            VideoOrientation.PORTRAIT,
            resolveVideoOrientation(VideoOrientationMode.AUTO, null),
        )
        assertEquals(
            VideoOrientation.PORTRAIT,
            resolveVideoOrientation(VideoOrientationMode.AUTO, VideoDimensions(0, 0, 0)),
        )
    }

    @Test
    fun `方形视频自动模式按竖屏处理`() {
        assertEquals(
            VideoOrientation.PORTRAIT,
            resolveVideoOrientation(
                VideoOrientationMode.AUTO,
                VideoDimensions(1080, 1080, 0),
            ),
        )
    }
}
