package com.pcyfox.lib_udp_player;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.HandlerThread;
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

    static String multiCastHost = "239.0.0.200";
    private int videoPort = 2021;
    private MulticastSocket multicastSocket;
    private Handler handler;
    private final static int MAX_UDP_PACKET_LEN = 65507;//UDP包大小限制
    private NativePlayer nativeUDPPlayer;
    private int maxFrameLen;
    private SurfaceView surfaceView;

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

    private void addSurfaceView() {
        surfaceView = new SurfaceView(getContext());
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.addRule(RelativeLayout.CENTER_IN_PARENT);
        addView(surfaceView, params);
    }

    public void config(String host, int port, int maxFrameLen) {
        Log.d(TAG, "config() called with: host = [" + host + "], port = [" + port + "], maxFrameLen = [" + maxFrameLen + "]");
        if (isPlaying) {
            return;
        }
        multiCastHost = host;
        videoPort = port;
        this.maxFrameLen = maxFrameLen;
        HandlerThread handlerThread = new HandlerThread("Fuck Video Data Handler");
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
            multicastSocket = new MulticastSocket(videoPort);
            InetAddress receiveAddress = InetAddress.getByName(multiCastHost);
            multicastSocket.joinGroup(receiveAddress);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void initNativePlayer() {
        nativeUDPPlayer = new NativePlayer();
        nativeUDPPlayer.init(BuildConfig.DEBUG);
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                int width = surfaceView.getWidth();
                int height = surfaceView.getHeight();
                holder.setKeepScreenOn(true);
                holder.setFixedSize(width, height);
                nativeUDPPlayer.configPlayer(holder.getSurface(), width, height);
                if (nativeUDPPlayer.getState() == PlayState.PAUSE) {
                    nativeUDPPlayer.play();
                    isPause = false;
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
                isPause = true;
                nativeUDPPlayer.pause();
            }
        });
    }

    private void startReceiveData() {
        byte[] receiveByte = new byte[MAX_UDP_PACKET_LEN];
        DatagramPacket dataPacket = new DatagramPacket(receiveByte, receiveByte.length);
        while (isPlaying) {
            if (isPause) {
                continue;
            }
            try {
                multicastSocket.receive(dataPacket);
                nativeUDPPlayer.handlePkt(receiveByte, dataPacket.getLength(), maxFrameLen, true);
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
        }
    }


    public void stopPlay() {
        Log.d(TAG, "stopPlay() called");
        if (!isPlaying) {
            Log.e(TAG, "stopPlay() called,fuck player is not start");
            return;
        }
        isPause = false;
        isPlaying = false;
        if (handler != null) {
            handler.getLooper().quitSafely();
        }

        if (multicastSocket != null) {
            multicastSocket.close();
        }
        if (nativeUDPPlayer != null) {
            nativeUDPPlayer.stop();
        }

        if (surfaceView != null) {
            surfaceView.getHolder().getSurface().release();
            removeView(surfaceView);
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
    }


}
