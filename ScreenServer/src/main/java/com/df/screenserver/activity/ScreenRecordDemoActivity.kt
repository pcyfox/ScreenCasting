package com.df.screenserver.activity

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import com.blankj.utilcode.constant.PermissionConstants
import com.blankj.utilcode.util.PermissionUtils
import com.blankj.utilcode.util.ToastUtils
import com.df.lib_push.Publisher
import com.df.lib_push.VideoEncodeParam
import com.df.screenserver.R
import com.df.lib_push.service.ScreenRecorderService
import kotlinx.android.synthetic.main.activity_screen_record.*


class ScreenRecordDemoActivity : FragmentActivity(), View.OnClickListener {
    private val TAG = "ScreenRecordDemoActivit"
    private val REQUEST_CODE = 202
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_screen_record)

        PermissionUtils.permission(
            PermissionConstants.STORAGE,
            PermissionConstants.ACTIVITY_RECOGNITION
        ).request()

        initTestVideo()
    }

    private fun initTestVideo() {
        vv_test.setVideoPath("/sdcard/test.mp4")
        vv_test.setOnPreparedListener {
            vv_test.start()
            it.isLooping = true

        }

//        val w = ScreenUtils.getScreenWidth()
//        val h = ScreenUtils.getScreenHeight()

        val w = 1920
        val h = 1080

        et_w.setText(w.toString())
        et_h.setText(h.toString())
        //val bitrate =1.2*1024*1024

        val fps = et_fps.text.toString().toInt()
        val bitRate: Int = (w * h * fps * 0.05).toInt()

        et_udp_max_len.setText((com.df.lib_push.Publisher.MAX_PKT_LEN).toString())
        et_bitrate.setText(bitRate.toString())

    }


    override fun onClick(view: View) {
        when (view.id) {
            R.id.btn_start_screen -> {
                if (btn_start_screen.text.toString() == "START") {
                    btn_start_screen.text = "STOP"

                    startActivityForResult(
                        (getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent(),
                        REQUEST_CODE
                    )

                } else {
                    btn_start_screen.text = "START"
                    ScreenRecorderService.stop(this)
                }
            }
        }
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

        ScreenRecorderService.start(
            this,
            resultCode,
            data,
            VideoEncodeParam(
                w,
                h,
                fps,
                resources.displayMetrics.densityDpi,
                bitrate.toInt()
            ),

            maxUdpPktLen,
            Publisher.MULTI_CAST_IP,
            Publisher.TARGET_PORT,
        )

    }


}
