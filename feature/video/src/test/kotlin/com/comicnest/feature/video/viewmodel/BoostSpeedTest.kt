package com.comicnest.feature.video.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

/** 长按临时加速倍率规则：低于 2x 用 2x，2x 及以上用 3x。 */
class BoostSpeedTest {

    @Test
    fun `常规倍速长按变为两倍`() {
        assertEquals(2f, boostSpeedFor(0.5f), 0f)
        assertEquals(2f, boostSpeedFor(1f), 0f)
        assertEquals(2f, boostSpeedFor(1.5f), 0f)
    }

    @Test
    fun `两倍及以上长按变为三倍`() {
        assertEquals(3f, boostSpeedFor(2f), 0f)
        assertEquals(3f, boostSpeedFor(3f), 0f)
    }
}
