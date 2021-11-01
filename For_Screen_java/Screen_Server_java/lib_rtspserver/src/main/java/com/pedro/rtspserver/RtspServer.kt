package com.pedro.rtspserver

import android.content.Context
import android.media.MediaCodec
import android.net.wifi.WifiManager
import android.util.Log
import com.pedro.rtsp.rtsp.Protocol
import com.pedro.rtsp.rtsp.RtspSender
import com.pedro.rtsp.utils.ConnectCheckerRtsp
import java.io.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.ByteBuffer

/**
 *
 * Created by pedro on 13/02/19.
 *
 *
 * TODO Use different session per client.
 */

class RtspServer(
        context: Context,
        private val connectCheckerRtsp: ConnectCheckerRtsp,
        val port: Int
) {

    private val TAG = "RtspServer"
    private lateinit var server: ServerSocket
    val serverIp = getServerIp(context)
    var sps: ByteBuffer? = null
    var pps: ByteBuffer? = null
    var vps: ByteBuffer? = null
    var sampleRate = 32000
    var isStereo = true
    private val clients = mutableListOf<Client>()
    private var thread: Thread? = null
    private var isBroadcast = true

    var isStarted = false
        private set

    fun startServer() {
        thread = Thread {
            server = ServerSocket(port)
            isStarted = true
            while (!Thread.interrupted()) {
                try {
                    Client(
                            server.accept(),
                            serverIp,
                            port,
                            connectCheckerRtsp,
                            sps,
                            pps,
                            vps,
                            sampleRate,
                            isStereo
                    ).run {
                        start()
                        clients.add(this)
                    }

                } catch (e: SocketException) {
                    Log.e(TAG, "Error", e)
                    break
                } catch (e: IOException) {
                    Log.e(TAG, e.message ?: "")
                    continue
                }
            }
            Log.i(TAG, "Server finished")
        }
        thread?.start()
    }

    fun stopServer() {
        clients.forEach { it.stopClient() }
        clients.clear()
        thread?.interrupt()
        try {
            thread?.join(100)
        } catch (e: InterruptedException) {
            thread?.interrupt()
        }
        thread = null
        if (!server.isClosed) server.close()
    }

    fun sendVideo(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        clients.forEach {
            if (it.isAlive && it.canSend) {
                it.rtspSender.sendVideoFrame(h264Buffer.duplicate(), info)
            }
        }
    }

    fun sendAudio(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        clients.forEach {
            if (it.isAlive && it.canSend) {
                it.rtspSender.sendAudioFrame(aacBuffer.duplicate(), info)
            }
        }
    }

    fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?) {
        Log.d(TAG, "------------------>setVideoInfo() called with: sps = [$sps], pps = [$pps], vps = [$vps]")
        this.sps = sps
        this.pps = pps
        this.vps = vps  //H264 has no vps so if not null assume H265
    }

    private fun getServerIp(context: Context): String {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return InetAddress.getByAddress(ByteArray(4) { i ->
            wm.connectionInfo.ipAddress.shr(i * 8).and(255).toByte()
        }).hostAddress
    }

    internal class Client(
            private val socket: Socket, serverIp: String, serverPort: Int,
            connectCheckerRtsp: ConnectCheckerRtsp, sps: ByteBuffer?,
            pps: ByteBuffer?, vps: ByteBuffer?, private val sampleRate: Int,
            isStereo: Boolean/*是否为立体声*/
    ) : Thread() {
        private val TAG = "Client"
        private var cSeq = 0
        private val output = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
        private val input = BufferedReader(InputStreamReader(socket.getInputStream()))
        val rtspSender = RtspSender(connectCheckerRtsp)
        private val commandsManager =
                ServerCommandManager(
                        serverIp,
                        serverPort,
                        socket.inetAddress.hostAddress
                )
        var canSend = false

        init {
            commandsManager.setIsStereo(isStereo)
            if (sps != null && pps != null) {
                commandsManager.setVideoInfo(sps, pps, vps)
            }
        }

        override fun run() {
            super.run()
            Log.i(TAG, "New client ${commandsManager.clientIp}")
            while (!interrupted()) {
                try {
                    val request = commandsManager.getRequest(input)
                    cSeq = commandsManager.getCSeq(request) //update cSeq
                    if (cSeq == -1) { //If cSeq parsed fail send error to client
                        output.write(commandsManager.createError(500, cSeq))
                        output.flush()
                        continue
                    }
                    val action = request.split("\n")[0]
                    Log.i(TAG, request)
                    //TODO 如果已经连接的，OPTIONS就不再回复

                    val response = commandsManager.createResponse(action, request, cSeq)
                    Log.i(TAG, response)
                    output.write(response)
                    output.flush()

                    Log.d(TAG, "--------action:$action")
                    Log.d(TAG, "--------protocol:${commandsManager.protocol}")

                    if (action.contains("play", true)) {
                        Log.i(TAG, "Protocol ${commandsManager.protocol}")
                        rtspSender.setSocketsInfo(
                                commandsManager.protocol, commandsManager.videoClientPorts,
                                commandsManager.audioClientPorts
                        )

                        if (commandsManager.sps != null && commandsManager.pps != null) {
                            rtspSender.setVideoInfo(
                                    commandsManager.sps,
                                    commandsManager.pps,
                                    commandsManager.vps
                            )
                        } else {
                            Log.e(TAG, "play with not sps,pps!")
                        }
                        rtspSender.setAudioInfo(sampleRate)
                        rtspSender.setDataStream(socket.getOutputStream(), commandsManager.clientIp)
                        if (commandsManager.protocol == Protocol.UDP) {
                            rtspSender.setVideoPorts(
                                    commandsManager.videoPorts[0],
                                    commandsManager.videoPorts[1]
                            )
                            rtspSender.setAudioPorts(
                                    commandsManager.audioPorts[0],
                                    commandsManager.audioPorts[1]
                            )
                        }
                        rtspSender.start()
                        canSend = true
                    }
                } catch (e: SocketException) { // Client has left
                    Log.e(TAG, "Client disconnected", e)
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error", e)
                }
            }
        }

        fun stopClient() {
            canSend = false
            rtspSender.stop()
            interrupt()
            try {
                join(100)
            } catch (e: InterruptedException) {
                interrupt()
            } finally {
                socket.close()
            }
        }

        override fun equals(other: Any?): Boolean {
            if (other == null) {
                return false
            }
            if (other === this) {
                return true
            }
            if (other is Client) {
                return other.commandsManager.clientIp == this.commandsManager.clientIp
            }
            return false
        }

        override fun hashCode(): Int {
            var result = socket.hashCode()
            result = 31 * result + sampleRate
            result = 31 * result + cSeq
            result = 31 * result + commandsManager.hashCode()
            result = 31 * result + canSend.hashCode()
            return result
        }
    }
}