package com.xieguiawu.picturetrans.media

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 媒体权限判定测试（2026-09-06 真机授权死循环根因回归）：
 * - required() 必须按 SDK 分流——API 33 请求 READ_MEDIA_VISUAL_USER_SELECTED
 *   （API 34 才存在）会被系统静默拒绝；API 29-32 请求 WRITE_EXTERNAL_STORAGE
 *   （manifest maxSdkVersion=28）同样静默拒绝。
 * - access() 必须区分 Full / Partial / Denied，旧版 required().all 判定
 *   把「部分授权」和「拒绝」混为一谈，导致授权卡片永不消失。
 */
@RunWith(RobolectricTestRunner::class)
class MediaPermissionsTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    private fun grantAll() {
        shadowOf(context).grantPermissions(*MediaPermissions.required())
    }

    private fun grant(vararg perms: String) {
        shadowOf(context).grantPermissions(*perms)
    }

    @Test
    @Config(sdk = [34])
    fun sdk34_required_includesVisualUserSelected() {
        val req = MediaPermissions.required().toList()
        assertTrue("android.permission.READ_MEDIA_VISUAL_USER_SELECTED" in req)
        assertTrue(android.Manifest.permission.READ_MEDIA_IMAGES in req)
        assertTrue(android.Manifest.permission.READ_MEDIA_VIDEO in req)
        assertEquals(3, req.size)
    }

    @Test
    @Config(sdk = [34])
    fun sdk34_fullAccess_whenBothMediaGranted() {
        grant(
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VIDEO,
        )
        assertEquals(MediaAccess.Full, MediaPermissions.access(context))
    }

    @Test
    @Config(sdk = [34])
    fun sdk34_partialAccess_whenOnlyUserSelectedGranted() {
        // 部分授权场景：用户在系统弹窗选「选择照片」→ 只有 VISUAL_USER_SELECTED 授予
        grant("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
        assertEquals(MediaAccess.Partial, MediaPermissions.access(context))
        // Partial 也算「可用」——旧 bug 就是把这种状态当成了未授权
        assertTrue(MediaPermissions.granted(context))
    }

    @Test
    @Config(sdk = [34])
    fun sdk34_denied_whenNothingGranted() {
        assertEquals(MediaAccess.Denied, MediaPermissions.access(context))
    }

    @Test
    @Config(sdk = [33])
    fun sdk33_required_excludesVisualUserSelected() {
        val req = MediaPermissions.required().toList()
        // 🔴 核心回归点：API 33 上绝不能请求 API 34 才引入的权限（静默拒绝 → 死循环）
        assertTrue("android.permission.READ_MEDIA_VISUAL_USER_SELECTED" !in req)
        assertEquals(
            listOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
            ),
            req,
        )
    }

    @Test
    @Config(sdk = [33])
    fun sdk33_fullAccess_whenBothMediaGranted() {
        grant(
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VIDEO,
        )
        assertEquals(MediaAccess.Full, MediaPermissions.access(context))
    }

    @Test
    @Config(sdk = [31])
    fun sdk31_required_isReadOnly() {
        // 🔴 核心回归点：API 29-32 不能请求 WRITE（manifest maxSdkVersion=28，静默拒绝）
        assertEquals(
            listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
            MediaPermissions.required().toList(),
        )
    }

    @Test
    @Config(sdk = [31])
    fun sdk31_fullAccess_whenReadGranted() {
        grant(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        assertEquals(MediaAccess.Full, MediaPermissions.access(context))
    }

    @Test
    @Config(sdk = [28])
    fun sdk28_required_includesReadWrite() {
        val req = MediaPermissions.required().toList()
        assertTrue(android.Manifest.permission.READ_EXTERNAL_STORAGE in req)
        assertTrue(android.Manifest.permission.WRITE_EXTERNAL_STORAGE in req)
    }

    @Test
    @Config(sdk = [28])
    fun sdk28_readOnlyIsPartial() {
        // API<29 上传需要写权限：只读时列表可用但上传受限，归为 Partial
        grant(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        assertEquals(MediaAccess.Partial, MediaPermissions.access(context))
    }

    @Test
    @Config(sdk = [28])
    fun sdk28_readWriteIsFull() {
        grantAll()
        assertEquals(MediaAccess.Full, MediaPermissions.access(context))
    }
}
