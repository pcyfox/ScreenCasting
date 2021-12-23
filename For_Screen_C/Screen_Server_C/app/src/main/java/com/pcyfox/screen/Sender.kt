package com.pcyfox.screen

import android.media.MediaCodec
import android.os.Handler
import android.os.HandlerThread
import com.pcyfox.h264.H264HandlerNative
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer


class Sender(
    ip: String,
    port: Int,
    type: SocketType,
    var isLiteMod: Boolean = true,
    var maxPacketLength: Int = MAX_PKT_LEN
) {
    private val TAG = "RtspBroadcast"
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

        if (h264File == null) {
            h264File = File("sdcard/screen.h264")
        }

        if (h264File!!.exists()) {
            h264File!!.delete()
        }

        os = FileOutputStream(h264File)
        bufOS = BufferedOutputStream(os)
    }

    fun send(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        val buf = ByteArray(info.size)
        h264Buffer.get(buf, info.offset, info.size)

        h264HandlerNative.packH264ToRTP(
            buf,
            buf.size,
            maxPacketLength,
            info.presentationTimeUs * 1000,
            clock,
            isLiteMod
        ) {
            handler?.post {
                bufOS?.write(it)
            }
        }

        h264Buffer.clear()
    }


    fun stop() {
        bufOS?.flush()
        os?.close()
        bufOS?.close()
    }


    fun updateSPS_PPS(sps: ByteArray, pps: ByteArray) {
        h264HandlerNative.updateSPS_PPS(sps, sps.size, pps, pps.size)
    }

    companion object {
        const val MULTI_CAST_IP = "239.0.0.200"
        const val TARGET_PORT = 2021
        const val MAX_PKT_LEN = 65000
    }
}


enum class SocketType {
    TCP, UDP
}