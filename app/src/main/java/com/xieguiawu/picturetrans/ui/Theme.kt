package com.xieguiawu.picturetrans.ui

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// 全深色：无视系统主题，恒为深色（2026-09-06 用户要求）
@Composable
fun PictureTransTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= 31) {
        dynamicDarkColorScheme(context)
    } else {
        darkColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
