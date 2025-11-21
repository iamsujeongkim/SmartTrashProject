package com.example.smarttrashproject2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.AdapterView
import android.widget.GridView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class RecycleActivity : AppCompatActivity() {

    private lateinit var gridView: GridView
    private val recycleItems = arrayOf("플라스틱", "유리", "종이")
    private val recycleIcons = intArrayOf(
        R.drawable.plastic,
        R.drawable.glass,
        R.drawable.paper
    )

    // RTDB URL (뒤에 슬래시 없음)
    private val DB_URL = "https://smarttrashproject-1a495-default-rtdb.firebaseio.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // super 먼저
        setContentView(R.layout.activity_recycle)

        // 1) 앱 진입 시 미리 익명 로그인(최초 1회면 충분)
        ensureSignedIn()

        val db = FirebaseDatabase.getInstance(DB_URL)
        val ref = db.getReference("RecycleCategories")
        Log.d("DBPATH", "ref = $ref")

        // 2) 헬스체크 쓰기 (로그인 이후에만)
        runAfterSignedIn {
            val health = mapOf("ping" to "ok", "ts" to System.currentTimeMillis())
            ref.child("healthcheck").setValue(health)
                .addOnSuccessListener { Log.d("DB", "✅ healthcheck saved") }
                .addOnFailureListener { e -> Log.e("DB", "❌ healthcheck failed", e) }
        }

        gridView = findViewById(R.id.gridView)
        val adapter = GridAdapter(this, recycleItems, recycleIcons)
        gridView.adapter = adapter

        // 3) 클릭 시: 화면 전환은 즉시, DB 저장은 비동기(성공/실패와 무관하게 전환)
        gridView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selected = recycleItems[position]
            Log.d("CLICK", "item clicked: $selected")

            // (A) 먼저 화면 전환
            startActivity(Intent(this, CameraActivity::class.java).apply {
                putExtra("category", selected)
            })

            // (B) 저장은 뒤에서 비동기로 진행
            val payload = mapOf(
                "category" to selected,
                "timestamp" to System.currentTimeMillis()
            )

            runAfterSignedIn {
                val newRef = ref.push()
                Log.d("DBPATH", "newRef = $newRef")
                newRef.setValue(payload)
                    .addOnSuccessListener {
                        Log.d("DB", "✅ saved: $selected")
                        // 필요 시 토스트만: 사용자 UX 방해 안 되게 조용히 처리해도 됨
                        // Toast.makeText(this, "저장 완료: $selected", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Log.e("DB", "❌ save failed", e)
                        // 필요하면 가벼운 토스트
                        // Toast.makeText(this, "DB 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    /** 익명 로그인 보장용 래퍼 */
    private fun runAfterSignedIn(block: () -> Unit) {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            block()
        } else {
            auth.signInAnonymously()
                .addOnSuccessListener { block() }
                .addOnFailureListener { e ->
                    Log.e("Auth", "익명 로그인 실패", e)
                    Toast.makeText(this, "로그인 실패: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    /** 앱 진입 시 한 번 트리거해서 미리 로그인 */
    private fun ensureSignedIn() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener { Log.d("Auth", "익명 로그인 성공") }
                .addOnFailureListener { e ->
                    Log.e("Auth", "익명 로그인 실패", e)
                    Toast.makeText(this, "로그인 실패: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}
