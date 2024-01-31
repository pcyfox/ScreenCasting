package com.df.screenserver.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.blankj.utilcode.constant.PermissionConstants
import com.blankj.utilcode.util.PermissionUtils
import com.blankj.utilcode.util.ToastUtils
import com.df.lib_push.Publisher
import com.df.lib_push.VideoEncodeParam
import com.df.lib_push.service.ScreenRecorderService
import com.df.screenserver.R
import com.df.screenserver.dialog.PlayerDialog
import kotlinx.android.synthetic.main.activity_screen_record.btn_start_screen
import kotlinx.android.synthetic.main.activity_screen_record.container
import kotlinx.android.synthetic.main.activity_screen_record.et_bitrate
import kotlinx.android.synthetic.main.activity_screen_record.et_fps
import kotlinx.android.synthetic.main.activity_screen_record.et_h
import kotlinx.android.synthetic.main.activity_screen_record.et_q
import kotlinx.android.synthetic.main.activity_screen_record.et_udp_max_len
import kotlinx.android.synthetic.main.activity_screen_record.et_w
import kotlinx.android.synthetic.main.activity_screen_record.tvTime
import kotlinx.android.synthetic.main.activity_screen_record.whiteboard
import java.text.SimpleDateFormat
import java.util.Date


class ScreenRecordDemoActivity : FragmentActivity(), View.OnClickListener {
    private val TAG = "ScreenRecordDemoActivit"
    private val REQUEST_CODE = 202

    private val colors =
        arrayListOf(Color.RED, Color.BLACK, Color.BLUE, Color.CYAN, Color.GREEN, Color.DKGRAY)
    private var index = 0


    private var timer: TimeCountDown? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_screen_record)

        PermissionUtils.permission(
            PermissionConstants.STORAGE, PermissionConstants.ACTIVITY_RECOGNITION
        ).request()
        initTestVideo()
    }

    override fun onStart() {
        super.onStart()
        startTimer()
    }

    override fun onStop() {
        super.onStop()
        timer?.cancel()
    }

    private fun startTimer() {
        timer?.cancel()
        timer = TimeCountDown(Long.MAX_VALUE, 100, tvTime)
        timer?.start()
    }

    private fun initTestVideo() {
//        val w = ScreenUtils.getScreenWidth()
//        val h = ScreenUtils.getScreenHeight()

//        val w = 1920
//        val h = 1080

        val w = 1280
        val h = 720

        et_w.setText(w.toString())
        et_h.setText(h.toString())
        //val bitrate =1.2*1024*1024

        val fps = et_fps.text.toString().toInt()
        val q = et_q.text.toString().toFloat()
        et_udp_max_len.setText((Publisher.MAX_PKT_LEN).toString())
        // et_udp_max_len.setText((3000).toString())
        val bitRate: Int = (w * h * fps * q).toInt()
        et_bitrate.setText(bitRate.toString())
    }


    override fun onClick(view: View) {
        when (view.id) {
            R.id.btn_change_color -> {
                val i = index++ % (colors.size - 1)
                container.setBackgroundColor(colors[i])
            }

            R.id.btn_clear -> whiteboard.board.clear()

            R.id.btn_video -> {
                PlayerDialog().show(supportFragmentManager, "123")
            }

            R.id.btn_start_screen -> {
                if (btn_start_screen.text.toString() == "START") {
                    btn_start_screen.text = "STOP"
                    requestMediaProjectionManager()
                } else {
                    btn_start_screen.text = "START"
                    ScreenRecorderService.stop(this)
                }
            }
        }
    }

    private fun requestMediaProjectionManager() {
        startActivityForResult(
            (getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent(),
            REQUEST_CODE
        )
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(
            TAG,
            "onActivityResult() called with: requestCode = $requestCode, resultCode = $resultCode, data = $data"
        )

        if (data == null) {
            ToastUtils.showShort("请允许录制/投射您的屏幕！")
            return
        }

        val w = Integer.parseInt(et_w.text.toString())
        val h = Integer.parseInt(et_h.text.toString())

        val bitrate = et_bitrate.text.toString().toFloat()
        val fps = Integer.parseInt(et_fps.text.toString())

        val maxUdpPktLen = Integer.parseInt(et_udp_max_len.text.toString())

        val encodeParam = VideoEncodeParam(
            w, h, fps, resources.displayMetrics.densityDpi, bitrate.toInt()
        )

        Log.d(
            TAG,
            "onActivityResult() called with: requestCode = $requestCode, encodeParam= $encodeParam"
        )

        ScreenRecorderService.start(
            this,
            resultCode,
            data,
            encodeParam,
            maxUdpPktLen,
            Publisher.MULTI_CAST_IP,
            Publisher.TARGET_PORT,
        )
    }
}


class TimeCountDown(millisInFuture: Long, countDownInterval: Long, val view: TextView) :
    CountDownTimer(millisInFuture, countDownInterval) {
    @SuppressLint("SimpleDateFormat")
    override fun onTick(millisUntilFinished: Long) {
        view.post {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS")
            view.text = sdf.format(Date())
        }
    }

    override fun onFinish() {
    }
}
