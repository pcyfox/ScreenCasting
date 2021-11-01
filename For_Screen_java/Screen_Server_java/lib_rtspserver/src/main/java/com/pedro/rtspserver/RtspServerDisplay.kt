package com.pedro.rtspserver

import android.content.Context
import android.media.MediaCodec
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.pedro.encoder.utils.CodecUtil
import com.pedro.rtplibrary.base.DisplayBase
import com.pedro.rtsp.rtsp.VideoCodec
import com.pedro.rtsp.utils.ConnectCheckerRtsp
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingDeque


@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class RtspServerDisplay(context: Context, useOpengl: Boolean, connectCheckerRtsp: ConnectCheckerRtsp, port: Int) : DisplayBase(context, useOpengl) {
    private val h264BufferQueue = LinkedBlockingDeque<Pair<ByteBuffer, Int>>(100)
    var isStop = false
    private val rtspServer: RtspServer = RtspServer(context, connectCheckerRtsp, port)
    private val rtspBroadcast = RtspBroadcast()
    private val TAG = "RtspServerDisplay"
    private val BUF_MAX_SIZE = 4 * 1024

    //广播的实现 :由客户端发出广播，服务器端接收
    //var host = "255.255.255.255" //广播地址
    var host = "192.168.1.103" //广播地址
    var port = 9999 //广播的目的端口

    val adds: InetAddress = InetAddress.getByName(host)
    var ds: DatagramSocket = DatagramSocket()

    fun setVideoCodec(videoCodec: VideoCodec) {
        videoEncoder.type = if (videoCodec == VideoCodec.H265) CodecUtil.H265_MIME else CodecUtil.H264_MIME
    }

    fun getEndPointConnection(): String = "rtsp://${rtspServer.serverIp}:${rtspServer.port}/1"

    override fun setAuthorization(user: String, password: String) { //not developed
    }

    fun startStream() {
        super.startStream("")
        isStop = false
        // startSave()
        Log.d(TAG, "startStream() called url=" + getEndPointConnection())
    }

    private fun sendData(data: ByteArray) {
        ds.send(DatagramPacket(data, data.size, adds, port))
    }

    private fun saveData(data: ByteArray) {
        val out = FileOutputStream("/sdcard/h264/test.h264")
        out.write(data)
        out.flush()
    }

    private fun startSave() {
        val out = FileOutputStream("/sdcard/h264/test.h264")
        Thread {
            while (!isStop) {
                val pair = h264BufferQueue.take()
                val size = pair.second
                val buf = pair.first
                Log.d(TAG, "send buf size=${size}")

                if (size > BUF_MAX_SIZE) {
                    var offset = 0
                    var packCount = size / BUF_MAX_SIZE
                    if (packCount * BUF_MAX_SIZE < size) {
                        packCount++
                    }
                    for (i in 0 until packCount) {
                        var len = BUF_MAX_SIZE
                        //最后一次
                        if (i == packCount - 1) {
                            len = size - i * BUF_MAX_SIZE
                        }
                        offset = i * len
                        val data = ByteArray(len)
                        buf.get(data, offset, len)
                        sendData(data)
                    }

                } else {
                    val allData = ByteArray(size)
                    buf.get(allData, 0, size)
                    sendData(allData)
                }

                val allData = ByteArray(size)
                buf.get(allData, 0, size)
                out.write(allData)
                out.flush()
            }
        }.start()
    }

    override fun prepareAudioRtp(isStereo: Boolean, sampleRate: Int) {
        rtspServer.isStereo = isStereo
        rtspServer.sampleRate = sampleRate
    }

    override fun startStreamRtp(url: String) { //unused
    }

    override fun stopStreamRtp() {
        isStop = true
        rtspServer.stopServer()
    }

    override fun getAacDataRtp(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        rtspServer.sendAudio(aacBuffer, info)
    }

    private fun getData(byteBuffer: ByteBuffer): ByteArray {
        val bytes = ByteArray(byteBuffer.capacity() - 4)
        byteBuffer.position(4)
        byteBuffer[bytes, 0, bytes.size]
        return bytes
    }

    override fun onSpsPpsVpsRtp(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?) {
        val newSps = sps.duplicate()
        val newPps = pps.duplicate()
        val newVps = vps?.duplicate()
        rtspServer.setVideoInfo(newSps, newPps, newVps)
        //rtspServer.startServer()

        rtspBroadcast.setVideoInfo(getData(newSps), getData(newPps))
    }

    override fun getH264DataRtp(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        rtspServer.sendVideo(h264Buffer, info)
        //   sendData(h264Buffer, info.size)
        rtspBroadcast.sendVideoFrame(h264Buffer, info)
    }


    private fun sendData(buf: ByteBuffer, size: Int) {
        val allData = ByteArray(size)
        buf.get(allData)

        if (size > BUF_MAX_SIZE) {
            var offset = 0
            var packCount = size / BUF_MAX_SIZE
            if (packCount * BUF_MAX_SIZE < size) {
                packCount++
            }
            for (i in 0 until packCount) {
                var len = BUF_MAX_SIZE
                //最后一次
                if (i == packCount - 1) {
                    len = size - i * BUF_MAX_SIZE
                }
                offset = i * len
                val data = ByteArray(len)
                System.arraycopy(allData, offset, data, 0, len)
                Log.d(TAG, "----------->sendData() called with:  size = $len")
                sendData(data)
            }

        } else {
            Log.d(TAG, "----------->sendData() called with:  size = $size")
            sendData(allData)
        }

        saveData(allData)

    }

    /**
     * Unused functions
     */
    @Throws(RuntimeException::class)
    override fun resizeCache(newSize: Int) {
    }

    override fun shouldRetry(reason: String?): Boolean = false

    override fun reConnect(delay: Long) {
    }

    override fun setReTries(reTries: Int) {
    }

    override fun getCacheSize(): Int = 0

    override fun getSentAudioFrames(): Long = 0

    override fun getSentVideoFrames(): Long = 0

    override fun getDroppedAudioFrames(): Long = 0

    override fun getDroppedVideoFrames(): Long = 0

    override fun resetSentAudioFrames() {
    }

    override fun resetSentVideoFrames() {
    }

    override fun resetDroppedAudioFrames() {
    }

    override fun resetDroppedVideoFrames() {
    }
}