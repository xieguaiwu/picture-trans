package com.xieguiawu.picturetrans.qr

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** 生成局域网地址二维码（ZXing 纯 Java 核心，无相机依赖）。 */
object QrCode {

    fun encode(content: String, sizePx: Int = 640): ImageBitmapResult {
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1,
            )
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val w = matrix.width
            val h = matrix.height
            val pixels = IntArray(w * h) { i ->
                if (matrix.get(i % w, i / w)) Color.BLACK else Color.WHITE
            }
            val bmp = createBitmap(w, h, Bitmap.Config.RGB_565)
            bmp.setPixels(pixels, 0, w, 0, 0, w, h)
            ImageBitmapResult.Success(bmp.asImageBitmap())
        } catch (e: Exception) {
            ImageBitmapResult.Failed(e.message ?: "QR encode failed")
        }
    }
}

sealed interface ImageBitmapResult {
    data class Success(val bitmap: androidx.compose.ui.graphics.ImageBitmap) : ImageBitmapResult
    data class Failed(val message: String) : ImageBitmapResult
}
