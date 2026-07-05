// BabyCare/app/src/main/java/com/babycare/util/CountdownNotificationReceiver.kt
package com.babycare.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 接收倒计时通知栏操作：暂停/继续/取消。
 * 通过 EventBus 或直接设置静态引用路由到 ViewModel 无法跨进程，
 * 因此使用全局 Application 实例持有当前 ViewModel 引用来派发操作。
 */
class CountdownNotificationReceiver : BroadcastReceiver() {

    companion object {
        /** 由 TimerFragment 在 onViewCreated 时设置 */
        var onPause: (() -> Unit)? = null
        var onResume: (() -> Unit)? = null
        var onClear: (() -> Unit)? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.babycare.PAUSE" -> onPause?.invoke()
            "com.babycare.RESUME" -> onResume?.invoke()
            "com.babycare.CLEAR" -> onClear?.invoke()
        }
    }
}