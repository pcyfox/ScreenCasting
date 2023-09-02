package com.pcyfox.lib_udp_player;


import android.util.Log;
import android.view.Surface;

import java.lang.annotation.Native;

public class NativePlayer {
    private static final String TAG = "NativePlayer";

    static {
        System.loadLibrary("udp_player");
    }

    private PlayState state;

    private OnPlayStateChangeListener onStateChangeListener;
    private OnDecodeStateChangeListener onDecodeStateChangeListener;

    public void setOnStateChangeListener(OnPlayStateChangeListener onStateChangeListener) {
        this.onStateChangeListener = onStateChangeListener;
    }

    public void setOnDecodeStateChangeListener(OnDecodeStateChangeListener onDecodeStateChangeListener) {
        this.onDecodeStateChangeListener = onDecodeStateChangeListener;
    }

    public PlayState getState() {
        return state;
    }


    //call in native
    public void onPlayerStateChange(int state) {
        Log.d(TAG, "onPlayerStateChange() called with: state = [" + state + "]");
        for (PlayState s : PlayState.values()) {
            if (state == s.ordinal()) {
                this.state = s;
                if (onStateChangeListener != null) {
                    onStateChangeListener.onStateChange(s);
                }
            }
        }
    }

    //call in native
    public void onDecodeStateChange(int state) {
        Log.d(TAG, "onDecodeStateChange() called with: state = [" + state + "]");
        if (onDecodeStateChangeListener != null) {
            onDecodeStateChangeListener.onStateChange(state);
        }
    }


    //-------------for native-------------------------
    public native int init(boolean isDebug);

    public native int configPlayer(Surface surface, int w, int h);


    public native int handlePkt(byte[] rtpPkt, int pktLen, int maxFrameLen, boolean isLiteMod);

    public native int play();

    public native int stop();

    public native int pause();

    public native void release();

    public native void test();

}
