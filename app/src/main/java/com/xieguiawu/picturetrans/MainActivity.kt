package com.xieguiawu.picturetrans

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.xieguiawu.picturetrans.ui.MainScreen
import com.xieguiawu.picturetrans.ui.PictureTransTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PictureTransTheme {
                MainScreen(vm)
            }
        }
    }

    /** 用户可能从系统设置页改完权限返回，回到前台时重新检查（官方 best practice）。 */
    override fun onResume() {
        super.onResume()
        vm.refreshPermission()
    }
}
