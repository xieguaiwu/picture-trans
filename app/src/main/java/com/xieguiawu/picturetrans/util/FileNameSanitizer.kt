package com.xieguiawu.picturetrans.util

/**
 * 上传文件名清理：去目录成分、去控制字符与文件系统非法字符。
 * 中文/emoji 等合法 Unicode 原样保留（关卡 12：字符安全）。
 */
object FileNameSanitizer {
    private val ILLEGAL = Regex("[\\p{Cntrl}/\\\\:*?\"<>|]")

    fun sanitize(raw: String): String {
        var name = raw.substringAfterLast('/').substringAfterLast('\\')
        name = name.replace(ILLEGAL, "_").trim().trimStart('.', ' ')
        if (name.isEmpty()) name = "file"
        if (name.length > 150) {
            val dot = name.lastIndexOf('.')
            name = if (dot > 0 && dot < name.length - 1) {
                val base = name.substring(0, minOf(dot, 120))
                val ext = name.substring(dot, minOf(name.length, dot + 16))
                base + ext
            } else {
                name.take(150)
            }
        }
        return name
    }
}
