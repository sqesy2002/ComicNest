package com.comicnest.core.model

/** 主页排序方式（名称默认 / 导入时间）。 */
enum class SortMode {
    NAME,
    IMPORT_TIME,
}

/** 阅读模式（默认 WEBTOON 下拉式）。 */
enum class ReaderMode {
    WEBTOON,
    PAGER,
}

/** 默认视频朝向（DESIGN v2 §11.6 R6）：竖屏 / 横屏 / 根据视频比例自动。 */
enum class VideoOrientationMode {
    PORTRAIT,
    LANDSCAPE,
    AUTO,
}
