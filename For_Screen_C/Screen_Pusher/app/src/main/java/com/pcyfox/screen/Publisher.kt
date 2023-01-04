package com.pcyfox.screen

import android.media.MediaCodec
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.pcyfox.h264.H264HandlerNative
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer


enum class SocketType {
    TCP, UDP
}

class Publisher(
    ip: String,
    port: Int,
    type: SocketType,
    val isNeedSaveRTPPkt: Boolean = BuildConfig.DEBUG,
    var maxPacketLength: Int = MAX_PKT_LEN
) {
    private val TAG = "Publisher"
    private var h264HandlerNative: H264HandlerNative = H264HandlerNative()
    private val clock = 25L

    private var h264File: File? = null
    private var handler: Handler? = null

    private var os: FileOutputStream? = null
    private var bufOS: BufferedOutputStream? = null

    init {
        h264HandlerNative.init(true, ip, port, type.ordinal)
        h264HandlerNative.startSend()

        val handlerThread = HandlerThread("SenderHandler")
        handlerThread.start();
        handler = Handler(handlerThread.looper)

        if (isNeedSaveRTPPkt && h264File == null) {
            h264File = File("sdcard/screen.h264").also {
                if (it.exists()) {
                    it.delete()
                }
            }
            Log.d(TAG, "init() called: save h264 file:${h264File?.absolutePath}")
            os = FileOutputStream(h264File)
            bufOS = BufferedOutputStream(os)
        }

    }

    fun send(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        val buf = ByteArray(info.size)
        h264Buffer.get(buf, info.offset, info.size)

        if (isNeedSaveRTPPkt) {
            handler?.post {
                bufOS?.write(buf)
            }
        }

        h264HandlerNative.packAndSedH264ToRTP(
            buf,
            buf.size,
            maxPacketLength,
            info.presentationTimeUs * 1000,
            clock,
            0, null
        )
        h264Buffer.clear()
    }


    fun stop() {
        if (os == null) return
        bufOS?.flush()
        os?.close()
        bufOS?.close()
        os = null
    }


    fun updateSPS_PPS(sps: ByteArray, pps: ByteArray) {
        h264HandlerNative.updateSPS_PPS(sps, sps.size, pps, pps.size)
    }

    fun updateScreen(w: Int, h: Int) {
        if (w * h > 0) {
            h264HandlerNative.updateScreen(w, h)
        }
    }

    companion object {
        const val MULTI_CAST_IP = "239.0.0.200"
        const val TARGET_PORT = 2021

        const val MAX_PKT_LEN = 65000
    }
}

