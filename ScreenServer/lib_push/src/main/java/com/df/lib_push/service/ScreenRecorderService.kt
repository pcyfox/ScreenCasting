package com.df.lib_push.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.df.lib_push.Publisher
import com.df.lib_push.ScreenDisplay
import com.df.lib_push.VideoEncodeParam


/**
 *
 */
class ScreenRecorderService : Service() {
    private var screenDisplay: ScreenDisplay? = null
    private var isUseGL = true
    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_HIGH)
            notificationManager?.createNotificationChannel(channel)
        }
        keepAliveTrick()
    }

    private fun keepAliveTrick() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            val notification =
                NotificationCompat.Builder(this, channelId).setOngoing(true).setContentTitle("")
                    .setContentText("").build()
            startForeground(1, notification)
        } else {
            startForeground(1, Notification())
        }
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.run {
            val state = getIntExtra(KEY_STATE, -1)
            Log.d(
                TAG,
                "onStartCommand() called with: state= $state"
            )
            when (state) {
                0 -> {//stop
                    screenDisplay?.stopStream()
                    stopSelf()
                    return -1
                }
                //start
                1 -> start()
                //pause
                2 -> screenDisplay?.run {
                    pause()
                }
                //resume
                3 -> screenDisplay?.run {
                    resume()
                }
                else -> {
                }
            }
        }

        return START_STICKY
    }


    private fun start() {
        if (isStreaming()) {
            Log.d(TAG, "start() called fail,is streaming...")
            return
        }
        if (requestDisplayIntent == null) {
            Log.d(TAG, "start() called fail,requestDisplayIntent is null")
            return
        }

        requestDisplayIntent?.run {
            Log.d(TAG, "start() startStreamRtp...")
            screenDisplay = ScreenDisplay(applicationContext, ip, port, maxUdpPktLen, isUseGL)
            screenDisplay?.setIntentResult(resultCode, this)
            startStreamRtp(videoEncodeParam)
            if (isUseGL) {
                screenDisplay?.glInterface?.setForceRender(true)
            }
        }
    }


    fun isStreaming(): Boolean {
        return screenDisplay != null && screenDisplay!!.isStreaming
    }


    fun stopStream() {
        screenDisplay?.stopRecord()
        screenDisplay?.stopStream()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e(TAG, "RTP Display service destroy")
        stopStream()
    }


    private fun startStreamRtp(videoEncodeParam: VideoEncodeParam) {
        screenDisplay?.run {
            if (isStreaming) {
                stopStream()
                return
            }
            if (prepareVideo(
                    videoEncodeParam.w,
                    videoEncodeParam.h,
                    videoEncodeParam.fps,
                    videoEncodeParam.bitRate,
                    0,
                    videoEncodeParam.dpi,
                    videoEncodeParam.iFrameInterval,
                    videoEncodeParam.avcProfile,
                    videoEncodeParam.avcProfileLevel
                )
            ) {
                startStream()
            }
        }
    }

    companion object {
        private const val TAG = "ScreenRecordService"
        private const val channelId = "ScreenRecordServiceChannel"
        private var notificationManager: NotificationManager? = null
        private var resultCode: Int = -1
        private var requestDisplayIntent: Intent? = null

        var videoEncodeParam = VideoEncodeParam()
        private var ip: String = Publisher.MULTI_CAST_IP
        private var port: Int = Publisher.TARGET_PORT
        private var maxUdpPktLen = Publisher.MAX_PKT_LEN
        private const val KEY_STATE = "KEY_STATE"

        private fun changeState(context: Context, state: Int) {
            val intent = Intent()
            intent.setClass(context, ScreenRecorderService::class.java)
            intent.putExtra(KEY_STATE, state)
            context.startService(intent)
        }

        fun stop(context: Context) {
            changeState(context, 0)
        }

        fun pause(context: Context) {
            changeState(context, 1)
        }

        fun resume(context: Context) {
            changeState(context, 2)
        }

        fun start(
            context: Context,
            resultCode: Int,
            requestDisplayIntent: Intent,
            videoEncodeParam: VideoEncodeParam,
            maxUdpPktLen: Int,
            ip: String,
            port: Int
        ) {
            Companion.videoEncodeParam = videoEncodeParam
            Companion.ip = ip
            Companion.port = port
            Companion.resultCode = resultCode
            Companion.requestDisplayIntent = requestDisplayIntent
            Companion.maxUdpPktLen = maxUdpPktLen

            Log.d(
                TAG,
                "start() called with:  resultCode = $resultCode, videoEncodeParam = $videoEncodeParam, maxUdpPktLen = $maxUdpPktLen, ip = $ip, port = $port"
            )
            val startIntent = Intent()
            startIntent.setClass(context, ScreenRecorderService::class.java)
            startIntent.putExtra(KEY_STATE, 1)
            startIntent.putExtras(requestDisplayIntent)
            startIntent.putExtra("resultCode", resultCode)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(startIntent)
            } else {
                context.startService(startIntent)
            }
        }

    }
}
