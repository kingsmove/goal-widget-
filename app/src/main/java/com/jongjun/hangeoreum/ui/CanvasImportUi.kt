package com.jongjun.hangeoreum.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jongjun.hangeoreum.MainActivity
import com.jongjun.hangeoreum.data.AppData
import com.jongjun.hangeoreum.data.CanvasImport
import com.jongjun.hangeoreum.data.CanvasPlan
import com.jongjun.hangeoreum.data.Event
import kotlinx.coroutines.launch

@Composable
fun eventColorOf(e: Event): Color = e.customColor?.let { Color(it) } ?: itemColor(e.color)

/** Two ways to bring an Obsidian timeline canvas into the app. */
@Composable
fun CanvasImportCard(data: AppData) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val count = data.events.count { it.source == CanvasPlan.SOURCE }

    fun report(r: Result<Int>) {
        status = r.fold({ "플랜 ${it}개를 가져왔어요" }, { it.message ?: "가져오지 못했어요" })
        busy = false
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) { busy = false; return@rememberLauncherForActivityResult }
        scope.launch { report(CanvasImport.fromUri(ctx, uri)) }
    }

    CardBox {
        SectionTitle("옵시디언 플랜 연동")
        T(
            if (count > 0) "지금 옵시디언에서 가져온 항목 ${count}개" else "옵시디언 캔버스(.canvas)의 월 칸과 블록을 그대로 플랜으로 옮겨요",
            14, color = pal.sub,
        )
        status?.let { T(it, 14, Modifier.padding(top = 6.dp), weight = FontWeight.SemiBold, color = pal.blue) }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("파일 선택", Modifier.weight(1f)) {
                if (busy) return@SecondaryButton
                busy = true; status = null
                (ctx as? MainActivity)?.expectExternal()
                runCatching { picker.launch(arrayOf("*/*")) }.onFailure { busy = false; status = "파일 선택 창을 열 수 없어요" }
            }
            PrimaryButton(if (busy) "가져오는 중…" else "PC에서 동기화", Modifier.weight(1f)) {
                if (busy) return@PrimaryButton
                busy = true; status = null
                scope.launch { report(CanvasImport.fromRepo()) }
            }
        }
        T(
            "다시 가져오면 옵시디언 항목만 새로 바뀌고, 앱에서 직접 만든 일정은 그대로예요", 12,
            Modifier.padding(top = 10.dp), color = pal.sub,
        )
    }
}
