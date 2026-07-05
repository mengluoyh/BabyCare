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
        val dailyData: Map<String, Pair<Int, Int>>,
        val chartDays: Int,
        val density: Float,
        /** 左/底部柱子颜色（argb） */
        val barColor1: Int,
        /** 右/顶部柱子颜色（argb） */
        val barColor2: Int,
        /** 左/底部柱子数值标签颜色 */
        val labelColor1: Int,
        /** 右/顶部柱子数值标签颜色 */
        val labelColor2: Int,
        /** 图例文字，可用 %1\$d %2\$d 占位符接收 total1 total2 */
        val legendFormat: String,
        val total1: Int,
        val total2: Int,
        /** true=左右并排，false=上下堆叠（默认false） */
        val sideBySide: Boolean = false
    )

    fun draw(config: ChartConfig) {
        val (ctx, chartContainer, columnsContainer, dailyData, chartDays, density,
             barC1, barC2, labelC1, labelC2, legendFmt, total1, total2, sideBySide) = config

        val maxVal = dailyData.values.maxOfOrNull { maxOf(it.first, it.second) } ?: 1
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
                setBackgroundColor(0x15FFFFFF.toInt())
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
            val (v1, v2) = data  // v1 = 左/底部柱子, v2 = 右/顶部柱子
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                setPadding(1, 0, 1, 0)
            }

            if (sideBySide) {
                // ─── 左右并排模式，每根柱子带独立数值标签 ───
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.BOTTOM
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                // 左柱（分类1）
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

                // 右柱（分类2）
                val rightCol = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                if (v2 > 0) {
                    rightCol.addView(TextView(ctx).apply {
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
                rightCol.addView(bar2)
                row.addView(rightCol)

                col.addView(row)
            } else {
                // ─── 上下堆叠模式（原逻辑） ───
                // 底部柱子（分类1）
                val bar1 = View(ctx).apply {
                    val h = if (maxVal > 0) (v1.toFloat() / maxVal * barAreaHeight).toInt() else 0
                    layoutParams = LinearLayout.LayoutParams(barWidth.coerceAtLeast(4), h.coerceAtLeast(2))
                    background = GradientDrawable().apply {
                        setColor(barC1)
                        cornerRadius = 4f * density
                    }
                }
                // 顶部柱子（分类2）
                val bar2 = View(ctx).apply {
                    val h = if (maxVal > 0) (v2.toFloat() / maxVal * barAreaHeight).toInt() else 0
                    layoutParams = LinearLayout.LayoutParams(barWidth.coerceAtLeast(4), h.coerceAtLeast(2))
                    background = GradientDrawable().apply {
                        setColor(barC2)
                        cornerRadius = 4f * density
                    }
                }

                // 数值标签（从上往下加：先加顶部标签，再加底部标签）
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
                setTextColor(0xFF000000.toInt())
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
