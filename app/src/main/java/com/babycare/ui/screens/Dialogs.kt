package com.babycare.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FeedingDialog(onDismiss: () -> Unit, onSave: (String, Int, Int, Long) -> Unit) {
    var type by remember { mutableStateOf("FORMULA") }
    var amount by remember { mutableStateOf("0") }
    var duration by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录喂奶") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("配方奶量 (ml)") })
                OutlinedTextField(value = duration, onValueChange = { duration = it }, label = { Text("母乳时长 (min)") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(type, amount.toIntOrNull() ?: 0, duration.toIntOrNull() ?: 0, System.currentTimeMillis())
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun ExcretionDialog(onDismiss: () -> Unit, onSave: (String, String, Long) -> Unit) {
    var type by remember { mutableStateOf("POOP") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录排泄") },
        text = {
            Column {
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(type, note, System.currentTimeMillis())
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}