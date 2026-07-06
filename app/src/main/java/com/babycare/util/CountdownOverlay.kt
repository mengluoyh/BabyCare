// BabyCare/app/src/main/java/com/babycare/util/CountdownOverlay.kt
package com.babycare.util

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Color
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * 倒计时悬浮窗：在 App 处于后台时在屏幕顶部显示倒计时时间。
 * 需要 SYSTEM_ALERT_WINDOW 权限（Android 6+ 需用户在设置中手动授予）。
 */
object CountdownOverlay {

    private var overlayView: TextView? = null
    private var windowManager: WindowManager? = null

    /** 检查当前是否有悬浮窗权限 */
    fun hasPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    /** 显示或更新倒计时悬浮窗 */
    fun show(context: Context, text: String) {
        if (overlayView != null) {
            overlayView?.text = text
            return
        }
        if (!hasPermission(context)) return

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 60 // 距离顶部 60px
        }

        overlayView = TextView(context).apply {
            this.text = text
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(200, 0, 0, 0))
            gravity = Gravity.CENTER
            setPadding(16, 8, 16, 8)
            elevation = 10f
        }

        try {
            windowManager?.addView(overlayView, params)
        } catch (_: Exception) {
            overlayView = null
        }
    }

    /** 检查悬浮窗是否正在显示 */
    fun isShowing(): Boolean = overlayView != null

    /** 更新悬浮窗文本 */
    fun update(text: String) {
        overlayView?.text = text
    }

    /** 隐藏悬浮窗 */
    fun hide() {
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
        windowManager = null
    }
}