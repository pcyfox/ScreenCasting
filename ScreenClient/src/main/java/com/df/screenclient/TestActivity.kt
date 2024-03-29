package com.df.screenclient

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class TestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)
        window.decorView.postDelayed({
            startActivity(Intent(this, ClientMainActivity::class.java))
        }, 300)
    }

    fun onStartClick(v: View) {
        startActivity(Intent(this, ClientMainActivity::class.java))
    }

}