package com.df.lib_push;

import static android.content.Context.MEDIA_PROJECTION_SERVICE;

import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;

import androidx.annotation.RequiresApi;

import com.df.lib_push.utils.FpsListener;
import com.pcyfox.encoder.Frame;
import com.pcyfox.encoder.audio.AudioEncoder;
import com.pcyfox.encoder.audio.GetAacData;
import com.pcyfox.encoder.input.audio.CustomAudioEffect;
import com.pcyfox.encoder.input.audio.GetMicrophoneData;
import com.pcyfox.encoder.input.audio.MicrophoneManager;
import com.pcyfox.encoder.utils.CodecUtil;
import com.pcyfox.encoder.video.FormatVideoEncoder;
import com.pcyfox.encoder.video.OnVideoData;
import com.pcyfox.encoder.video.VideoEncoder;

import java.nio.ByteBuffer;

public abstract class DisplayBase implements GetAacData, OnVideoData, GetMicrophoneData {
    private static final String TAG = "DisplayBase";
    private boolean disableAudio = true;
    protected Context context;
    private MediaProjection mediaProjection;
    private final MediaProjectionManager mediaProjectionManager;
    protected VideoEncoder videoEncoder;
    private final MicrophoneManager microphoneManager;
    private final AudioEncoder audioEncoder;
    private boolean streaming = false;
    private boolean isRunning = false;
    protected SurfaceView surfaceView;

    private int dpi = 480;
    protected int width;
    protected int height;

    private VirtualDisplay virtualDisplay;
    private int resultCode = -1;
    private Intent data;


    private OffScreenGlThread glInterface;
    private final FpsListener fpsListener = new FpsListener();

    public DisplayBase(Context context, boolean useOpengl) {
        this.context = context;
        this.surfaceView = null;
        if (useOpengl) {
            glInterface = new OffScreenGlThread(context);
            glInterface.init();
        }
        mediaProjectionManager = ((MediaProjectionManager) context.getSystemService(MEDIA_PROJECTION_SERVICE));
        videoEncoder = new VideoEncoder(this);
        microphoneManager = new MicrophoneManager(this);
        audioEncoder = new AudioEncoder(this);
    }

    /**
     * Set an audio effect modifying microphone's PCM buffer.
     */
    public void setCustomAudioEffect(CustomAudioEffect customAudioEffect) {
        microphoneManager.setCustomAudioEffect(customAudioEffect);
    }

    public GlInterface getGlInterface() {
        if (glInterface != null) {
            return glInterface;
        } else {
            throw new RuntimeException("You can't do it. You are not using Opengl");
        }
    }

    /**
     * Call this method before use @startStream. If not you will do a stream without video.
     *
     * @param width    resolution in px.
     * @param height   resolution in px.
     * @param fps      frames per second of the stream.
     * @param bitrate  H264 in bps.
     * @param rotation could be 90, 180, 270 or 0 (Normally 0 if you are streaming in landscape or 90
     *                 if you are streaming in Portrait). This only affect to stream result. This work rotating with
     *                 encoder.
     *                 NOTE: Rotation with encoder is silence ignored in some devices.
     * @param dpi      of your screen device.
     * @return true if success, false if you get a error (Normally because the encoder selected
     * doesn't support any configuration seated or your device hasn't a H264 encoder).
     */
    public boolean prepareVideo(int width, int height,
                                int fps, int bitrate, int rotation, int dpi,
                                int iFrameInterval,
                                int avcProfile, int avcProfileLevel
    ) {
        this.dpi = dpi;
        this.width = width;
        this.height = height;
        boolean ret = videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, rotation, true, iFrameInterval, FormatVideoEncoder.SURFACE, avcProfile, avcProfileLevel);
        if (glInterface != null) {
            if (rotation == 90 || rotation == 270) {
                glInterface.setEncoderSize(height, width);
            } else {
                glInterface.setEncoderSize(width, height);
            }
        }
        return ret;
    }

    public boolean prepareVideo(int width, int height, int fps, int bitrate, int rotation, int dpi) {
        return prepareVideo(width, height, fps, bitrate, rotation, dpi, -1, -1, 1);
    }

    protected abstract void prepareAudioRtp(boolean isStereo, int sampleRate);

    /**
     * Call this method before use @startStream. If not you will do a stream without audio.
     *
     * @param bitrate         AAC in kb.
     * @param sampleRate      of audio in hz. Can be 8000, 16000, 22500, 32000, 44100.
     * @param isStereo        true if you want Stereo audio (2 audio channels), false if you want Mono audio
     *                        (1 audio channel).
     * @param echoCanceler    true enable echo canceler, false disable.
     * @param noiseSuppressor true enable noise suppressor, false  disable.
     * @return true if success, false if you get a error (Normally because the encoder selected
     * doesn't support any configuration seated or your device hasn't a AAC encoder).
     */
    public boolean prepareAudio(int bitrate, int sampleRate, boolean isStereo, boolean echoCanceler,
                                boolean noiseSuppressor) {
        microphoneManager.createMicrophone(sampleRate, isStereo, echoCanceler, noiseSuppressor);
        prepareAudioRtp(isStereo, sampleRate);
        return audioEncoder.prepareAudioEncoder(bitrate, sampleRate, isStereo,
                microphoneManager.getMaxInputSize());
    }

    /**
     * Call this method before use @startStream for streaming internal audio only.
     *
     * @param bitrate    AAC in kb.
     * @param sampleRate of audio in hz. Can be 8000, 16000, 22500, 32000, 44100.
     * @param isStereo   true if you want Stereo audio (2 audio channels), false if you want Mono audio
     *                   (1 audio channel).
     * @see AudioPlaybackCaptureConfiguration.Builder#Builder(MediaProjection)
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public boolean prepareInternalAudio(int bitrate, int sampleRate, boolean isStereo) {
        if (mediaProjection == null) {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
        }

        AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration
                .Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build();

        microphoneManager.createInternalMicrophone(config, sampleRate, isStereo);
        prepareAudioRtp(isStereo, sampleRate);
        return audioEncoder.prepareAudioEncoder(bitrate, sampleRate, isStereo,
                microphoneManager.getMaxInputSize());
    }

    /**
     * Same to call:
     * rotation = 0;
     * if (Portrait) rotation = 90;
     * prepareVideo(640, 480, 30, 1200 * 1024, true, 0);
     *
     * @return true if success, false if you get a error (Normally because the encoder selected
     * doesn't support any configuration seated or your device hasn't a H264 encoder).
     */
    public boolean prepareVideo() {
        return prepareVideo(640, 480, 30, 1200 * 1024, 0, 320);
    }

    /**
     * Same to call:
     * prepareAudio(64 * 1024, 32000, true, false, false);
     *
     * @return true if success, false if you get a error (Normally because the encoder selected
     * doesn't support any configuration seated or your device hasn't a AAC encoder).
     */
    public boolean prepareAudio() {
        return prepareAudio(64 * 1024, 32000, true, false, false);
    }

    /**
     * @param forceVideo force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
     * @param forceAudio force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
     */
    public void setForce(CodecUtil.Force forceVideo, CodecUtil.Force forceAudio) {
        videoEncoder.setForce(forceVideo);
        audioEncoder.setForce(forceAudio);
    }


    /**
     * Stop record MP4 video started with @startRecord. If you don't call it file will be unreadable.
     */
    public void stopRecord() {
        if (!streaming) stopStream();
    }

    protected abstract void startStreamRtp(String url);

    /**
     * Create Intent used to init screen capture with startActivityForResult.
     *
     * @return intent to startActivityForResult.
     */
    public Intent sendIntent() {
        return mediaProjectionManager.createScreenCaptureIntent();
    }

    public void setIntentResult(int resultCode, Intent data) {
        this.resultCode = resultCode;
        this.data = data;
    }

    public void startStream(String url) {
        streaming = true;
        if (!isRunning) {
            startEncoders(resultCode, data);
        } else {
            resetVideoEncoder();
        }
        startStreamRtp(url);
    }

    private void startEncoders(int resultCode, Intent data) {
        Log.d(TAG, "startEncoders() called with: resultCode = [" + resultCode + "], data = [" + data + "],dpi=" + dpi);
        if (data == null) {
            throw new RuntimeException("You need send intent data before startRecord or startStream");
        }
        videoEncoder.start();
        if (!disableAudio) audioEncoder.start();

        if (glInterface != null) {
            glInterface.setFps(videoEncoder.getFps());
            glInterface.start();
            glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
        }

        Surface surface = (glInterface != null) ? glInterface.getSurface() : videoEncoder.getInputSurface();

        if (mediaProjection == null) {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
        }

        if (!disableAudio) {
            microphoneManager.start();
        }

        int flag = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;

        if (glInterface != null && videoEncoder.getRotation() == 90 || videoEncoder.getRotation() == 270) {
            mediaProjection.createVirtualDisplay("Stream Display", videoEncoder.getHeight(),
                    videoEncoder.getWidth(), dpi, flag, surface, null, null);
        } else {
            mediaProjection.createVirtualDisplay("Stream Display", videoEncoder.getWidth(),
                    videoEncoder.getHeight(), dpi, flag, surface, null, null);
        }
    }

    private void resetVideoEncoder() {
        virtualDisplay.setSurface(null);
        videoEncoder.reset();
        virtualDisplay.setSurface(videoEncoder.getInputSurface());
    }

    public void requestKeyFrame() {
        if (videoEncoder != null)
            videoEncoder.forceSyncFrame();

        if (BuildConfig.DEBUG)
            Log.d(TAG, "requestKeyFrame() called");
    }


    protected abstract void stopStreamRtp();

    /**
     * Stop stream started with @startStream.
     */
    public void stopStream() {
        if (streaming) {
            stopStreamRtp();
            streaming = false;
            microphoneManager.stop();
            if (mediaProjection != null) {
                mediaProjection.stop();
            }
            if (glInterface != null) {
                glInterface.removeMediaCodecSurface();
                glInterface.stop();
            }

            videoEncoder.stop();
            if (audioEncoder != null) {
                audioEncoder.stop();
            }
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }
            data = null;
        }
    }

    public boolean pause() {
        if (virtualDisplay != null) {
            streaming = false;
        } else {
            return false;
        }
        return true;
    }

    public boolean resume() {
        if (virtualDisplay != null) {
            streaming = true;
        } else {
            return false;
        }
        return true;
    }

    /**
     * Replace with reTry(long delay, String reason);
     */
    @Deprecated
    public void reTry(long delay) {
        resetVideoEncoder();
    }


    /**
     * Mute microphone, can be called before, while and after stream.
     */
    public void disableAudio() {
        disableAudio = true;
        microphoneManager.mute();
        microphoneManager.stop();
    }

    /**
     * Enable a muted microphone, can be called before, while and after stream.
     */
    public void enableAudio() {
        disableAudio = false;
        microphoneManager.unMute();
    }

    /**
     * Get mute state of microphone.
     *
     * @return true if muted, false if enabled
     */
    public boolean isAudioMuted() {
        return microphoneManager.isMuted();
    }

    public int getBitrate() {
        return videoEncoder.getBitRate();
    }

    public int getResolutionValue() {
        return videoEncoder.getWidth() * videoEncoder.getHeight();
    }

    public int getStreamWidth() {
        return videoEncoder.getWidth();
    }

    public int getStreamHeight() {
        return videoEncoder.getHeight();
    }

    /**
     * Set video bitrate of H264 in bits per second while stream.
     *
     * @param bitrate H264 in bits per second.
     */

    public void setVideoBitrateOnFly(int bitrate) {
        videoEncoder.setVideoBitrateOnFly(bitrate);
    }

    /**
     * Set limit FPS while stream. This will be override when you call to prepareVideo method.
     * This could produce a change in iFrameInterval.
     *
     * @param fps frames per second
     */
    public void setLimitFPSOnFly(int fps) {
        videoEncoder.setFps(fps);
    }

    /**
     * Get stream state.
     *
     * @return true if streaming, false if not streaming.
     */
    public boolean isStreaming() {
        return streaming;
    }


    /**
     * Get record state.
     *
     * @return true if recording, false if not recoding.
     */


    protected abstract void aacDataToRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info);

    @Override
    public void onAacData(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
        if (streaming) aacDataToRtp(aacBuffer, info);
    }

    protected abstract void onSpsPpsVpsRtp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps);

    @Override
    public void onSpsPps(ByteBuffer sps, ByteBuffer pps) {
        if (streaming) onSpsPpsVpsRtp(sps, pps, null);
    }

    @Override
    public void onSpsPpsVps(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
        if (streaming) onSpsPpsVpsRtp(sps, pps, vps);
    }

    protected abstract void h264DataToRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info);

    @Override
    public void onVideoData(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
        if (streaming) h264DataToRtp(h264Buffer, info);
    }

    @Override
    public void inputPCMData(Frame frame) {
        audioEncoder.inputPCMData(frame);
    }

    @Override
    public void onVideoFormat(MediaFormat mediaFormat) {
    }

    @Override
    public void onAudioFormat(MediaFormat mediaFormat) {
    }
}

