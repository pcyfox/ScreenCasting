package com.taike.lib_rtp_player.rtsp;

public interface CodecBufferInfoListener {
    void onDecodeStart(byte[] data);

    void onDecodeOver(byte[] data, int w, int h);
}
