package com.example.smarttrashproject2

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase

class ResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        val db = FirebaseDatabase.getInstance(
            "https://smarttrashproject-1a495-default-rtdb.firebaseio.com/"
        )
        val ref = db.getReference("RecycleCategories")

        val imageView: ImageView = findViewById(R.id.imageViewResult)
        val btnRetake: Button = findViewById(R.id.btnRetake)
        val btnConfirm: Button = findViewById(R.id.btnConfirm)

        val imageUriString = intent.getStringExtra("imageUri")
        if (imageUriString != null) {
            val imageUri = Uri.parse(imageUriString)
            imageView.setImageURI(imageUri)
        } else {
            Toast.makeText(this, "이미지를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
        }

        btnRetake.setOnClickListener {
            finish()
        }

        btnConfirm.setOnClickListener {
            // 이후 AI 분석이나 서버 전송 등 기능 연결
        }
    }
}
