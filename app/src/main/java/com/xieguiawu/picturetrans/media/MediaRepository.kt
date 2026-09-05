package com.xieguiawu.picturetrans.media

import java.io.InputStream

/**
 * 媒体访问抽象层。接口只依赖 java.io 与 Models——保证服务器路由可在纯 JVM
 * 测试中用 fake 实现做端到端验证。
 */
interface MediaRepository {
    suspend fun list(collection: Collection): List<MediaItem>

    fun resolve(id: Long, collection: Collection): MediaItem?

    fun openStream(item: MediaItem): InputStream?

    /** 缩略图 JPEG 流；无缩略图（如 Download 中的普通文件）返回 null。 */
    fun thumbnailStream(item: MediaItem, sizePx: Int): InputStream?

    /** 流式保存上传文件，返回最终落盘文件名（系统可能重命名去重）。 */
    suspend fun save(displayName: String, mimeType: String?, source: InputStream): Result<String>
}
