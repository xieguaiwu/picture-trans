package com.xieguiawu.picturetrans.util

import android.content.Context
import java.security.SecureRandom
import android.util.Base64

/** 访问 token 持久化：12 随机字节 → base64url 16 字符。 */
object TokenStore {

    private const val FILE = "picture_trans_prefs"
    private const val KEY_TOKEN = "share_token"
    private const val KEY_PORT = "server_port"
    const val DEFAULT_PORT = 8765

    fun getOrCreate(context: Context): String {
        val sp = prefs(context)
        sp.getString(KEY_TOKEN, null)?.let { return it }
        val bytes = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        sp.edit().putString(KEY_TOKEN, token).apply()
        return token
    }

    fun reset(context: Context): String {
        prefs(context).edit().remove(KEY_TOKEN).apply()
        return getOrCreate(context)
    }

    fun port(context: Context): Int = prefs(context).getInt(KEY_PORT, DEFAULT_PORT)

    fun setPort(context: Context, port: Int) {
        prefs(context).edit().putInt(KEY_PORT, clampPort(port)).apply()
    }

    fun clampPort(port: Int): Int = port.coerceIn(1024, 65535)

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
