package com.xieguiawu.picturetrans.ui

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// 全深色：无视系统主题，恒为深色（2026-09-06 用户要求）
//
// Surface 不是可选的装饰：MaterialTheme 只换 colorScheme 令牌，不会改
// LocalContentColor，根层默认仍是 Color.Black。于是任何不在 Card/Button 里的
// 裸 Text 都会深底黑字（实测：首页标题「Picture Trans」看不见）。
// 包一层 Surface 让根层 content color = onBackground，一次性堵住这类 bug。
@Composable
fun PictureTransTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= 31) {
        dynamicDarkColorScheme(context)
    } else {
        darkColorScheme()
    }
    MaterialTheme(colorScheme = scheme) {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}
