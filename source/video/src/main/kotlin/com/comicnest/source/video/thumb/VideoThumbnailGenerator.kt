package com.comicnest.source.video.thumb

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import java.io.ByteArrayOutputStream

/**
 * 中间帧缩略图生成（DESIGN v2 §3.4/V4）：
 * 取 `durationMs / 2` 处帧，先 `OPTION_CLOSEST_SYNC`（快），近黑时降级 `OPTION_CLOSEST`（更准）。
 */
object VideoThumbnailGenerator {

    private const val NEAR_BLACK_LUMA = 16.0
    private const val JPEG_QUALITY = 85
    private const val SAMPLE_GRID = 8

    /** 取视频中间帧；取不到返回 null。 */
    fun frameAtMiddle(retriever: MediaMetadataRetriever, durationMs: Long): Bitmap? {
        val middleUs = durationMs.coerceAtLeast(0L) * 500L
        val sync = runCatching {
            retriever.getFrameAtTime(middleUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }.getOrNull()
        if (sync != null && !isNearBlack(sync)) return sync
        val exact = runCatching {
            retriever.getFrameAtTime(middleUs, MediaMetadataRetriever.OPTION_CLOSEST)
        }.getOrNull()
        return exact ?: sync
    }

    /** 中心区域平均亮度低于阈值即视为黑帧（纯黑片头/片尾保护）。 */
    fun isNearBlack(bitmap: Bitmap): Boolean {
        if (bitmap.isRecycled) return true
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return true
        val x0 = (w * 0.4).toInt().coerceIn(0, w - 1)
        val x1 = (w * 0.6).toInt().coerceIn(x0 + 1, w)
        val y0 = (h * 0.4).toInt().coerceIn(0, h - 1)
        val y1 = (h * 0.6).toInt().coerceIn(y0 + 1, h)
        val stepX = ((x1 - x0) / SAMPLE_GRID).coerceAtLeast(1)
        val stepY = ((y1 - y0) / SAMPLE_GRID).coerceAtLeast(1)
        var sum = 0.0
        var count = 0
        var y = y0
        while (y < y1) {
            var x = x0
            while (x < x1) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                sum += 0.299 * r + 0.587 * g + 0.114 * b
                count++
                x += stepX
            }
            y += stepY
        }
        return count > 0 && sum / count < NEAR_BLACK_LUMA
    }

    /** 按旋转元数据摆正竖拍视频（避免缩略图歪 90°）。 */
    fun applyRotation(bitmap: Bitmap, degrees: Int): Bitmap {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0 || bitmap.isRecycled) return bitmap
        return runCatching {
            val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    /** 等比缩放到目标宽度（只在超宽时缩放）。 */
    fun scaleToWidth(bitmap: Bitmap, maxWidthPx: Int): Bitmap {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.width <= maxWidthPx) return bitmap
        val ratio = maxWidthPx.toFloat() / bitmap.width
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return runCatching {
            Bitmap.createScaledBitmap(bitmap, maxWidthPx, height, true)
        }.getOrDefault(bitmap)
    }

    /** JPEG 编码（质量 85，单张约 15~40KB）。 */
    fun encodeJpeg(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        return out.toByteArray()
    }
}
