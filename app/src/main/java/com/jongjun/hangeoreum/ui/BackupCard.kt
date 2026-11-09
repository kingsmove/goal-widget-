package com.jongjun.hangeoreum.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import com.jongjun.hangeoreum.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Save all data to a file the user keeps, or restore from one. */
@Composable
fun BackupCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = Store.exportJson()
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("x") }
            }
            status = if (r.isSuccess) "백업 파일을 저장했어요" else "저장하지 못했어요"
        }
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("x") }
            }
            r.onSuccess { pendingRestore = it }.onFailure { status = "파일을 열 수 없어요" }
        }
    }

    CardBox {
        SectionTitle("데이터 백업")
        T("앱을 업데이트해도 기록은 그대로예요. 다만 앱을 지우거나 폰을 바꾸면 사라지니까 가끔 백업 파일로 저장해두세요.", 14, color = pal.sub)
        status?.let { T(it, 14, Modifier.padding(top = 6.dp), weight = FontWeight.SemiBold, color = pal.blue) }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("불러오기", Modifier.weight(1f)) {
                (ctx as? MainActivity)?.expectExternal()
                runCatching { opener.launch(arrayOf("*/*")) }
            }
            PrimaryButton("백업 저장", Modifier.weight(1f)) {
                (ctx as? MainActivity)?.expectExternal()
                runCatching { saver.launch("UFP-backup-${LocalDate.now()}.json") }
            }
        }
    }

    pendingRestore?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            containerColor = pal.card,
            title = { T("백업으로 되돌릴까요?", 19, weight = FontWeight.Bold) },
            text = { T("지금 앱에 있는 기록이 백업 파일 내용으로 바뀌어요.", 15, color = pal.sub) },
            confirmButton = {
                TextButton(onClick = {
                    status = Store.importJson(text).fold({ "백업을 불러왔어요" }, { it.message ?: "불러오지 못했어요" })
                    pendingRestore = null
                }) { T("되돌리기", 16, weight = FontWeight.SemiBold, color = pal.red) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { T("취소", 16, weight = FontWeight.SemiBold, color = pal.sub) }
            },
        )
    }
}
