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
}
