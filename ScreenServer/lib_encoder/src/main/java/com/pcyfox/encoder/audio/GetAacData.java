package com.pcyfox.encoder.audio;

import android.media.MediaCodec;

import android.media.MediaFormat;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 19/01/17.
 */

public interface GetAacData {

  void onAacData(ByteBuffer aacBuffer, MediaCodec.BufferInfo info);

  void onAudioFormat(MediaFormat mediaFormat);
}
