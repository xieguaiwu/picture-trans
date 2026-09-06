package com.xieguiawu.picturetrans

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the real UI under Robolectric (layoutlib NATIVE graphics) and writes
 * PNGs into the fastlane store-metadata tree.
 *
 * Why not `captureToImage()`: androidx.compose.ui.test's window capture calls
 * `forceRedraw()` which waits for a real Choreographer frame and times out
 * under Robolectric (`ComposeTimeoutException: Condition still not satisfied
 * after 2000 ms`). Drawing the decor view into a Canvas works instead.
 *
 * Run explicitly when store screenshots need refreshing:
 *   ./gradlew :app:testDebugUnitTest \
 *       --tests "*StoreScreenshotsTest" -PstoreScreenshots
 *
 * Skipped unless `-PstoreScreenshots` is passed, so normal CI stays fast and
 * committed screenshots are only replaced on purpose.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreScreenshotsTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val enabled: Boolean
        get() = System.getProperty("storeScreenshots") == "true"

    private fun outDir(): File {
        // user.dir is the :app module directory during unit tests.
        val root = File(System.getProperty("user.dir", ".")).parentFile ?: File(".")
        return File(root, "fastlane/metadata/android/en-US/images/phoneScreenshots").also {
            it.mkdirs()
        }
    }

    private fun shoot(name: String) {
        if (!enabled) return
        val view: View = compose.activity.window.decorView
        compose.waitForIdle()
        val w = view.width.takeIf { it > 0 } ?: 822
        val h = view.height.takeIf { it > 0 } ?: 1782
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val f = File(outDir(), name)
        FileOutputStream(f).use { o ->
            check(bmp.compress(Bitmap.CompressFormat.PNG, 100, o))
        }
        println("WROTE ${f.path} ${bmp.width}x${bmp.height}")
    }

    @Test
    fun s1_idle_home() {
        compose.onNodeWithText("Picture Trans").assertIsDisplayed()
        shoot("1.png")
    }

    @Test
    fun s2_server_running_with_qr() {
        compose.onNodeWithText("启动").performClick()
        // The CIO server binds on a real thread; poll for the Running card
        // instead of guessing a frame count.
        compose.waitUntil(timeoutMillis = 25_000) {
            compose.onAllNodes(hasText("停止"), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        shoot("2.png")
    }
}
