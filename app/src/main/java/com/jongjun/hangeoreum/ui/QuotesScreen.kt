package com.jongjun.hangeoreum.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jongjun.hangeoreum.data.AppData
import com.jongjun.hangeoreum.data.Dates
import com.jongjun.hangeoreum.data.Quote
import com.jongjun.hangeoreum.data.Store
import kotlinx.coroutines.launch

/** The quote shown today: changes once a day, stays the same all day. */
fun quoteOfDay(d: AppData): Quote? =
    if (d.quotes.isEmpty()) null else d.quotes.sortedBy { it.createdAt }[Math.floorMod(Dates.today(), d.quotes.size.toLong()).toInt()]

/** Home card: today's line from the collection. */
@Composable
fun QuoteCard(data: AppData, open: () -> Unit) {
    val q = quoteOfDay(data)
    CardBox(Modifier.bounceClick(onClick = open)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("들었던 좋은 말", 14, Modifier.weight(1f), weight = FontWeight.SemiBold, color = pal.sub)
            T(if (q == null) "+ 적기" else "${data.quotes.size}개 →", 13, weight = FontWeight.SemiBold, color = pal.blue)
        }
        if (q == null) {
            T("누군가에게 들은 힘이 되는 말을 모아두세요", 16, Modifier.padding(top = 8.dp), weight = FontWeight.Medium, color = pal.tertiary)
        } else {
            T("“${q.text}”", 18, Modifier.padding(top = 8.dp), weight = FontWeight.SemiBold, maxLines = 4)
            if (q.who.isNotBlank()) T("— ${q.who}", 14, Modifier.padding(top = 6.dp), color = pal.sub)
        }
    }
}

@Composable
fun QuotesScreen(data: AppData, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<Quote?>(null) }
    var sheet by remember { mutableStateOf(false) }
    val list = data.quotes.sortedByDescending { it.createdAt }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.AutoMirrored.Filled.ArrowBack, onBack, bg = pal.bg)
            T("좋은 말 모음", 18, Modifier.padding(start = 6.dp).weight(1f), weight = FontWeight.Bold)
            IconCircle(Icons.Filled.Add, { editing = null; sheet = true }, tint = pal.blue, bg = pal.card)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (list.isEmpty()) {
                item {
                    CardBox {
                        EmptyNote("아직 없어요\n오른쪽 위 + 로 들었던 좋은 말을 적어보세요")
                    }
                }
            }
            items(list, key = { it.id }) { q ->
                CardBox(Modifier.bounceClick { editing = q; sheet = true }) {
                    T("“${q.text}”", 18, weight = FontWeight.SemiBold)
                    Row(Modifier.padding(top = 10.dp)) {
                        T(if (q.who.isNotBlank()) "— ${q.who}" else "", 14, Modifier.weight(1f), color = pal.sub)
                        T(Dates.full(q.day), 13, color = pal.tertiary)
                    }
                }
            }
        }
    }

    if (sheet) QuoteSheet(editing) { sheet = false; editing = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuoteSheet(q: Quote?, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(q?.text ?: "") }
    var who by remember { mutableStateOf(q?.who ?: "") }
    fun close() { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = pal.card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
                .navigationBarsPadding().imePadding().padding(bottom = 16.dp)
        ) {
            T(if (q == null) "좋은 말 적기" else "좋은 말 수정", 22, weight = FontWeight.Bold)
            FieldLabel("들은 말")
            InputField(text, { text = it }, "예: 너는 결국 해낼 사람이야", size = 18, singleLine = false)
            FieldLabel("누가 (선택)")
            InputField(who, { who = it }, "예: 팀장님", size = 16)
            VGap(28.dp)
            PrimaryButton(if (q == null) "저장하기" else "수정하기", Modifier.fillMaxWidth(), enabled = text.isNotBlank()) {
                Store.upsertQuote(
                    Quote(
                        id = q?.id ?: Store.newId(), text = text.trim(), who = who.trim(),
                        day = q?.day ?: Dates.today(), createdAt = q?.createdAt ?: System.currentTimeMillis(),
                    )
                )
                close()
            }
            if (q != null) {
                VGap(8.dp)
                SecondaryButton("삭제", Modifier.fillMaxWidth(), textColor = pal.red) { Store.deleteQuote(q.id); close() }
            }
        }
    }
}
