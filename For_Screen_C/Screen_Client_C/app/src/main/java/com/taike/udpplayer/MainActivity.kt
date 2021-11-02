package com.taike.udpplayer

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.view.Window
import android.view.WindowManager
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
        /*set it to be no title*/
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        /*set it to be full screen*/
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main)

        initPlayer()
        startPlay()

    }
    @SuppressLint("ClickableViewAccessibility")
    private fun initPlayer(){
        playerView = findViewById(R.id.view_mcpv)
        val btnGroup = findViewById<View>(R.id.group_btn)
        btnGroup.visibility = View.GONE
        findViewById<View>(R.id.view_root).setOnTouchListener { v, event ->
            btnGroup.visibility =
                if (btnGroup.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            false
        }
    }

    private fun startPlay(){
        playerView?.post {
            onStartClick(playerView!!)
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