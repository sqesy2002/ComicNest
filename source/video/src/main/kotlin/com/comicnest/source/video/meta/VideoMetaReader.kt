package com.comicnest.source.video.meta

import android.media.MediaMetadataRetriever
import com.comicnest.source.video.VideoMeta

/** 从 [MediaMetadataRetriever] 读取时长/宽高/旋转角（DESIGN v2 §3.4）。 */
object VideoMetaReader {

    fun fromRetriever(retriever: MediaMetadataRetriever): VideoMeta? {
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?: return null
        if (duration <= 0L) return null
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: 0
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull() ?: 0
        return VideoMeta(
            durationMs = duration,
            width = width,
            height = height,
            rotationDegrees = ((rotation % 360) + 360) % 360,
        )
    }
}
