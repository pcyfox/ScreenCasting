package com.taike.lib_rtp_player.rtsp;

/**
 * Created by pedro on 20/02/17.
 */

public  class DefRtspListener implements RtspListener {
    @Override
    public void onCanPlay(String sessionId, int[] ports) {

    }

    @Override
    public void onPlayError(String sessionId, int[] ports) {

    }

    @Override
    public void onPauseOver(String sessionId, int[] ports) {

    }

    @Override
    public void onPauseError(String sessionId, int[] ports) {

    }

    @Override
    public void onStopOver(String sessionId, int[] ports) {

    }

    @Override
    public void onStopError(String sessionId, int[] ports) {

    }

    @Override
    public void onConnectionSuccessRtsp() {

    }

    @Override
    public void onConnectionFailedRtsp(String reason) {

    }

    @Override
    public void onNewBitrateRtsp(long bitrate) {

    }

    @Override
    public void onDisconnectRtsp() {

    }

    @Override
    public void onAuthErrorRtsp() {

    }

    @Override
    public void onAuthSuccessRtsp() {

    }
}
