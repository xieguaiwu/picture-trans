package com.xieguiawu.picturetrans.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * MediaStore 实现。
 * API 33+: READ_MEDIA_IMAGES/VIDEO；API 29-32: READ_EXTERNAL_STORAGE；
 * API 26-28: READ + WRITE_EXTERNAL_STORAGE，上传写公共 Download 目录后扫媒。
 */
class MediaStoreRepository(private val context: Context) : MediaRepository {

    private val resolver = context.contentResolver

    // ---------- 查询 ----------

    override suspend fun list(collection: Collection): List<MediaItem> =
        withContext(Dispatchers.IO) { query(collection, null) }

    override fun resolve(id: Long, collection: Collection): MediaItem? =
        query(collection, id).firstOrNull()

    private fun query(collection: Collection, singleId: Long?): List<MediaItem> {
        val uri = contentUri(collection)
        val projection = projectionFor(collection)
        var selection = if (singleId != null) "${MediaStore.MediaColumns._ID} = ?" else null
        var args = if (singleId != null) arrayOf(singleId.toString()) else null
        // API 26-28 无 Downloads 专属集合：Files 集合 + DATA 路径 LIKE 过滤
        if (collection == Collection.DOWNLOADS && Build.VERSION.SDK_INT < 29) {
            val like = "%${File.separator}Download${File.separator}%"
            selection = if (selection == null) {
                "${MediaStore.MediaColumns.DATA} LIKE ?"
            } else {
                "$selection AND ${MediaStore.MediaColumns.DATA} LIKE ?"
            }
            args = ((args?.toList() ?: emptyList()) + like).toTypedArray()
        }
        val order = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

        val out = mutableListOf<MediaItem>()
        runCatching {
            resolver.query(uri, projection, selection, args, order)?.use { c ->
                val idI = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameI = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeI = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val sizeI = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateI = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val durI = if (collection == Collection.VIDEOS) c.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1

                while (c.moveToNext()) {
                    out += MediaItem(
                        id = c.getLong(idI),
                        collection = collection,
                        displayName = c.getString(nameI) ?: "unnamed",
                        mimeType = c.getString(mimeI) ?: "application/octet-stream",
                        sizeBytes = c.getLong(sizeI),
                        dateModifiedMs = c.getLong(dateI) * 1000L,
                        durationMs = if (durI >= 0) c.getLong(durI) else 0L,
                    )
                }
            }
        }.onFailure { return@onFailure } // 权限缺失等场景：返回空列表，UI 提示授权
        return out
    }

    private fun projectionFor(collection: Collection): Array<String> {
        val base = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
        return when {
            collection == Collection.VIDEOS -> base + MediaStore.Video.VideoColumns.DURATION
            collection != Collection.DOWNLOADS && Build.VERSION.SDK_INT >= 29 ->
                base + MediaStore.MediaColumns.WIDTH + MediaStore.MediaColumns.HEIGHT
            else -> base
        }
    }

    private fun contentUri(collection: Collection): Uri = when (collection) {
        Collection.IMAGES -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        Collection.VIDEOS -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        Collection.DOWNLOADS ->
            if (Build.VERSION.SDK_INT >= 29) MediaStore.Downloads.EXTERNAL_CONTENT_URI
            else MediaStore.Files.getContentUri("external")
    }

    // ---------- 读取 ----------

    override fun openStream(item: MediaItem): InputStream? = runCatching {
        resolver.openInputStream(ContentUris.withAppendedId(contentUri(item.collection), item.id))
    }.getOrNull()

    override fun thumbnailStream(item: MediaItem, sizePx: Int): InputStream? {
        if (item.collection == Collection.DOWNLOADS) return null
        val bitmap: Bitmap = runCatching {
            val uri = ContentUris.withAppendedId(contentUri(item.collection), item.id)
            if (Build.VERSION.SDK_INT >= 29) {
                resolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
            } else {
                legacyThumbnail(item, sizePx) ?: return null
            }
        }.getOrNull() ?: return null
        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos)
        bitmap.recycle()
        return bos.toByteArray().inputStream()
    }

    private fun legacyThumbnail(item: MediaItem, sizePx: Int): Bitmap? {
        val sampled = runCatching {
            openStream(item)?.use { input ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, opts)
                var sample = 1
                while (opts.outWidth / (sample * 2) >= sizePx && opts.outHeight / (sample * 2) >= sizePx) sample *= 2
                openStream(item)?.use { s ->
                    BitmapFactory.decodeStream(s, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }
        }.getOrNull()
        return sampled
    }

    // ---------- 保存上传 ----------

    override suspend fun save(
        displayName: String,
        mimeType: String?,
        source: InputStream,
    ): Result<String> = withContext(Dispatchers.IO) {
        val mime = mimeType?.takeIf { it.isNotBlank() } ?: guessMime(displayName)
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) saveModern(displayName, mime, source)
            else saveLegacy(displayName, mime, source)
        }
    }

    private fun saveModern(displayName: String, mime: String?, source: InputStream): String {
        val target = when {
            mime?.startsWith("image/") == true -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            mime?.startsWith("video/") == true -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> if (Build.VERSION.SDK_INT >= 29) MediaStore.Downloads.EXTERNAL_CONTENT_URI
            else MediaStore.Files.getContentUri("external")
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(target, values)
            ?: error("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)?.use { out -> source.copyTo(out, BUFFER) }
                ?: error("openOutputStream failed")
            if (Build.VERSION.SDK_INT >= 29) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) } // 失败不留残骸
            throw e
        }
        return finalDisplayName(uri, displayName)
    }

    private fun finalDisplayName(uri: Uri, fallback: String): String = runCatching {
        resolver.query(
            uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null,
        )?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: fallback
    }.getOrDefault(fallback)

    private fun saveLegacy(displayName: String, mime: String?, source: InputStream): String {
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        var file = File(dir, displayName)
        var n = 1
        while (file.exists()) {
            val dot = displayName.lastIndexOf('.')
            file = if (dot > 0) {
                File(dir, "${displayName.substring(0, dot)} ($n)${displayName.substring(dot)}")
            } else {
                File(dir, "$displayName ($n)")
            }
            n++
        }
        FileOutputStream(file).use { out -> source.copyTo(out, BUFFER) }
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
        return file.name
    }

    private fun guessMime(name: String): String? {
        val ext = name.substringAfterLast('.', "").lowercase()
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    companion object {
        private const val BUFFER = 64 * 1024
    }
}
