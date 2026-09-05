package com.xieguiawu.picturetrans

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Activity 入口烟测（skill 铁律：测试必须覆盖真实入口，而非仅组件）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivitySmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test fun launches_andShowsStartUI() {
        compose.onNodeWithText("Picture Trans").assertIsDisplayed()
        compose.onNodeWithText("启动").assertIsDisplayed()
    }

    @Test fun showsPortField() {
        compose.onNodeWithText("端口").assertIsDisplayed()
    }
}
