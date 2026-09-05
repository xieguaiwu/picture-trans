package com.xieguiawu.picturetrans.media

import kotlinx.serialization.Serializable

/** 手机端媒体集合。DOWNLOADS = 公共 Download 目录。 */
@Serializable
enum class Collection {
    IMAGES, VIDEOS, DOWNLOADS;

    companion object {
        /** URL 参数解析；未知值回退 null（防御恶意参数）。 */
        fun fromParam(raw: String?): Collection? = when (raw) {
            "images" -> IMAGES
            "videos" -> VIDEOS
            "downloads" -> DOWNLOADS
            else -> null
        }
    }
}

/** 单个可传输文件。id 是 MediaStore 行 id，不是文件路径——杜绝路径穿越。 */
@Serializable
data class MediaItem(
    val id: Long,
    val collection: Collection,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val dateModifiedMs: Long,
    val durationMs: Long = 0,
)
