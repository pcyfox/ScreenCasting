package com.pcyfox.screen.activity

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.blankj.utilcode.constant.PermissionConstants
import com.blankj.utilcode.util.PermissionUtils
import com.blankj.utilcode.util.ToastUtils
import com.pcyfox.screen.R
import com.pcyfox.screen.Publisher
import com.pcyfox.screen.service.ScreenRecorderService
import kotlinx.android.synthetic.main.activity_screen_record.*


class ScreenRecordDemoActivity : AppCompatActivity(), View.OnClickListener {
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
//        val w = ScreenUtils.getAppScreenWidth()
//        val h = ScreenUtils.getAppScreenHeight()

        val w = 1920
        val h = 1080
         et_w.setText(w.toString())
        et_h.setText(h.toString())
        val r = (w * h * 0.8).toInt()
        et_bitrate.setText(r.toString())
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


    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val w = Integer.parseInt(et_w.text.toString())
        val h = Integer.parseInt(et_h.text.toString())
        val r = Integer.parseInt(et_bitrate.text.toString())
        val fps = Integer.parseInt(et_fps.text.toString())

        if (data == null) {
            ToastUtils.showShort("intent data=null!")
        } else {
            ScreenRecorderService.start(
                this,
                resultCode,
                data,
                w,
                h,
                fps,
                r,
                Publisher.MULTI_CAST_IP,
                Publisher.TARGET_PORT,
            )
        }

    }


}
