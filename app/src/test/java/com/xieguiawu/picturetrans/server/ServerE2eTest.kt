package com.xieguiawu.picturetrans.server

import com.xieguiawu.picturetrans.media.Collection
import com.xieguiawu.picturetrans.media.MediaItem
import com.xieguiawu.picturetrans.media.MediaRepository
import com.xieguiawu.picturetrans.transfer.Direction
import com.xieguiawu.picturetrans.transfer.TransferTracker
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Headers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong

/** 真 socket 端到端：起真实 CIO 服务器，走完整 HTTP 栈验证鉴权/列表/下载/Range/上传。 */
class ServerE2eTest {

    private val token = "e2etoken"
    private lateinit var repo: FakeMediaRepository
    private lateinit var server: TransferServer
    private lateinit var tracker: TransferTracker
    private lateinit var client: HttpClient
    private var port = 0

    @Before
    fun setUp(): Unit = runBlocking {
        repo = FakeMediaRepository(createTempDir())
        tracker = TransferTracker()
        server = TransferServer(0, token, repo, tracker)
        port = server.start()
        client = HttpClient(CIO)
    }

    @After
    fun tearDown() {
        client.close()
        server.stop()
    }

    private fun base(): String = "http://127.0.0.1:" + port + "/t/" + token
    private fun wrong(): String = "http://127.0.0.1:" + port + "/t/wrongtoken"

    @Test fun wrongTokenGets404() = runBlocking {
        val r = client.get(wrong() + "/api/list?c=downloads")
        assertEquals(HttpStatusCode.NotFound, r.status)
    }

    @Test fun rootWithoutTokenGets404() = runBlocking {
        val r = client.get("http://127.0.0.1:" + port + "/")
        assertEquals(HttpStatusCode.NotFound, r.status)
    }

    @Test fun indexPageServed() = runBlocking {
        val r = client.get(base() + "/")
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.bodyAsText().contains("Picture Trans"))
    }

    @Test fun apiListReturnsItemsAndRejectsBadCollection() = runBlocking {
        repo.add("note.txt", byteArrayOf(1, 2, 3, 4, 5), "text/plain", Collection.DOWNLOADS)
        val r = client.get(base() + "/api/list?c=downloads")
        assertEquals(HttpStatusCode.OK, r.status)
        val arr = Json.parseToJsonElement(r.bodyAsText()).jsonArray
        assertEquals(1, arr.size)
        assertEquals("note.txt", arr[0].jsonObject["displayName"]!!.jsonPrimitive.content)
        assertEquals(5L, arr[0].jsonObject["sizeBytes"]!!.jsonPrimitive.content.toLong())
        val bad = client.get(base() + "/api/list?c=evil")
        assertEquals(HttpStatusCode.BadRequest, bad.status)
    }

    @Test fun fileDownloadStreamsExactBytesWithProgress() = runBlocking {
        val bytes = ByteArray(100_000) { (it % 251).toByte() }
        repo.add("video.bin", bytes, "video/mp4", Collection.VIDEOS)
        val r = client.get(base() + "/file?id=" + repo.idOf("video.bin") + "&c=videos")
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(100_000L, r.headers[HttpHeaders.ContentLength]!!.toLong())
        assertTrue(r.headers[HttpHeaders.ContentDisposition]!!.contains("video.bin"))
        assertTrue(r.readBytes().contentEquals(bytes))
        val h = tracker.history.value.first()
        assertTrue(h.ok)
        assertEquals(100_000L, h.bytes)
    }

    @Test fun rangeRequestReturns206Slice() = runBlocking {
        val bytes = ByteArray(100) { it.toByte() }
        repo.add("img.jpg", bytes, "image/jpeg", Collection.IMAGES)
        val r = client.get(base() + "/file?id=" + repo.idOf("img.jpg") + "&c=images") {
            header(HttpHeaders.Range, "bytes=10-19")
        }
        assertEquals(HttpStatusCode.PartialContent, r.status)
        assertEquals("bytes 10-19/100", r.headers[HttpHeaders.ContentRange])
        val body = r.readBytes()
        assertEquals(10, body.size)
        assertEquals(10, body[0].toInt())
    }

    @Test fun uploadSavesStreamedFile() = runBlocking {
        val bytes = ByteArray(50_000) { (it * 7 % 256).toByte() }
        val r = upload(base() + "/upload", "upload.bin", bytes, "application/octet-stream")
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.bodyAsText().contains("\"saved\":1"))
        val saved = repo.savedFile("upload.bin")
        assertTrue("saved file exists", saved.exists())
        assertTrue(saved.readBytes().contentEquals(bytes))
        val h = tracker.history.value.first()
        assertEquals(Direction.UPLOAD, h.direction)
        assertEquals(50_000L, h.bytes)
    }

    @Test fun uploadKeepsCjkName() = runBlocking {
        val r = upload(base() + "/upload", "\u6c49\u5b57\u56fe\u7247.png", byteArrayOf(1, 2), "image/png")
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(repo.savedFile("\u6c49\u5b57\u56fe\u7247.png").exists())
    }

    @Test fun uploadTraversalNameIsSanitized() = runBlocking {
        val r = upload(base() + "/upload", "../../evil.sh", byteArrayOf(9), null)
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(repo.savedFile("evil.sh").exists())
        assertTrue(!repo.savedFile("evil.sh").parentFile.name.startsWith("."))
    }

    @Test fun thumbForDownloadsIs404() = runBlocking {
        repo.add("doc.pdf", byteArrayOf(1), "application/pdf", Collection.DOWNLOADS)
        val r = client.get(base() + "/thumb?id=" + repo.idOf("doc.pdf") + "&c=downloads")
        assertEquals(HttpStatusCode.NotFound, r.status)
    }

    @Test fun parseRangeUnitCases() {
        assertEquals(null, TransferServer.parseRange("bytes=-", 100))
        assertEquals(null, TransferServer.parseRange("garbage", 100))
        assertEquals(null, TransferServer.parseRange("bytes=100-", 100))
        assertEquals(50L to 99L, TransferServer.parseRange("bytes=50-", 100))
        assertEquals(10L to 19L, TransferServer.parseRange("bytes=10-19", 100))
        assertEquals(90L to 99L, TransferServer.parseRange("bytes=-10", 100))
        assertEquals(null, TransferServer.parseRange("bytes=0-", 0))
    }

    private suspend fun upload(url: String, fileName: String, bytes: ByteArray, mime: String?): HttpResponse {
        val contentType = io.ktor.http.ContentType.parse(mime ?: "application/octet-stream")
        return client.submitFormWithBinaryData(
            url,
            formData {
                append(
                    "file", bytes,
                    Headers.build {
                        append(HttpHeaders.ContentType, contentType.toString())
                        append(HttpHeaders.ContentDisposition, "filename=\"" + fileName + "\"")
                    },
                )
            },
        )
    }
}

/** 临时目录 fake——接口零 android 依赖，纯 JVM 跑真 socket。 */
private class FakeMediaRepository(private val dir: File) : MediaRepository {
    private val items = mutableListOf<MediaItem>()
    private val counter = AtomicLong(1)

    fun add(name: String, bytes: ByteArray, mime: String, col: Collection): MediaItem {
        File(dir, name).writeBytes(bytes)
        val item = MediaItem(
            id = counter.getAndIncrement(),
            collection = col,
            displayName = name,
            mimeType = mime,
            sizeBytes = bytes.size.toLong(),
            dateModifiedMs = System.currentTimeMillis(),
        )
        items += item
        return item
    }

    fun idOf(name: String): Long = items.first { it.displayName == name }.id

    fun savedFile(name: String): File = File(dir, name)

    override suspend fun list(collection: Collection): List<MediaItem> =
        items.filter { it.collection == collection }

    override fun resolve(id: Long, collection: Collection): MediaItem? =
        items.find { it.id == id && it.collection == collection }

    override fun openStream(item: MediaItem): InputStream? =
        items.find { it.id == item.id }?.let { File(dir, it.displayName).inputStream() }

    override fun thumbnailStream(item: MediaItem, sizePx: Int): InputStream? = null

    override suspend fun save(
        displayName: String,
        mimeType: String?,
        source: InputStream,
    ): Result<String> = runCatching {
        val f = File(dir, displayName)
        f.outputStream().use { source.copyTo(it) }
        items += MediaItem(
            id = counter.getAndIncrement(),
            collection = Collection.DOWNLOADS,
            displayName = f.name,
            mimeType = mimeType ?: "application/octet-stream",
            sizeBytes = f.length(),
            dateModifiedMs = System.currentTimeMillis(),
        )
        f.name
    }
}
