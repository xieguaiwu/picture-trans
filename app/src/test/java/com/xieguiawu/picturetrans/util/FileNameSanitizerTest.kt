package com.xieguiawu.picturetrans.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNameSanitizerTest {

    @Test fun `removes path traversal`() {
        assertEquals("passwd", FileNameSanitizer.sanitize("../../etc/passwd"))
        assertEquals("secret.txt", FileNameSanitizer.sanitize("..\\..\\secret.txt"))
    }

    @Test fun `windows drive and directories`() {
        val out = FileNameSanitizer.sanitize("C:\\Users\\test\\a.png")
        assertEquals("a.png", out)
        assertEquals("b.jpg", FileNameSanitizer.sanitize("/tmp/x/b.jpg"))
    }

    @Test fun `keeps cjk and emoji`() {
        assertEquals("照片 📷.png", FileNameSanitizer.sanitize("照片 📷.png"))
    }

    @Test fun `control and illegal chars become underscore`() {
        assertEquals("a_b.png", FileNameSanitizer.sanitize("a\u0000b.png"))
        assertEquals("x_y.png", FileNameSanitizer.sanitize("x:y.png"))
    }

    @Test fun `empty becomes file`() {
        assertEquals("file", FileNameSanitizer.sanitize(""))
        assertEquals("file", FileNameSanitizer.sanitize(".."))
        assertEquals("file", FileNameSanitizer.sanitize("///"))
    }

    @Test fun `long name keeps extension`() {
        val longName = "ф".repeat(200) + ".jpg"
        val out = FileNameSanitizer.sanitize(longName)
        assertTrue("length ${out.length}", out.length <= 136)
        assertTrue(out.endsWith(".jpg"))
    }

    @Test fun `leading dots stripped`() {
        // 只去首部点号（防隐藏文件与 . / ..），中间点号保留
        assertEquals("hidden..a.txt", FileNameSanitizer.sanitize(".hidden..a.txt"))
    }
}
