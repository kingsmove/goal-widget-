package com.jongjun.hangeoreum.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jongjun.hangeoreum.sync.Sync

/** Email login so the phone and the PC version share one set of data. */
@Composable
fun SyncCard() {
    val ctx = LocalContext.current
    val st by Sync.state.collectAsState()
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    val toast = { m: String -> Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show() }

    CardBox {
        SectionTitle("계정 · 동기화")
        if (st.email == null) {
            T("PC 버전과 같은 이메일로 로그인하면 폰·PC가 같은 데이터를 써요. 처음이면 ‘가입하기’를 눌러요.", 14, color = pal.sub)
            FieldLabel("이메일")
            InputField(email, { email = it.trim() }, "you@example.com", size = 17, email = true)
            FieldLabel("비밀번호 (6자 이상)")
            InputField(pw, { pw = it }, "비밀번호", size = 17, password = true)
            if (st.status.isNotBlank()) T(st.status, 13, Modifier.padding(top = 10.dp), color = pal.blue, weight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("가입하기", Modifier.weight(1f)) {
                    if (email.isBlank() || pw.length < 6) toast("이메일과 6자 이상 비밀번호를 적어주세요")
                    else Sync.signIn(email, pw, true) { toast(it) }
                }
                PrimaryButton("로그인", Modifier.weight(1f), enabled = !st.busy) {
                    if (email.isBlank() || pw.isEmpty()) toast("이메일과 비밀번호를 적어주세요")
                    else Sync.signIn(email, pw, false) { toast(it) }
                }
            }
            T(
                "비밀번호를 잊었어요", 13,
                Modifier.padding(top = 12.dp).bounceClick {
                    if (email.isBlank()) toast("이메일을 먼저 적어주세요")
                    else Sync.resetPassword(email) { ok -> toast(if (ok) "비밀번호 재설정 메일을 보냈어요" else "메일을 보내지 못했어요") }
                },
                color = pal.sub, weight = FontWeight.SemiBold,
            )
        } else {
            T(st.email ?: "", 16, weight = FontWeight.Bold)
            T(st.status, 13, Modifier.padding(top = 4.dp), color = pal.sub)
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("로그아웃", Modifier.weight(1f)) { Sync.signOut() }
                PrimaryButton("지금 올리기", Modifier.weight(1f)) { Sync.push() }
            }
        }
    }
}
