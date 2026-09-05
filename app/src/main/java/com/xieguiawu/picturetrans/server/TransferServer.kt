package com.xieguiawu.picturetrans.server

import com.xieguiawu.picturetrans.media.Collection
import com.xieguiawu.picturetrans.media.MediaRepository
import com.xieguiawu.picturetrans.transfer.Direction
import com.xieguiawu.picturetrans.transfer.TransferTracker
import com.xieguiawu.picturetrans.util.CountingInputStream
import com.xieguiawu.picturetrans.util.FileNameSanitizer
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.cio.CIO
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.net.URLEncoder

/**
 * 内嵌 HTTP 服务器。
 * 全部路由位于 /t/{token}/ 下；token 不匹配一律 404（不暴露服务存在性）。
 * 大文件全程流式：下载 = WriteChannelContent + 字节计数；上传 = multipart 流式。
 * 手写单段 Range 支持（视频在浏览器里可拖进度条）。
 */
class TransferServer(
    private val port: Int,
    token: String,
    private val repository: MediaRepository,
    private val tracker: TransferTracker,
) {
    private val guard = TokenGuard(token)
    private val json = Json { encodeDefaults = true }
    private var engine: ApplicationEngine? = null

    /** 绑定并启动。返回实际端口（port=0 时为系统分配）。 */
    suspend fun start(): Int {
        check(engine == null) { "server already started" }
        val env = applicationEngineEnvironment {
            connector { host = "0.0.0.0"; port = this@TransferServer.port }
            module { routes() }
        }
        val e = embeddedServer(CIO, environment = env)
        e.start(wait = false)
        engine = e
        return e.resolvedConnectors().first().port
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 2000)
        engine = null
    }

    private fun Application.routes() {
        routing {
            get("/t/{token}") { if (call.checkToken()) call.index() }
            get("/t/{token}/") { if (call.checkToken()) call.index() }
            get("/t/{token}/api/list") { if (call.checkToken()) call.apiList() }
            get("/t/{token}/thumb") { if (call.checkToken()) call.thumb() }
            get("/t/{token}/file") { if (call.checkToken()) call.file() }
            post("/t/{token}/upload") { if (call.checkToken()) call.upload() }
        }
    }

    private suspend fun ApplicationCall.checkToken(): Boolean {
        val ok = guard.isValid(parameters["token"])
        if (!ok) respond(HttpStatusCode.NotFound)
        return ok
    }

    private suspend fun ApplicationCall.index() {
        respondText(WebPage.HTML, ContentType.Text.Html)
    }

    private suspend fun ApplicationCall.apiList() {
        val col = Collection.fromParam(request.queryParameters["c"])
        if (col == null) {
            respond(HttpStatusCode.BadRequest)
            return
        }
        respondText(json.encodeToString(repository.list(col)), ContentType.Application.Json)
    }

    private suspend fun ApplicationCall.thumb() {
        val item = resolveItem() ?: return
        val size = (request.queryParameters["s"]?.toIntOrNull() ?: 256).coerceIn(32, 512)
        val stream = repository.thumbnailStream(item, size)
        if (stream == null) {
            respond(HttpStatusCode.NotFound)
            return
        }
        response.headers.append(HttpHeaders.CacheControl, "public, max-age=86400")
        respondBytes(stream.readBytes(), ContentType.Image.JPEG)
    }

    private suspend fun ApplicationCall.file() {
        val item = resolveItem() ?: return
        val input = repository.openStream(item)
        if (input == null) {
            respond(HttpStatusCode.NotFound)
            return
        }
        val mime = ContentType.parse(item.mimeType.ifBlank { "application/octet-stream" })
        val size = item.sizeBytes
        val asAttachment = request.queryParameters["dl"] == "1"
        response.headers.append(
            HttpHeaders.ContentDisposition,
            contentDisposition(item.displayName, asAttachment),
        )

        val handle = tracker.begin(
            Direction.DOWNLOAD,
            item.displayName,
            if (size > 0) size else -1L,
        )
        val range = parseRange(request.header(HttpHeaders.Range), size)
        if (range != null) {
            val (start, endInclusive) = range
            val length = endInclusive - start + 1
            response.headers.append(HttpHeaders.ContentRange, "bytes $start-$endInclusive/$size")
            respond(HttpStatusCode.PartialContent, object : OutgoingContent.WriteChannelContent() {
                override val contentLength: Long = length
                override val contentType: ContentType = mime
                override suspend fun writeTo(channel: ByteWriteChannel) {
                    try {
                        input.use { stream ->
                            val source = stream.toByteReadChannel()
                            if (start > 0) source.skipExactly(start)
                            copyCounting(source, channel, length, handle)
                        }
                        handle.finish(ok = true)
                    } catch (e: Exception) {
                        handle.finish(ok = false, message = e.message)
                        throw e
                    }
                }
            })
        } else {
            respond(object : OutgoingContent.WriteChannelContent() {
                override val contentLength: Long? = if (size > 0) size else null
                override val contentType: ContentType = mime
                override suspend fun writeTo(channel: ByteWriteChannel) {
                    try {
                        input.use { stream ->
                            copyCounting(stream.toByteReadChannel(), channel, -1, handle)
                        }
                        handle.finish(ok = true)
                    } catch (e: Exception) {
                        handle.finish(ok = false, message = e.message)
                        throw e
                    }
                }
            })
        }
    }

    private suspend fun ApplicationCall.upload() {
        var saved = 0
        var failed = 0
        val multipart = receiveMultipart()
        multipart.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    val name = FileNameSanitizer.sanitize(part.originalFileName ?: "file")
                    val handle = tracker.begin(Direction.UPLOAD, name, -1L)
                    try {
                        val counted = CountingInputStream(part.streamProvider()) { handle.addBytes(it) }
                        repository.save(name, part.contentType?.toString(), counted)
                            .onSuccess { handle.finish(ok = true); saved++ }
                            .onFailure { handle.finish(ok = false, message = it.message); failed++ }
                    } catch (e: Exception) {
                        handle.finish(ok = false, message = e.message)
                        failed++
                    }
                }
                else -> Unit
            }
            part.dispose()
        }
        respondText("""{"saved":$saved,"failed":$failed}""", ContentType.Application.Json)
    }

    /** 解析 id+c 参数并取回条目；参数非法时已发送 400，返回 null。 */
    private suspend fun ApplicationCall.resolveItem() =
        request.queryParameters["id"]?.toLongOrNull()?.let { id ->
            Collection.fromParam(request.queryParameters["c"])?.let { col ->
                repository.resolve(id, col)
            }
        } ?: run {
            respond(HttpStatusCode.BadRequest)
            null
        }

    private fun contentDisposition(name: String, attachment: Boolean): String {
        val type = if (attachment) "attachment" else "inline"
        val ascii = buildString {
            for (b in name.toByteArray(Charsets.US_ASCII)) {
                append(
                    if (b in 0x20..0x7e && b != '"'.code.toByte() && b != '\\'.code.toByte()) {
                        b.toInt().toChar()
                    } else "_",
                )
            }
        }
        val utf8 = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        return """$type; filename="$ascii"; filename*=UTF-8''$utf8"""
    }

    companion object {
        /** 解析单段 Range；无法解析返回 null（回退 200 全量响应）。 */
        fun parseRange(header: String?, size: Long): Pair<Long, Long>? {
            if (header.isNullOrBlank() || size <= 0) return null
            val m = Regex("""bytes=(\d*)-(\d*)""").matchEntire(header.trim()) ?: return null
            val (a, b) = m.destructured
            return when {
                a.isEmpty() && b.isEmpty() -> null
                a.isEmpty() -> {
                    val len = b.toLong().coerceAtMost(size)
                    (size - len) to (size - 1)
                }
                else -> {
                    val start = a.toLong()
                    if (start >= size) null
                    else start to minOf(if (b.isEmpty()) size - 1 else b.toLong(), size - 1)
                }
            }
        }

        private suspend fun ByteReadChannel.skipExactly(n: Long) {
            var left = n
            val buf = ByteArray(64 * 1024)
            while (left > 0) {
                val want = minOf(buf.size.toLong(), left).toInt()
                val r = readAvailable(buf, 0, want)
                if (r == -1) break
                left -= r
            }
        }

        private suspend fun copyCounting(
            source: ByteReadChannel,
            target: ByteWriteChannel,
            limit: Long,
            handle: TransferTracker.Handle,
        ) {
            val buf = ByteArray(64 * 1024)
            var remaining = limit
            while (limit < 0 || remaining > 0) {
                val want = if (limit < 0) buf.size else minOf(buf.size.toLong(), remaining).toInt()
                val n = source.readAvailable(buf, 0, want)
                if (n == -1) break
                target.writeFully(buf, 0, n)
                handle.addBytes(n)
                if (limit > 0) remaining -= n
            }
            target.flush()
        }
    }
}
