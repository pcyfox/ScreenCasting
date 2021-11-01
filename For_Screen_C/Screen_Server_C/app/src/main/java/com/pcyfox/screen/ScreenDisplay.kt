package com.pcyfox.screen

import android.content.Context
import android.media.MediaCodec
import android.os.Build
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class ScreenDisplay(context: Context, ip: String, port: Int, maxPacketLen: Int) :
    DisplayBase(context, false) {
    var isStop = false
    private val sender = Sender(ip, port, SocketType.UDP, maxPacketLength = maxPacketLen)
    override fun prepareAudioRtp(isStereo: Boolean, sampleRate: Int) {
    }

    fun startStream() {
        super.startStream("")
        isStop = false
    }

    override fun startStreamRtp(url: String) { //unused

    }

    override fun stopStreamRtp() {
        isStop = true
    }

    override fun getAacDataRtp(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    }

    override fun onSpsPpsVpsRtp(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?) {
        val spsBuf = ByteArray(sps.capacity())
        sps.get(spsBuf)
        val ppsBuf = ByteArray(pps.capacity())
        pps.get(ppsBuf)
        sender.updateSPS_PPS(spsBuf, ppsBuf)
    }

    override fun getH264DataRtp(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        sender.send(h264Buffer, info)
    }


}