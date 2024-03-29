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
import kotlinx.android.synthetic.main.activity_main.ll_btns

class ClientMainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private val maxFrameLen = 4 * 1024 * 1024 //视频帧大小限制
    private val multiCastHost = "239.0.0.200"
    private val videoPort = 9527
    private var progressBar: ProgressBar? = null
    private var playerView: MultiCastPlayerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate() called with: savedInstanceState ---------")
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
        ll_btns.isVisible = BuildConfig.DEBUG
    }

    override fun onResume() {
        super.onResume()
        autoPlay()
    }

    private fun autoPlay() {
        playerView?.run {
            isVisible = true
            configAndStart()
        }
    }

    private fun showProgress(isShow: Boolean = true) {
        runOnUiThread {
            progressBar?.isVisible = isShow
        }
    }

    private fun configAndStart() {
        Log.d(TAG, "--------configAndStart() called------")
        showProgress()
        playerView?.run {
            if (isPlaying) {
                Log.e(TAG, "configAndStart() called player is playing")
                return
            }
            config(multiCastHost, videoPort, maxFrameLen)
            setOnDecodeStateChangeListener {
                Log.d(TAG, "onVisibilityClick() called state:$it")
                if (it == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    showProgress(false)
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

    fun onStopClick(v: View) {
        playerView?.stopPlay()
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

    fun onFinishClick(v: View) {
        finish()
    }

    override fun onPause() {
        super.onPause()
        playerView?.pause()
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