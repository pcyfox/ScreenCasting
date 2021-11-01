package com.taike.lib_rtp_player;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.taike.lib_rtp_player.rtsp.CodecBufferInfoListener;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class VideoDecoder {
    private CodecBufferInfoListener codecBufferInfoListener;
    private final static String TAG = "VideoEncoder";
    private final static int CONFIGURE_FLAG_DECODE = 0;
    private MediaCodec mMediaCodec;
    private MediaFormat mMediaFormat;
    private Surface mSurface;
    private long lastTime = 0;
    private long frameTime = 0;
    private final BlockingQueue<byte[]> h264DataQueue = new ArrayBlockingQueue<>(1000);
    private volatile boolean isPlaying;
    private final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();


    public VideoDecoder(String mimeType, Surface surface, int width, int height) {
        Log.d(TAG, "VideoDecoder() called with: mimeType = [" + mimeType + "], surface = [" + surface + "], width = [" + width + "], height = [" + height + "]");
        try {
            mMediaCodec = MediaCodec.createDecoderByType(mimeType);
            MediaCodec.Callback mCallback = new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(@NonNull MediaCodec mediaCodec, int id) {
                    if (id < 0) {
                        return;
                    }
                    ByteBuffer inputBuffer = mediaCodec.getInputBuffer(id);
                    inputBuffer.clear();
                    byte[] dataSources = h264DataQueue.poll();
                    int length = 0;
                    if (dataSources != null) {
                        inputBuffer.put(dataSources);
                        length = dataSources.length;
                    }
                    mediaCodec.queueInputBuffer(id, 0, length, 0, 0);
                }

                @Override
                public void onOutputBufferAvailable(@NonNull MediaCodec mediaCodec, int id, @NonNull MediaCodec.BufferInfo bufferInfo) {
                    //  Log.d(TAG, "onOutputBufferAvailable() called with: mediaCodec = [" + mediaCodec + "], id = [" + id + "], bufferInfo = [" + bufferInfo + "]");
//            ByteBuffer outputBuffer = mMediaCodec.getOutputBuffer(id);
//            MediaFormat outputFormat = mMediaCodec.getOutputFormat(id);
//            if (mMediaFormat == outputFormat && outputBuffer != null && bufferInfo.size > 0) {
//                byte[] buffer = new byte[outputBuffer.remaining()];
//                outputBuffer.get(buffer);
//            }
                    mMediaCodec.releaseOutputBuffer(id, true);
                }

                @Override
                public void onError(@NonNull MediaCodec mediaCodec, @NonNull MediaCodec.CodecException e) {
                    Log.d(TAG, "------> onError" + e);
                }

                @Override
                public void onOutputFormatChanged(@NonNull MediaCodec mediaCodec, @NonNull MediaFormat mediaFormat) {
                    Log.d(TAG, "------> onOutputFormatChanged");
                }
            };
            mMediaCodec.setCallback(mCallback);
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            mMediaCodec = null;
            return;
        }
        if (surface == null) {
            return;
        }
        this.mSurface = surface;
        mMediaFormat = MediaFormat.createVideoFormat(mimeType, width, height);
        //  mMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        mMediaCodec.configure(mMediaFormat, surface, null, CONFIGURE_FLAG_DECODE);
    }

    public void putData(byte[] data) {
        if (h264DataQueue.size() > 100) {
            Log.e(TAG, "putData() queue is too large! ");
            // h264DataQueue.clear();
        }
        h264DataQueue.offer(data);
    }

    public void startDecoder() {
        if (mMediaCodec != null && mSurface != null) {
            isPlaying = true;
            mMediaCodec.start();
            Log.d(TAG, "startDecoder() called");
        } else {
            throw new IllegalArgumentException("startDecoder failed, please check the MediaCodec is init correct");
        }
    }

    public byte[] decodeValue(ByteBuffer bytes) {
        bytes.flip();
        bytes.limit(bufferInfo.offset + bufferInfo.size);
        byte[] yuvData = new byte[bytes.remaining()];
        bytes.get(yuvData);
        return yuvData;
    }

    public void stopDecoder() {
        Log.d(TAG, "stopDecoder() called");
        if (mMediaCodec != null) {
            mMediaCodec.stop();
        }
    }

    /**
     * release all resource that used in Encoder
     */
    public void release() {
        Log.d(TAG, "release() called");
        if (mMediaCodec != null) {
            h264DataQueue.clear();
            mMediaCodec.release();
            mMediaCodec = null;
        }
    }
}
