package com.comicnest.core.model

/** 播放页进入时的显示方向（已按旋转角摆正）。 */
enum class VideoOrientation {
    PORTRAIT,
    LANDSCAPE,
}

/** 视频像素尺寸与旋转角（AUTO 判定用，DESIGN v2 §11.6 R6）。 */
data class VideoDimensions(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
)

/**
 * 依据偏好与（可空的）视频尺寸解析进入播放页时的方向：
 * - [VideoOrientationMode.PORTRAIT] / [VideoOrientationMode.LANDSCAPE] 直接返回；
 * - [VideoOrientationMode.AUTO] 按摆正后的宽高判定（旋转 90/270 需交换宽高），
 *   尺寸缺失或宽高非法时按竖屏处理（与默认行为一致）。
 */
fun resolveVideoOrientation(
    mode: VideoOrientationMode,
    dimensions: VideoDimensions?,
): VideoOrientation = when (mode) {
    VideoOrientationMode.PORTRAIT -> VideoOrientation.PORTRAIT
    VideoOrientationMode.LANDSCAPE -> VideoOrientation.LANDSCAPE
    VideoOrientationMode.AUTO -> {
        val dims = dimensions
        if (dims == null || dims.width <= 0 || dims.height <= 0) {
            VideoOrientation.PORTRAIT
        } else {
            val quarterTurn = dims.rotationDegrees % 180 != 0
            val width = if (quarterTurn) dims.height else dims.width
            val height = if (quarterTurn) dims.width else dims.height
            if (width > height) VideoOrientation.LANDSCAPE else VideoOrientation.PORTRAIT
        }
    }
}
