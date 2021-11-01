package com.taike.udpplayer

import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.taike.lib_udp_player.MultiCastPlayer
import com.taike.lib_udp_player.MultiCastPlayerView

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private val maxFrameLen = 4 * 1024 * 1024 //视频帧大小限制
    private val multiCastHost = "239.0.0.200"
    private val videoPort = 2021
    private var playerView: MultiCastPlayerView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        playerView = findViewById(R.id.view_mcpv)
        val btnGroup = findViewById<View>(R.id.group_btn)
        playerView?.post {
            onStartClick(playerView!!)
            btnGroup.visibility = View.GONE
        }

        val root = findViewById<View>(R.id.view_root)
        root.setOnTouchListener { v, event ->
            btnGroup.visibility =
                if (btnGroup.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            false
        }
    }


    private fun configAndStart() {
        playerView?.run {
            if (isPlaying) {
                Log.e(TAG, "configAndStart() called player is playing")
                return;
            }
            config(multiCastHost, videoPort, maxFrameLen)
            postDelayed({
                playerView?.startPlay()
            }, 100)
        }
    }

    fun onStopClick(v: View) {
        playerView?.stopPlay()
    }

    fun onStartClick(v: View) {
        configAndStart()
    }
}