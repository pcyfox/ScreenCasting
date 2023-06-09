package com.df.lib_push

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
    var maxPacketLength: Int = MAX_PKT_LEN
) {
    private val TAG = "Publisher"
    private var h264HandlerNative: H264HandlerNative = H264HandlerNative()
    private val clock = 25L

    private var h264File: File? = null
    private var handler: Handler? = null

    @Volatile
    private var isRecording: Boolean = false

    private var os: FileOutputStream? = null
    private var bufOS: BufferedOutputStream? = null

    init {
        h264HandlerNative.init(true, ip, port, type.ordinal)
        h264HandlerNative.startSend()

        val handlerThread = HandlerThread("SenderHandler")
        handlerThread.start();
        handler = Handler(handlerThread.looper)

    }

    fun startRecord(file: String = "sdcard/screen.h264") {
        if (h264File == null || os == null) {
            h264File = File(file).also {
                if (it.exists()) {
                    it.delete()
                }
            }
            Log.d(TAG, "init() called: save h264 file:${h264File?.absolutePath}")
            os = FileOutputStream(h264File)
            bufOS = BufferedOutputStream(os)
        }
        isRecording = true
    }

    fun stopRecord() {
        isRecording = false
        if (os == null) return
        bufOS?.flush()
        bufOS?.close()
        os?.close()
        os = null
        bufOS = null
    }

    fun send(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        //  Log.d(TAG, "send() called with: presentationTimeUs= ${info.presentationTimeUs}")
        val buf = ByteArray(info.size)
        h264Buffer.get(buf, info.offset, info.size)
        h264Buffer.clear()
        if (isRecording) handler?.post { bufOS?.write(buf) }
        h264HandlerNative.packAndSedH264ToRTP(
            buf,
            buf.size,
            maxPacketLength,
            info.presentationTimeUs * 1000,
            clock,
            0, null
        )
    }


    fun stop() {
        stopRecord()
        h264HandlerNative.stopSend()
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
        const val TARGET_PORT = 9527

        const val MAX_PKT_LEN = 62000
    }
}

