package com.pedro.rtspserver

import android.media.MediaCodec
import android.util.Log
import com.pedro.rtsp.rtp.packets.H264Packet
import com.pedro.rtsp.rtp.packets.VideoPacketCallback
import com.pedro.rtsp.rtsp.RtpFrame
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

class RtspBroadcast : VideoPacketCallback {
    private val TAG = "RtspBroadcast"
    private val datagramSocket = DatagramSocket()

    // private val broadcastIp = "255.255.255.255"
    //private val broadcastIp = "192.168.41.18"
    private val broadcastIp = "239.0.0.200"
    private val targetPort = 2021
    private var videoPacket: H264Packet? = null

    override fun onVideoFrameCreated(rtpFrame: RtpFrame) {
//        if (rtpFrame.sequence != 0L && rtpFrame.sequence - lastSeq != 1L) {
//            Log.e(TAG, "onVideoFrameCreated() called with: lastAwq= $lastSeq  currentSeq= ${rtpFrame.sequence}")
//        }
//        if (rtpFrame.isKeyFrame) {
//            Log.i(TAG, "onVideoFrameCreated() called with: rtpFrame = $rtpFrame")
//        } else {
//            Log.d(TAG, "onVideoFrameCreated() called with: rtpFrame = $rtpFrame")
//        }


/*
        for (ip in ipGroup) {
            val address = InetAddress.getByName("192.168.43.$ip")
            val packet = DatagramPacket(rtpFrame.buffer, rtpFrame.length, address, targetPort)
            datagramSocket.send(packet)
        }
*/

        val targetAddress = InetAddress.getByName(broadcastIp)
        val packet = DatagramPacket(rtpFrame.buffer, rtpFrame.length, targetAddress, targetPort)
        datagramSocket.send(packet)
    }

    fun setVideoInfo(sps: ByteArray, pps: ByteArray) {
        Log.d(TAG, "setVideoInfo() called with: sps.size = ${sps.size}, pps.size = ${pps.size}")
        videoPacket = H264Packet(sps, pps, this)
    }

    fun sendVideoFrame(h264Buffer: ByteBuffer?, info: MediaCodec.BufferInfo?) {
        videoPacket!!.createAndSendPacket(h264Buffer, info)
    }

}