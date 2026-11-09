package com.jongjun.hangeoreum.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jongjun.hangeoreum.MainActivity
import com.jongjun.hangeoreum.data.AppData
import com.jongjun.hangeoreum.data.ExcelPlan
import com.jongjun.hangeoreum.data.ExcelPlanner
import com.jongjun.hangeoreum.data.Store
import com.jongjun.hangeoreum.data.Xlsx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Download the Excel template / upload a filled one. */
@Composable
fun ExcelCard(data: AppData) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<ExcelPlan?>(null) }
    val imported = data.events.count { it.source == ExcelPlanner.SOURCE } +
        data.goals.count { it.source == ExcelPlanner.SOURCE } + data.routines.count { it.source == ExcelPlanner.SOURCE }

    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.assets.open("ufp_template.xlsx").use { input ->
                        ctx.contentResolver.openOutputStream(uri)!!.use { input.copyTo(it) }
                    }
                }
            }
            status = if (r.isSuccess) "양식을 저장했어요. 채워서 다시 올려주세요" else "저장하지 못했어요"
        }
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    Xlsx.read(bytes)
                }
            }
            r.onSuccess { sheets ->
                val plan = ExcelPlanner.plan(sheets, Store.current)
                if (plan.events.isEmpty() && plan.goals.isEmpty() && plan.routines.isEmpty()) {
                    status = "넣을 내용을 찾지 못했어요" + (plan.warnings.firstOrNull()?.let { " · $it" } ?: "")
                } else pending = plan
            }.onFailure { status = "엑셀 파일을 읽지 못했어요 (.xlsx만 돼요)" }
        }
    }

    CardBox {
        SectionTitle("엑셀로 한 번에 넣기")
        T(
            if (imported > 0) "지금 엑셀에서 넣은 항목 ${imported}개" else "양식에 일정·목표·반복 할 일을 적어서 올리면 한 번에 들어가요",
            14, color = pal.sub,
        )
        status?.let { T(it, 14, Modifier.padding(top = 6.dp), weight = FontWeight.SemiBold, color = pal.blue) }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("양식 저장", Modifier.weight(1f)) {
                (ctx as? MainActivity)?.expectExternal()
                runCatching { saver.launch("UFP_양식.xlsx") }
            }
            PrimaryButton("엑셀 업로드", Modifier.weight(1f)) {
                (ctx as? MainActivity)?.expectExternal()
                runCatching { opener.launch(arrayOf("*/*")) }
            }
        }
        T(
            "다시 올리면 이전에 엑셀·옵시디언으로 넣은 항목만 새 내용으로 바뀌어요", 12,
            Modifier.padding(top = 10.dp), color = pal.sub,
        )
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = pal.card,
            title = { T("이대로 넣을까요?", 19, weight = FontWeight.Bold) },
            text = {
                Column {
                    T("일정 ${p.events.size}개 · 목표 ${p.goals.size}개 · 반복 ${p.routines.size}개", 16, weight = FontWeight.SemiBold)
                    T("이전에 엑셀·옵시디언으로 넣은 항목은 이 내용으로 바뀌어요", 14, Modifier.padding(top = 6.dp), color = pal.sub)
                    if (p.warnings.isNotEmpty()) {
                        T("건너뛴 줄 ${p.warnings.size}개", 14, Modifier.padding(top = 10.dp), weight = FontWeight.SemiBold, color = pal.red)
                        p.warnings.take(5).forEach { T("· $it", 12, color = pal.sub) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Store.applyExcel(p)
                    Store.ensureRoutines()
                    status = "넣었어요: 일정 ${p.events.size} · 목표 ${p.goals.size} · 반복 ${p.routines.size}"
                    pending = null
                }) { T("넣기", 16, weight = FontWeight.SemiBold, color = pal.blue) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { T("취소", 16, weight = FontWeight.SemiBold, color = pal.sub) }
            },
        )
    }
}
