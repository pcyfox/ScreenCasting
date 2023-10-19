package com.pcyfox.lib_udp_player;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class MultiCastPlayerView extends RelativeLayout {
    private static final String TAG = "MultiCastPlayer";
    //MediaCodec variable
    private volatile boolean isPlaying = false;
    private volatile boolean isPause = false;
    private volatile boolean hasReceivedData = false;

    static String multiCastHost = "239.0.0.200";
    private int videoPort = 2021;
    private MulticastSocket multicastSocket;
    private Handler handler;
    private final static int MAX_UDP_PACKET_LEN = 65507;//UDP包大小限制
    private NativePlayer nativeUDPPlayer;
    private int maxFrameLen;
    private SurfaceView surfaceView;
    private HandlerThread handlerThread;

    public MultiCastPlayerView(Context context) {
        super(context);
    }

    public MultiCastPlayerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MultiCastPlayerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MultiCastPlayerView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    protected void onFinishInflate() {
        super.onFinishInflate();
        setBackgroundColor(Color.WHITE);
    }

    public void setOnStateChangeListener(OnPlayStateChangeListener onStateChangeListener) {
        buildPlayer().setOnStateChangeListener(onStateChangeListener);
    }

    public void setOnDecodeStateChangeListener(OnDecodeStateChangeListener onDecodeStateChangeListener) {
        buildPlayer().setOnDecodeStateChangeListener(onDecodeStateChangeListener);
    }

    private void addSurfaceView() {
        if (surfaceView != null) {
            removeView(surfaceView);
            surfaceView.getHolder().getSurface().release();
        }
        surfaceView = new SurfaceView(getContext());
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.addRule(RelativeLayout.CENTER_IN_PARENT);
        addView(surfaceView, params);
    }

    public void config(String host, int port, int maxFrameLen) {
        Log.d(TAG, "config() called with: host = [" + host + "], port = [" + port + "], maxFrameLen = [" + maxFrameLen + "]");
        if (isPlaying) {
            Log.e(TAG, "config() fail,is playing!");
            return;
        }
        multiCastHost = host;
        videoPort = port;
        this.maxFrameLen = maxFrameLen;
        if (handlerThread != null) handlerThread.quit();
        handlerThread = new HandlerThread("Fuck Video Data Handler");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        post(() -> {
            addSurfaceView();
            initNativePlayer();
            initMultiBroadcast();
        });
    }

    private void initMultiBroadcast() {
        try {
            if (multicastSocket != null) multicastSocket.disconnect();
            multicastSocket = new MulticastSocket(videoPort);
            InetAddress receiveAddress = InetAddress.getByName(multiCastHost);
            multicastSocket.joinGroup(receiveAddress);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private NativePlayer buildPlayer() {
        if (nativeUDPPlayer != null) return nativeUDPPlayer;
        nativeUDPPlayer = new NativePlayer();
        nativeUDPPlayer.init(BuildConfig.DEBUG);
        return nativeUDPPlayer;
    }

    private void initNativePlayer() {
        buildPlayer();
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.d(TAG, "surfaceChanged() called with: holder = [" + holder + "], format = [" + format + "], width = [" + width + "], height = [" + height + "]");
                if (nativeUDPPlayer == null) return;
                PlayState state = nativeUDPPlayer.getState();
                if (state == PlayState.STARTED || state == PlayState.PAUSE) {
                    nativeUDPPlayer.stop();
                }
                buildPlayer();
                post(() -> {
                    if (surfaceView != null) {
                        nativeUDPPlayer.configPlayer(holder.getSurface(), surfaceView.getWidth(), surfaceView.getHeight());
                    }
                });
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.d(TAG, "surfaceDestroyed() called with: holder = [" + holder + "]");
                isPause = true;
                if (nativeUDPPlayer != null) nativeUDPPlayer.pause();
            }
        });
    }

    private void startReceiveData() {
        Log.d(TAG, "startReceiveData() called");
        byte[] receiveByte = new byte[MAX_UDP_PACKET_LEN];
        DatagramPacket dataPacket = new DatagramPacket(receiveByte, receiveByte.length);
        while (isPlaying) {
            if (multicastSocket.isClosed()) return;
            if (isPause) {
                continue;
            }
            try {
                multicastSocket.receive(dataPacket);
                int len = dataPacket.getLength();
                if (len <= 16) continue;
                nativeUDPPlayer.handlePkt(receiveByte, len, maxFrameLen, true);
                if (!hasReceivedData) {
                    Log.d(TAG, "startReceiveData() --------- hasReceivedData----------");
                    hasReceivedData = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Log.e(TAG, "startReceiveData:() over!");
    }

    public void startPlay() {
        if (isPlaying) {
            Log.e(TAG, "start play failed.  player is playing");
        } else {
            isPlaying = true;
            nativeUDPPlayer.play();
            handler.post(this::startReceiveData);
            Log.d(TAG, "startPlay() called");
        }
    }


    public void stopPlay() {
        Log.d(TAG, "stopPlay() called");
        if (!isPlaying) {
            Log.e(TAG, "stopPlay() called,fuck, this player is not start");
            return;
        }
        isPause = false;
        isPlaying = false;

        if (handlerThread != null) handlerThread.quit();
        if (handler != null) handler.getLooper().quitSafely();
        handlerThread = null;
        handler = null;


        if (multicastSocket != null) {
            multicastSocket.close();
            multicastSocket = null;
        }
        if (nativeUDPPlayer != null) {
            nativeUDPPlayer.stop();
            nativeUDPPlayer = null;
        }

        if (surfaceView != null) {
            surfaceView.getHolder().getSurface().release();
            removeView(surfaceView);
            surfaceView = null;
        }
    }


    public void pause() {
        if (isPlaying) {
            isPause = true;
        } else {
            Log.e(TAG, "pause() called fail,player is not start!");
        }
    }


    public void resume() {
        if (isPause) {
            isPause = false;
        } else {
            Log.d(TAG, "resume() called fail,player in not pause");
        }
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
        if (nativeUDPPlayer != null) {
            nativeUDPPlayer.release();
        }
    }
}
