package com.babycare.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import com.babycare.ui.theme.BabyBlue

data class FeedingRecord(val timestamp: Long, val type: String, val amount: Int = 0) // 简化

@Composable
fun WeeklyChart(feedings: List<FeedingRecord>) {
    Column {
        Text("本周喂养趋势", modifier = Modifier.padding(bottom = 8.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            // 简化柱状图实现（实际可替换为MPAndroidChart或更完善Compose Chart）
            val max = feedings.maxOfOrNull { it.amount } ?: 100
            // ... 绘制逻辑省略为示例，可扩展
            drawIntoCanvas { }
        }
    }
}