package com.pcyfox.h264;
public class H264HandlerNative {
    static {
        System.loadLibrary("rtp_lib");
    }


    public native void init(boolean isDebug, String ip, int port, int protocol);

    public native void updateSPS_PPS(byte[] sps, int spsLen, byte[] pps, int ppsLen);


    public native int packH264ToRTP(byte[] h264Pkt, int length, int maxPktLen,
                                    long pts, long clock, int tag, ResultCallback callback);

    public native int startSend();

    public native byte[] getSPS_PPS_RTP_Pkt(long ts, long clock);


    public interface ResultCallback {
        void onCallback(byte[] data);
    }

}
