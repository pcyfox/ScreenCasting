package com.pcyfox.lib_udp_player;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

/**
 * Created by Auser on 2018/5/28.
 */

public class MultiCastPlayer {
    private static final String TAG = "MultiCastPlayer";
    //MediaCodec variable
    private volatile boolean isPlaying = false;
    static String multiCastHost = "239.0.0.200";
    private int videoPort = 2021;
    private MulticastSocket multicastSocket;
    private final Handler handler;
    private final static int MAX_UDP_PACKET_LEN = 65507;//UDP包大小限制
    private NativePlayer nativeUDPPlayer;
    private final int maxFrameLen;

    public MultiCastPlayer(String host, int port, int maxFrameLen, SurfaceView surfaceView) {
        multiCastHost = host;
        videoPort = port;
        this.maxFrameLen = maxFrameLen;
        HandlerThread handlerThread = new HandlerThread("Fuck Vidwo Data Handler");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        initMultiBroadcast();
        initNativePlayer(surfaceView);
    }

    private void initMultiBroadcast() {
        try {
            multicastSocket = new MulticastSocket(videoPort);
            InetAddress receiveAddress = InetAddress.getByName(multiCastHost);
            multicastSocket.joinGroup(receiveAddress);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void initNativePlayer(SurfaceView surfaceView) {
        nativeUDPPlayer = new NativePlayer();
        nativeUDPPlayer.init(BuildConfig.DEBUG);
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                nativeUDPPlayer.configPlayer(holder.getSurface(), surfaceView.getWidth(), surfaceView.getHeight());

                if (nativeUDPPlayer.getState() == PlayState.PAUSE) {
                    nativeUDPPlayer.play();
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.d(TAG, "surfaceChanged() called with: holder = [" + holder + "], format = [" + format + "], width = [" + width + "], height = [" + height + "]");
                nativeUDPPlayer.changeSurface(holder.getSurface(), width, height);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.d(TAG, "surfaceDestroyed() called with: holder = [" + holder + "]");
                isPlaying = false;
                nativeUDPPlayer.pause();
            }
        });
    }

    /*
    开始播放
     */
    public void startPlay() {
        if (isPlaying) {
            Log.e(TAG, "start play failed.  player is playing");
        } else {
            isPlaying = true;
            nativeUDPPlayer.play();
            handler.post(this::startReceiveData);
        }
    }

    private void startReceiveData() {
        byte[] receiveByte = new byte[MAX_UDP_PACKET_LEN];
        DatagramPacket dataPacket = new DatagramPacket(receiveByte, receiveByte.length);
        while (isPlaying) {
            try {
                multicastSocket.receive(dataPacket);
                nativeUDPPlayer.handlePkt(receiveByte, dataPacket.getLength(), maxFrameLen, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Log.e(TAG, "startReceiveData:() over!");
    }

    /*
     *停止播放
     */
    public void stopPlay() {
        Log.d(TAG, "stopPlay() called");
        isPlaying = false;
    }


    public void pause() {
    }

    public static String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder("");
        if (src == null || src.length <= 0) {
            return null;
        }
        for (byte b : src) {
            int v = b & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv).append(" ");
        }
        return stringBuilder.toString();
    }

    public static String intToHex(int i) {
        int v = i & 0xFF;
        String hv = Integer.toHexString(v);
        if (hv.length() < 2) {
            return "0" + hv;
        } else {
            return hv;
        }
    }


    public boolean isPlaying() {
        return isPlaying;
    }


    public void release() {
        Log.e(TAG, "release() called");
        isPlaying = false;
    }


}
