package com.pcyfox.screen

import android.media.MediaCodec
import com.pcyfox.h264.H264HandlerNative
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


    init {
        h264HandlerNative.init(true, ip, port, type.ordinal)
        h264HandlerNative.startSend()
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
            isLiteMod,
            null
        );
        h264Buffer.clear()
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