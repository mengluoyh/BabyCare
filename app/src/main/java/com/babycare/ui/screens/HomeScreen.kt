package com.babycare.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.babycare.ui.components.WeeklyChart
import com.babycare.ui.theme.BabyBlue
import com.babycare.ui.theme.MintGreen
import com.babycare.ui.theme.WarmYellow
import com.babycare.util.AgeCalculator
import com.babycare.util.ShortcutUtil
import com.babycare.viewmodel.BabyViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: BabyViewModel = viewModel()) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val feedings by viewModel.todayFeedings.collectAsStateWithLifecycle()
    val excretions by viewModel.todayExcretions.collectAsStateWithLifecycle()
    val weekFeedings by viewModel.weekFeedings.collectAsStateWithLifecycle()

    var showFeedDialog by remember { mutableStateOf(false) }
    var showExcretionDialog by remember { mutableStateOf(false) }
    var showEditProfile by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(permission)
    }

    val bgFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "BabyCareTheme/cover.png")

    Scaffold(
        topBar = { TopAppBar(title = { Text("BabyCare") }, colors = TopAppBarDefaults.topAppBarColors(BabyBlue)) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                ExtendedFloatingActionButton(onClick = { showFeedDialog = true }, containerColor = BabyBlue, icon = { Icon(Icons.Default.Add, null) }, text = { Text("记喂奶") })
                Spacer(Modifier.height(8.dp))
                ExtendedFloatingActionButton(onClick = { showExcretionDialog = true }, containerColor = MintGreen, icon = { Icon(Icons.Default.Add, null) }, text = { Text("记排泄") })
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 年龄区
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("宝宝成长", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        IconButton(onClick = { showEditProfile = true }) { Icon(Icons.Default.Edit, null) }
                    }
                    profile?.let { p ->
                        val (months, weeks, days) = AgeCalculator.calculateAge(p.birthDate)
                        Text("当前已 ${months}月 ${weeks}周 ${days}天", fontSize = 20.sp, color = BabyBlue)
                    }
                }
            }

            // 喂养汇总
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("今日喂养", fontWeight = FontWeight.Bold)
                    val formulaTotal = feedings.filter { it.type == "FORMULA" }.sumOf { it.amount }
                    val breastCount = feedings.count { it.type == "BREAST" }
                    Text("配方奶: $formulaTotal ml | 母乳: $breastCount 次")
                    WeeklyChart(weekFeedings)
                }
            }

            // 排泄
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("今日排泄", fontWeight = FontWeight.Bold)
                    val poop = excretions.count { it.type == "POOP" }
                    val pee = excretions.count { it.type == "PEE" }
                    Text("排便 $poop 次 | 排尿 $pee 次")
                }
            }

            Button(onClick = {
                val path = if (hasPermission && bgFile.exists()) bgFile.absolutePath else null
                ShortcutUtil.createCustomShortcut(context, path, profile?.name ?: "宝宝")
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(WarmYellow)) {
                Text("生成桌面图标")
            }
        }
    }

    if (showFeedDialog) FeedingDialog(onDismiss = { showFeedDialog = false }, onSave = { type, amount, dur, time -> viewModel.addFeeding(type, amount, dur, time); showFeedDialog = false })
    if (showExcretionDialog) ExcretionDialog(onDismiss = { showExcretionDialog = false }, onSave = { type, note, time -> viewModel.addExcretion(type, note, time); showExcretionDialog = false })
    if (showEditProfile) {
        AlertDialog(onDismissRequest = { showEditProfile = false }, title = { Text("编辑") }, confirmButton = { TextButton(onClick = { showEditProfile = false }) { Text("确定") } })
    }
}