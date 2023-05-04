package com.df.lib_push

import android.content.Context
import android.media.MediaCodec
import android.os.SystemClock
import com.blankj.utilcode.util.ThreadUtils
import java.nio.ByteBuffer

class ScreenDisplay(
    context: Context,
    ip: String,
    port: Int,
    maxPacketLen: Int,
    useOpengl: Boolean = false
) :
    DisplayBase(context, useOpengl) {

    private var lastKeyFrameUS = 0L
    private val publisher = Publisher(ip, port, SocketType.UDP, maxPacketLength = maxPacketLen)

    override fun prepareAudioRtp(isStereo: Boolean, sampleRate: Int) {
    }

    fun startStream() {
        super.startStream("")
    }

    override fun startStreamRtp(url: String) { //unused

    }

    override fun stopStreamRtp() {
        publisher.stop()
    }

    override fun aacDataToRtp(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    }

    override fun onSpsPpsVpsRtp(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?) {
        publisher.updateScreen(width, height)
        val spsBuf = ByteArray(sps.capacity())
        sps.get(spsBuf)
        val ppsBuf = ByteArray(pps.capacity())
        pps.get(ppsBuf)
        publisher.updateSPS_PPS(spsBuf, ppsBuf)
    }

    override fun h264DataToRtp(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) {
            lastKeyFrameUS = SystemClock.uptimeMillis()
        }
        publisher.send(h264Buffer, info)
    }

    fun autoRefreshKeyFrame() {
        var lastRequestKeyFrameUS = 0L
        val span = 3000
        ThreadUtils.getCachedPool().submit {
            while (isStreaming) {
                val currentTime = SystemClock.uptimeMillis()
                if (currentTime - lastRequestKeyFrameUS >= span && currentTime - lastKeyFrameUS >= span) {  // 3s no key frame.
                    requestKeyFrame()
                    lastRequestKeyFrameUS = SystemClock.uptimeMillis()
                    Thread.sleep(2000)
                }
            }
        }
    }


}