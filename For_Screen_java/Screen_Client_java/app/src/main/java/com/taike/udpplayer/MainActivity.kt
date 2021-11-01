package com.taike.udpplayer

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.blankj.utilcode.constant.PermissionConstants
import com.blankj.utilcode.util.PermissionUtils
import com.blankj.utilcode.util.ScreenUtils
import com.taike.lib_rtp_player.RTP_UDPPlayer
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketException
import java.util.concurrent.LinkedBlockingDeque

class MainActivity : AppCompatActivity() {
    private val BUF_MAX_SIZE = 4 * 1024
    private var player: RTP_UDPPlayer? = null
    private val TAG = "MainActivity"
    private var isStop = false
    private var mediaCodec: MediaCodec? = null;
    private val queue = LinkedBlockingDeque<ByteArray>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        PermissionUtils.permission(PermissionConstants.STORAGE)
            .callback(object : PermissionUtils.SimpleCallback {
                override fun onGranted() {
                }

                override fun onDenied() {
                }
            }).request()

        val sv = findViewById<SurfaceView>(R.id.udp_surface_view)
        sv.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder?) {
                holder?.setFixedSize(ScreenUtils.getScreenWidth(), ScreenUtils.getScreenHeight())
                player = RTP_UDPPlayer(
                    sv.holder.surface,
                    sv.width,
                    sv.height
                )
            }

            override fun surfaceChanged(
                holder: SurfaceHolder?,
                format: Int,
                width: Int,
                height: Int
            ) {
                Log.d(
                    TAG,
                    "surfaceChanged() called with: holder = $holder, format = $format, width = $width, height = $height"
                )
            }

            override fun surfaceDestroyed(holder: SurfaceHolder?) {
            }
        })
        findViewById<View>(R.id.udP_btn_start).run {
            postDelayed({
                performClick()
            }, 3000)
        }

    }

    fun startClick(v: View) {
        (v as TextView).text = "STOP"
        player?.startPlay()
        //player?.setH264DataStorePath("/sdcard/h264/udp.264")
        //   Thread { test() }.start()
    }

    private fun test() {
        val port = 2021//开启监听的端口
        var ds: DatagramSocket? = null
        var dp: DatagramPacket? = null
        val buf = ByteArray(BUF_MAX_SIZE) //存储发来的消息
        val root = Environment.getExternalStorageDirectory().absolutePath + "/h264"
        val rootFile = File(root)
        if (rootFile.exists()) {
            rootFile.delete()
        } else {
            rootFile.mkdirs()
        }

        val file = File("$root/rec_data.h264")
        if (file.exists()) {
            file.delete()
        }

        file.createNewFile()

        val fo = FileOutputStream(file.absolutePath)
        try {
            //绑定端口的
            ds = DatagramSocket(port)
            dp = DatagramPacket(buf, buf.size)
            Log.d(TAG, "监听广播端口打开：")

            Thread {
                //play()
            }.start()

            while (!isStop) {
                ds.receive(dp)
                val data = dp.data
                Log.d(TAG, "receive data size:${data.size}")
                queue.push(data)
                fo.write(data)
                fo.flush()
            }
            fo.close()
        } catch (e: SocketException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun play() {
        val mine = MediaFormat.MIMETYPE_VIDEO_AVC
        val surfaceView = findViewById<SurfaceView>(R.id.udp_surface_view)
        mediaCodec = MediaCodec.createDecoderByType(mine)


        mediaCodec?.setCallback(object : MediaCodec.Callback() {

            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                Log.d(TAG, "onInputBufferAvailable() called with: codec = $codec, index = $index")
                val bytes = queue.take()
                val input = codec.getInputBuffer(index)
                input?.run {
                    put(bytes)
                    codec.queueInputBuffer(index, 0, bytes.size, System.nanoTime(), 0)
                }

            }

            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo
            ) {
                codec.getOutputBuffer(index)?.run {
                    codec.releaseOutputBuffer(index, true)
                }
            }


            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            }

        })

        val format = MediaFormat.createVideoFormat(mine, surfaceView.width, surfaceView.height)
        mediaCodec?.configure(
            format,
            surfaceView.holder.surface,
            null,
            0
        )
        mediaCodec?.start()
        Log.d(TAG, "play() mediaCodec start")
    }


}