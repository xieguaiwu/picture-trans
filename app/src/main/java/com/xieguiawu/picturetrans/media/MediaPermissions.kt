package com.xieguiawu.picturetrans.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** 媒体访问级别：Full=全部照片/视频；Partial=仅用户选中的部分（Android 14+）；Denied=未授权。 */
enum class MediaAccess { Full, Partial, Denied }

/** 媒体读取权限：API 33+ 细分媒体权限，旧系统 READ_EXTERNAL_STORAGE。 */
object MediaPermissions {

    // 权限请求与判定逻辑对照官方文档：
    // developer.android.com/about/versions/14/changes/partial-photo-video-access
    //
    // 两个历史缺陷（2026-09-06 真机授权死循环根因）：
    // 1. READ_MEDIA_VISUAL_USER_SELECTED 是 Android 14 (API 34) 引入的权限——
    //    API 33 上请求不存在的权限会被静默拒绝（无弹窗），旧代码却对 API 33+ 一律请求它；
    // 2. WRITE_EXTERNAL_STORAGE 在 manifest 限 maxSdkVersion=28——API 29-32 上
    //    请求它同样静默拒绝，旧代码却把它塞进了 29+ 的请求数组。

    /**
     * 运行时应请求的权限，按系统版本分流。
     * API 34+：三个权限一起请求（官方要求单次操作，弹窗才含「选择照片」入口）；
     * API 33：只请求两个细分读权限；
     * API 29-32：只读（scoped storage 下写公共目录无需权限，走 IS_PENDING 流程）；
     * API 26-28：读 + 写（上传需写公共 Download 目录）。
     */
    fun required(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
        Build.VERSION.SDK_INT >= 29 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
    }

    /**
     * 当前媒体访问级别（官方四段判定）。注意：授权状态可能在设置页被用户随时修改，
     * 不得持久化缓存，应在 onResume 等时机实时调用本方法。
     */
    fun access(context: Context): MediaAccess {
        fun granted(permission: String): Boolean =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        return when {
            // 全量授权：细分读权限任一授予即全量
            Build.VERSION.SDK_INT >= 33 &&
                (granted(Manifest.permission.READ_MEDIA_IMAGES) || granted(Manifest.permission.READ_MEDIA_VIDEO)) ->
                MediaAccess.Full
            // 部分授权（仅 Android 14+ 存在该权限）：可列出用户选中的媒体
            Build.VERSION.SDK_INT >= 34 && granted("android.permission.READ_MEDIA_VISUAL_USER_SELECTED") ->
                MediaAccess.Partial
            // 旧系统：读是列表/传输的底线；API<29 上传还需写权限，只读时视为部分可用
            Build.VERSION.SDK_INT < 29 ->
                when {
                    granted(Manifest.permission.READ_EXTERNAL_STORAGE) &&
                        granted(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> MediaAccess.Full
                    granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> MediaAccess.Partial
                    else -> MediaAccess.Denied
                }
            granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> MediaAccess.Full
            else -> MediaAccess.Denied
        }
    }

    /** 是否已授予可用的媒体访问（Full 或 Partial）。 */
    fun granted(context: Context): Boolean = access(context) != MediaAccess.Denied
}
