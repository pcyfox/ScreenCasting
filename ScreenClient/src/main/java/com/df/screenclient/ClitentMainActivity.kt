package com.df.screenclient

import android.media.MediaCodec
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.pcyfox.lib_udp_player.MultiCastPlayerView
import com.pcyfox.lib_udp_player.PlayState

class ClientMainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    private val maxFrameLen = 4 * 1024 * 1024 //视频帧大小限制
    private val multiCastHost = "239.0.0.200"
    private val videoPort = 9527
    private var progressBar: ProgressBar? = null


    private var playerView: MultiCastPlayerView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /*set it to be no title*/
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        /*set it to be full screen*/
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        setContentView(R.layout.activity_main)
        playerView = findViewById(R.id.view_mcpv)
        progressBar = findViewById(R.id.progressBar)
        autoPlay()
//        playerView?.postDelayed({
//            finish()
//        }, 3000)
    }

    private fun autoPlay() {
        playerView?.run {
            isVisible = true
            configAndStart()
        }
    }


    private fun configAndStart() {
        playerView?.run {
            if (isPlaying) {
                Log.e(TAG, "configAndStart() called player is playing")
                return
            }
            config(multiCastHost, videoPort, maxFrameLen)

            setOnDecodeStateChangeListener {
                Log.d(TAG, "onVisibilityClick() called state:$it")
                if (it == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    runOnUiThread {
                        progressBar?.isVisible = false
                    }
                }
            }

            postDelayed({
                startPlay()
            }, 200)
        }
    }


    fun onStartClick(v: View) {
        configAndStart()
    }

    fun onVisibilityClick(v: View) {
        playerView?.run {
            this.isVisible = !this.isVisible

            if (isPlaying && !isVisible) {
                stopPlay()
            } else {
                configAndStart()
            }
        }

    }

    override fun onStop() {
        super.onStop()
        playerView?.stopPlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        playerView?.release()
    }
}