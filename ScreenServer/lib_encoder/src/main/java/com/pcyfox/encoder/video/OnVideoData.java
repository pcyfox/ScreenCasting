package com.pcyfox.encoder.video;

import android.media.MediaCodec;

import android.media.MediaFormat;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 20/01/17.
 */

public interface OnVideoData {

  void onSpsPps(ByteBuffer sps, ByteBuffer pps);

  void onSpsPpsVps(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps);

  void onVideoData(ByteBuffer h264Buffer, MediaCodec.BufferInfo info);

  void onVideoFormat(MediaFormat mediaFormat);
}
