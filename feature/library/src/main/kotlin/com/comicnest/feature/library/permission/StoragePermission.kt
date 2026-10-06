package com.comicnest.feature.library.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/**
 * 全部文件访问权限（D2：`MANAGE_EXTERNAL_STORAGE`）。
 * API 30+ 需用户授予"所有文件访问权限"；更低版本依赖已声明的 `READ_EXTERNAL_STORAGE`，视为已授权。
 */
object StoragePermission {

    fun isGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    /** 跳转系统"所有文件访问权限"设置页（仅 API 30+ 有意义）。 */
    fun openSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val intent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            // 个别 ROM 不支持带 data 的意图，回退到通用设置页
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}
