// BabyCare/app/src/main/java/com/babycare/util/ChartDrawer.kt
package com.babycare.util

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 柱状趋势图绘制工具。
 * 负责绘制网格线、双层柱状图、数值标签、日期标签、图例和入场动画。
 *
 * 使用方式：先在 Fragment 中准备好 dailyData（日期 → (分类1值, 分类2值)），
 * 再调用 [draw] 即可。
 */
object ChartDrawer {

    data class ChartConfig(
        val context: Context,
        val chartContainer: ViewGroup,
        val columnsContainer: ViewGroup,
        val dailyData: Map<String, Triple<Int, Int, Int>>,
        val chartDays: Int,
        val density: Float,
        /** 左/底部柱子颜色（argb） */
        val barColor1: Int,
        /** 中/顶部柱子颜色（argb） */
        val barColor2: Int,
        /** 右柱子颜色（argb，第三系列） */
        val barColor3: Int = barColor2,
        /** 左/底部柱子数值标签颜色 */
        val labelColor1: Int,
        /** 中/顶部柱子数值标签颜色 */
        val labelColor2: Int,
        /** 右柱子数值标签颜色（第三系列） */
        val labelColor3: Int = labelColor2,
        /** 图例文字，可用 %1\$d %2\$d %3\$d 占位符接收 total1 total2 total3 */
        val legendFormat: String,
        val total1: Int,
        val total2: Int,
        val total3: Int = 0,
        /** true=三列并排，false=上下堆叠（默认false） */
        val sideBySide: Boolean = false
    )

    fun draw(config: ChartConfig) {
        val (ctx, chartContainer, columnsContainer, dailyData, chartDays, density,
             barC1, barC2, barC3, labelC1, labelC2, labelC3, legendFmt, total1, total2, total3, sideBySide) = config

        val maxVal = dailyData.values.maxOfOrNull { maxOf(it.first, maxOf(it.second, it.third)) } ?: 1
        val barWidth = (Math.max(8, 24 - chartDays)).toInt() * density.toInt()
        val chartHeight = chartContainer.height.coerceAtLeast(100)
        val barAreaHeight = (chartHeight * 0.75f).toInt()

        columnsContainer.removeAllViews()
        chartContainer.removeAllViews()
        chartContainer.addView(columnsContainer)

        // ─── 网格线 ───
        val gridLevels = listOf(0.25f, 0.5f, 0.75f, 1.0f)
        val gridContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.BOTTOM
        }
        for (level in gridLevels) {
            val spacer = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f - level
                )
            }
            val line = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (0.5f * density).toInt()
                )
                setBackgroundColor(android.graphics.Color.argb(48, 0, 0, 0)) // 半透明黑，深浅色主题均可见
            }
            gridContainer.addView(spacer)
            gridContainer.addView(line)
        }
        gridContainer.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        })
        chartContainer.addView(gridContainer, 0)

        // ─── 柱状图 ───
        for ((date, data) in dailyData) {
            val (v1, v2, v3) = data  // v1 = 左/底部柱子(亲喂次数), v2 = 中(配方奶ml), v3 = 右(瓶喂母乳ml)
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                setPadding(1, 0, 1, 0)
            }

            if (sideBySide) {
                // ─── 三列并排模式，每根柱子带独立数值标签 ───
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.BOTTOM
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                // 左柱（分类1：亲喂次数）
                val leftCol = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                if (v1 > 0) {
                    leftCol.addView(TextView(ctx).apply {
                        text = v1.toString()
                        textSize = 8f
                        gravity = Gravity.CENTER
                        setTextColor(labelC1)
                    })
                }
                val bar1 = View(ctx).apply {
                    val h = if (maxVal > 0) (v1.toFloat() / maxVal * barAreaHeight).toInt() else 0
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, h.coerceAtLeast(2)
                    )
                    background = GradientDrawable().apply {
                        setColor(barC1)
                        cornerRadius = 4f * density
                    }
                }
                leftCol.addView(bar1)
                row.addView(leftCol)

                // 中柱（分类2：配方奶ml）
                val midCol = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                if (v2 > 0) {
                    midCol.addView(TextView(ctx).apply {
                        text = v2.toString()
                        textSize = 8f
                        gravity = Gravity.CENTER
                        setTextColor(labelC2)
                    })
                }
                val bar2 = View(ctx).apply {
                    val h = if (maxVal > 0) (v2.toFloat() / maxVal * barAreaHeight).toInt() else 0
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, h.coerceAtLeast(2)
                    )
                    background = GradientDrawable().apply {
                        setColor(barC2)
                        cornerRadius = 4f * density
                    }
                }
                midCol.addView(bar2)
                row.addView(midCol)

                // 右柱（分类3：瓶喂母乳ml）
                val rightCol = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                if (v3 > 0) {
                    rightCol.addView(TextView(ctx).apply {
                        text = v3.toString()
                        textSize = 8f
                        gravity = Gravity.CENTER
                        setTextColor(labelC3)
                    })
                }
                val bar3 = View(ctx).apply {
                    val h = if (maxVal > 0) (v3.toFloat() / maxVal * barAreaHeight).toInt() else 0
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, h.coerceAtLeast(2)
                    )
                    background = GradientDrawable().apply {
                        setColor(barC3)
                        cornerRadius = 4f * density
                    }
                }
                rightCol.addView(bar3)
                row.addView(rightCol)

                col.addView(row)
            } else {
                // ─── 上下堆叠模式（三系列堆叠） ───
                // 最底部柱子（分类1：亲喂次数）
                val bar1 = View(ctx).apply {
                    val h = if (maxVal > 0) (v1.toFloat() / maxVal * barAreaHeight).toInt() else 0
                    layoutParams = LinearLayout.LayoutParams(barWidth.coerceAtLeast(4), h.coerceAtLeast(2))
                    background = GradientDrawable().apply {
                        setColor(barC1)
                        cornerRadius = 4f * density
                    }
                }
                // 中部柱子（分类2：配方奶ml）
                val bar2 = View(ctx).apply {
                    val h = if (maxVal > 0) (v2.toFloat() / maxVal * barAreaHeight).toInt() else 0
                    layoutParams = LinearLayout.LayoutParams(barWidth.coerceAtLeast(4), h.coerceAtLeast(2))
                    background = GradientDrawable().apply {
                        setColor(barC2)
                        cornerRadius = 4f * density
                    }
                }
                // 顶部柱子（分类3：瓶喂母乳ml）
                val bar3 = View(ctx).apply {
                    val h = if (maxVal > 0) (v3.toFloat() / maxVal * barAreaHeight).toInt() else 0
                    layoutParams = LinearLayout.LayoutParams(barWidth.coerceAtLeast(4), h.coerceAtLeast(2))
                    background = GradientDrawable().apply {
                        setColor(barC3)
                        cornerRadius = 4f * density
                    }
                }

                // 数值标签（从上往下加：先加顶部标签，再加中部、底部标签）
                if (v3 > 0) {
                    col.addView(TextView(ctx).apply {
                        text = v3.toString()
                        textSize = 8f
                        gravity = Gravity.CENTER
                        setTextColor(labelC3)
                    })
                }
                if (v2 > 0) {
                    col.addView(TextView(ctx).apply {
                        text = v2.toString()
                        textSize = 8f
                        gravity = Gravity.CENTER
                        setTextColor(labelC2)
                    })
                }
                if (v1 > 0) {
                    col.addView(TextView(ctx).apply {
                        text = v1.toString()
                        textSize = 8f
                        gravity = Gravity.CENTER
                        setTextColor(labelC1)
                    })
                }

                col.addView(bar1)
                col.addView(bar2)
                col.addView(bar3)
            }

            // 日期标签
            val showLabel = if (chartDays > 15) {
                date.endsWith("/01") || date.endsWith("/15") ||
                        date == dailyData.keys.first() || date == dailyData.keys.last()
            } else true
            col.addView(TextView(ctx).apply {
                text = if (showLabel) date else ""
                textSize = 7f
                gravity = Gravity.CENTER
                setTextColor(androidx.core.content.ContextCompat.getColor(ctx, com.babycare.R.color.on_background))
            })

            columnsContainer.addView(col)
        }

        // ─── 更新图例 ───
        // 找到 legend TextView（从 chartContainer 的兄弟中查找，由调用方传入）
        // 改为由调用方自行更新 legend

        // ─── 进场动画 ───
        columnsContainer.post {
            for (i in 0 until columnsContainer.childCount) {
                val col = columnsContainer.getChildAt(i) as? LinearLayout ?: continue
                for (j in 0 until col.childCount) {
                    val v = col.getChildAt(j)
                    if (v.height > 0) {
                        v.pivotY = v.height.toFloat()
                        v.scaleY = 0f
                        v.animate().scaleY(1f).setDuration(300).startDelay = (i * 30).toLong()
                    }
                }
            }
        }
    }
}
