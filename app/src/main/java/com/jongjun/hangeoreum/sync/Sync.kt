package com.jongjun.hangeoreum.sync

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.jongjun.hangeoreum.data.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SyncState(val email: String? = null, val status: String = "", val busy: Boolean = false)

/**
 * Login + two-way sync with Firestore. Same layout as the PC version:
 * users/{uid}/items/{coll~id | meta} = { json: "<item JSON>", t: <ms>, del: <bool> }
 */
object Sync {
    private const val API_KEY = "AIzaSyA3scnHu8KLSGlOdHznx4QOZccfGuZKGhw"
    private const val APP_ID = "1:30400442323:android:b8f56f51b54f3e9c1e4926"
    private const val PROJECT_ID = "ultimate-final-kingsmove"
    private val COLLS = listOf("goals", "sessions", "events", "routines", "subjects", "quotes", "scores")

    private lateinit var app: Context
    private var auth: FirebaseAuth? = null
    private var db: FirebaseFirestore? = null
    private var listener: ListenerRegistration? = null
    private val main = Handler(Looper.getMainLooper())
    private var applying = false
    private val pushTask = Runnable { push() }

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state

    private val prefs get() = app.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private var synced: Map<String, String>
        get() = runCatching {
            val o = JSONObject(prefs.getString("synced", "{}")!!)
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
        set(v) { prefs.edit().putString("synced", JSONObject(v).toString()).apply() }
    private var pulled: Long
        get() = prefs.getLong("pulled", 0L)
        set(v) { prefs.edit().putLong("pulled", v).apply() }

    private fun now() = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date())
    private fun status(s: String, busy: Boolean = false) {
        _state.value = _state.value.copy(email = auth?.currentUser?.email, status = s, busy = busy)
    }

    fun init(ctx: Context) {
        app = ctx.applicationContext
        runCatching {
            val fb = FirebaseApp.getApps(app).firstOrNull() ?: FirebaseApp.initializeApp(
                app,
                FirebaseOptions.Builder().setApiKey(API_KEY).setApplicationId(APP_ID).setProjectId(PROJECT_ID).build(),
            )
            auth = FirebaseAuth.getInstance(fb)
            db = FirebaseFirestore.getInstance(fb)
            auth!!.addAuthStateListener { a ->
                listener?.remove(); listener = null
                if (a.currentUser != null) start() else status("로그인하면 PC와 같은 데이터를 써요")
            }
        }.onFailure { status("동기화를 시작하지 못했어요") }
    }

    private fun col(): CollectionReference? {
        val uid = auth?.currentUser?.uid ?: return null
        return db?.collection("users")?.document(uid)?.collection("items")
    }

    // ---------- data <-> per-item map ----------
    private fun split(full: JSONObject): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        val meta = JSONObject()
        full.keys().forEach { k ->
            if (k in COLLS) {
                val arr = full.optJSONArray(k) ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.opt("id")?.toString() ?: continue
                    m["$k~$id"] = o.toString()
                }
            } else meta.put(k, full.get(k))
        }
        m["meta"] = meta.toString()
        return m
    }

    private fun join(m: Map<String, String>): JSONObject {
        val full = m["meta"]?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        val arrs = COLLS.associateWith { JSONArray() }
        m.forEach { (k, v) ->
            if (k == "meta") return@forEach
            val c = k.substringBefore('~')
            arrs[c]?.put(runCatching { JSONObject(v) }.getOrNull() ?: return@forEach)
        }
        arrs.forEach { (c, a) -> full.put(c, a) }
        return full
    }

    private fun localMap(): Map<String, String> = split(JSONObject(Store.exportJson()))

    private fun applyRemote(m: Map<String, String>) {
        applying = true
        try {
            Store.importJson(join(m).toString())
        } finally {
            applying = false
        }
    }

    // ---------- flow ----------
    private fun start() {
        val c = col() ?: return
        if (synced.isEmpty()) {
            status("처음 동기화 중…", true)
            c.get().addOnSuccessListener { snap ->
                val remote = HashMap<String, String>()
                var maxT = 0L
                snap.documents.forEach { d ->
                    maxT = maxOf(maxT, d.getLong("t") ?: 0L)
                    if (d.getBoolean("del") != true) d.getString("json")?.let { remote[d.id] = it }
                }
                val merged = LinkedHashMap(localMap())
                merged.putAll(remote) // server wins; things only this phone has stay
                if (remote.isNotEmpty()) applyRemote(merged)
                synced = localMap().filterKeys { it in remote }
                pulled = maxT
                push()
                listen()
            }.addOnFailureListener { e -> status("동기화 오류: ${e.message}") }
        } else listen()
    }

    private fun listen() {
        val c = col() ?: return
        val since = maxOf(0L, pulled - 5 * 60_000L)
        listener = c.whereGreaterThan("t", since).addSnapshotListener { snap, err ->
            if (err != null) { status("동기화 오류: ${err.message}"); return@addSnapshotListener }
            if (snap == null) return@addSnapshotListener
            val m = LinkedHashMap(localMap())
            val s = HashMap(synced)
            var changed = false
            var maxT = pulled
            val touched = mutableListOf<String>()
            snap.documentChanges.forEach { ch ->
                val d = ch.document
                if (d.metadata.hasPendingWrites()) return@forEach
                maxT = maxOf(maxT, d.getLong("t") ?: 0L)
                if (d.getBoolean("del") == true) {
                    if (m.remove(d.id) != null) changed = true
                    s.remove(d.id)
                } else {
                    val j = d.getString("json") ?: return@forEach
                    if (m[d.id] != j) { m[d.id] = j; changed = true }
                    touched += d.id
                }
            }
            pulled = maxT
            if (changed) {
                applyRemote(m)
                val after = localMap()
                touched.forEach { id -> after[id]?.let { s[id] = it } }
            }
            synced = s
            status("동기화됨 · ${now()}")
        }
    }

    /** Called after every local change; uploads shortly after. */
    fun onLocalChange() {
        if (applying || auth?.currentUser == null) return
        main.removeCallbacks(pushTask)
        main.postDelayed(pushTask, 1200)
    }

    fun push() {
        val c = col() ?: return
        val fs = db ?: return
        val local = localMap()
        val old = synced
        val t = System.currentTimeMillis()
        val ops = mutableListOf<Pair<String, Map<String, Any>>>()
        local.forEach { (k, v) -> if (old[k] != v) ops += k to mapOf("json" to v, "t" to t, "del" to false) }
        old.keys.forEach { k -> if (k !in local) ops += k to mapOf("json" to "", "t" to t, "del" to true) }
        if (ops.isEmpty()) { status("동기화됨 · ${now()}"); return }
        status("올리는 중…", true)
        // Firestore queues writes offline and sends them later, so mark them synced right away
        ops.chunked(400).forEach { chunk ->
            val b = fs.batch()
            chunk.forEach { (k, v) -> b.set(c.document(k), v) }
            b.commit()
                .addOnSuccessListener { status("동기화됨 · ${now()}") }
                .addOnFailureListener { e -> status("올리지 못했어요: ${e.message}") }
        }
        synced = local
    }

    fun signIn(email: String, pw: String, create: Boolean, onError: (String) -> Unit) {
        val a = auth ?: return onError("동기화를 시작하지 못했어요")
        val task = if (create) a.createUserWithEmailAndPassword(email, pw) else a.signInWithEmailAndPassword(email, pw)
        status(if (create) "가입하는 중…" else "로그인 중…", true)
        task.addOnFailureListener { e ->
            val code = (e as? FirebaseAuthException)?.errorCode ?: ""
            onError(
                when (code) {
                    "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD" -> "이메일이나 비밀번호가 맞지 않아요"
                    "ERROR_USER_NOT_FOUND" -> "가입된 이메일이 아니에요"
                    "ERROR_EMAIL_ALREADY_IN_USE" -> "이미 가입된 이메일이에요. 로그인해주세요"
                    "ERROR_WEAK_PASSWORD" -> "비밀번호는 6자 이상이어야 해요"
                    "ERROR_INVALID_EMAIL" -> "이메일 형식을 확인해주세요"
                    else -> e.message ?: "로그인하지 못했어요"
                }
            )
            status("")
        }
    }

    fun resetPassword(email: String, done: (Boolean) -> Unit) {
        auth?.sendPasswordResetEmail(email)?.addOnCompleteListener { done(it.isSuccessful) } ?: done(false)
    }

    fun signOut() {
        listener?.remove(); listener = null
        auth?.signOut()
        synced = emptyMap(); pulled = 0L
        status("로그아웃했어요")
    }
}
