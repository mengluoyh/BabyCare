// BabyCare/app/src/main/java/com/babycare/util/Constants.kt
package com.babycare.util

object Constants {
    const val BACKUP_DIR = "/storage/emulated/0/BabyCare/backups"
    const val EXPORT_DIR = "/storage/emulated/0/BabyCare"
    const val BACKUP_PREFIX = "babycare_backup_"
    const val BACKUP_SUFFIX = ".json"

    // ─── WebDAV 远程目录（以包名命名） ───
    const val WEBDAV_DIR = "babycare"

    // ─── 喂养类型常量 ───
    const val FEED_BREAST = "breast"
    const val FEED_BOTTLE_BREAST = "bottle_breast"
    const val FEED_FORMULA = "formula"

    /** 判断是否需要记录奶量 */
    fun needsVolume(feedType: String): Boolean = feedType != FEED_BREAST

    /** 喂养类型的显示标签 */
    fun feedTypeLabel(feedType: String): String = when (feedType) {
        FEED_BREAST -> "亲喂"
        FEED_BOTTLE_BREAST -> "瓶喂母乳"
        FEED_FORMULA -> "配方奶"
        else -> feedType
    }

    /** 喂养类型的图标前缀 */
    fun feedTypeIcon(feedType: String): String = when (feedType) {
        FEED_BREAST -> "🤱"
        FEED_BOTTLE_BREAST -> "🍶"
        FEED_FORMULA -> "🍼"
        else -> ""
    }
}
