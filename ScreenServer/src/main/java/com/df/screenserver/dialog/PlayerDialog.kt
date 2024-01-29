package com.df.screenserver.dialog

import android.util.Log
import androidx.fragment.app.DialogFragment
import com.blankj.utilcode.constant.PermissionConstants
import com.blankj.utilcode.util.PathUtils
import com.blankj.utilcode.util.PermissionUtils
import com.df.screenserver.R
import kotlinx.android.synthetic.main.dialog_player.tv_close
import kotlinx.android.synthetic.main.dialog_player.tv_url
import kotlinx.android.synthetic.main.dialog_player.videoView

class PlayerDialog() : DialogFragment(R.layout.dialog_player) {
    private val TAG = "PlayerDialog"

    override fun onStart() {
        super.onStart()
        PermissionUtils.permission(PermissionConstants.STORAGE)
            .callback { isAllGranted, granted, deniedForever, denied -> if (isAllGranted) onAllPermissionGranted() }
            .request()
    }

    private fun onAllPermissionGranted() {
        startPlay()
    }

    private fun startPlay() {
        val tempPath = PathUtils.getExternalAppFilesPath()
        val videoPath = "$tempPath/test.mp4"
        Log.d(TAG, "startPlay() called:$videoPath")
        tv_url.text = videoPath
        tv_close.setOnClickListener { dismiss() }
        videoView.setVideoPath(videoPath)
        videoView.start()
    }

    override fun onStop() {
        super.onStop()
        videoView?.stopPlayback()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

}