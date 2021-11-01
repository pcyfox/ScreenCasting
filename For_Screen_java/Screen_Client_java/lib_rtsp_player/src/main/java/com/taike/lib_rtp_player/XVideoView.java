package com.taike.lib_rtp_player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.taike.lib_rtp_player.rtsp.CodecBufferInfoListener;
import com.taike.lib_rtp_player.rtsp.RtspListener;

import java.nio.ByteBuffer;


public class XVideoView extends LinearLayout {
    private RTSPPlayer player;
    private String TAG = "XVideo";
    private int[] videoClientPorts;
    private int[] audioClientPorts;
    private String url;
    private OnXVideoViewStateChangeListener onXVideoViewStateChangeListener;
    private boolean isSurfaceCreated = false;
    private Surface mSurface;

    public XVideoView(@NonNull Context context) {
        super(context);
    }

    public XVideoView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public XVideoView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public XVideoView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    {
        LayoutInflater.from(this.getContext()).inflate(R.layout.layout_xvideo, this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        View tvStart = findViewById(R.id.tv_start);
        View tvStop = findViewById(R.id.tv_stop);
        View tvPause = findViewById(R.id.tv_pause);
        TextView tvTag = findViewById(R.id.x_tv_video_tag);
        Object tag = getTag();
        if (tag != null) {
            tvTag.setText(tag.toString());
            setLogTAG(tag.toString());
        }
        int random = (int) (1000 * Math.random());
        int por1 = 5002 + random;
        int port2 = 5004 + random;

        videoClientPorts = new int[]{por1, port2};
        audioClientPorts = new int[]{por1 + 1, port2 + 1};
        if (BuildConfig.DEBUG) {
            findViewById(R.id.x_ll_buttons).setVisibility(View.VISIBLE);
        }
        final TextureView svVideo = new TextureView(getContext());
        svVideo.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "onSurfaceTextureAvailable() called with: surface = [" + surface + "], width = [" + width + "], height = [" + height + "]");
                isSurfaceCreated = true;
                mSurface = new Surface(surface);
                player = new RTSPPlayer(mSurface, width, height);
                player.setVideoClientPorts(videoClientPorts);
                player.setAudioClientPorts(audioClientPorts);
                if (onXVideoViewStateChangeListener != null) {
                    onXVideoViewStateChangeListener.onSurfaceTextureAvailable(mSurface, surface, width, height);

                    player.setRtspListener(new RtspListener() {
                        @Override
                        public void onCanPlay(String sessionId, int[] ports) {
                            onXVideoViewStateChangeListener.onCanPlay(sessionId, ports);
                        }

                        @Override
                        public void onPlayError(String sessionId, int[] ports) {
                            onXVideoViewStateChangeListener.onPlayError(sessionId, ports);

                        }

                        @Override
                        public void onPauseOver(String sessionId, int[] ports) {
                            onXVideoViewStateChangeListener.onPauseOver(sessionId, ports);

                        }

                        @Override
                        public void onPauseError(String sessionId, int[] ports) {
                            onXVideoViewStateChangeListener.onPauseError(sessionId, ports);

                        }

                        @Override
                        public void onStopOver(String sessionId, int[] ports) {
                            onXVideoViewStateChangeListener.onStopError(sessionId, ports);

                        }

                        @Override
                        public void onStopError(String sessionId, int[] ports) {
                            onXVideoViewStateChangeListener.onStopError(sessionId, ports);

                        }

                        @Override
                        public void onConnectionSuccessRtsp() {
                            onXVideoViewStateChangeListener.onConnectionSuccessRtsp();
                        }

                        @Override
                        public void onConnectionFailedRtsp(String reason) {
                            onXVideoViewStateChangeListener.onConnectionFailedRtsp(reason);

                        }

                        @Override
                        public void onNewBitrateRtsp(long bitrate) {
                            onXVideoViewStateChangeListener.onNewBitrateRtsp(bitrate);

                        }

                        @Override
                        public void onDisconnectRtsp() {
                            onXVideoViewStateChangeListener.onDisconnectRtsp();
                        }

                        @Override
                        public void onAuthErrorRtsp() {
                            onXVideoViewStateChangeListener.onAuthErrorRtsp();
                        }

                        @Override
                        public void onAuthSuccessRtsp() {
                            onXVideoViewStateChangeListener.onAuthSuccessRtsp();
                        }
                    });
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "onSurfaceTextureSizeChanged() called with: surface = [" + surface + "], width = [" + width + "], height = [" + height + "]");

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                player.stopPlay();
                Log.d(TAG, "onSurfaceTextureDestroyed() called with: surface = [" + surface + "]");
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                Bitmap bm = svVideo.getBitmap(player.getDecodeSize()[0], player.getDecodeSize()[1]);
                if (bm != null) {
                    onXVideoViewStateChangeListener.onSurfaceTextureUpdated(bm);
                }
            }
        });


        ViewGroup viewGroup = findViewById(R.id.fl_video);
        viewGroup.addView(svVideo);

        tvStart.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (player != null) {
                    player.startPlay();
                }
            }
        });

        tvStop.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                stop();
            }
        });
        tvPause.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                player.pause();
            }
        });
    }

    private void setPlayUrl(String url) {
        player.setPlayUrl(url);
    }

    public void setLogTAG(String tag) {
        TAG = TAG + "-" + tag;
    }

    public Surface getSurface() {
        return mSurface;
    }

    public int[] getVideoClientPorts() {
        return videoClientPorts;
    }

    public int[] getAudioClientPorts() {
        return audioClientPorts;
    }

    public void setVideoClientPorts(int[] videoClientPorts) {
        this.videoClientPorts = videoClientPorts;
    }

    public void setAudioClientPorts(int[] audioClientPorts) {
        this.audioClientPorts = audioClientPorts;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void release() {
        Log.d(TAG, "release() called");
        if (player != null) {
            player.release();
        }
    }

    public void isJustDecode(boolean isJustDecode) {
        player.setJustDecode(isJustDecode);
    }

    public void start() {
        if (TextUtils.isEmpty(url) || player == null || player.isPlaying()) {
            return;
        }
        Log.d(TAG, "start() called  url=" + url);
        setPlayUrl(url);
        if (isSurfaceCreated) {
            player.startPlay();
        }
    }

    public void stop() {
        Log.d(TAG, "stop() called");
        if (player != null) {
            player.stopPlay();
        }
    }

    public void setCodecBufferInfoListener(CodecBufferInfoListener codecBufferInfoListener) {
        if (player != null) {
            player.setCodecBufferInfoListener(codecBufferInfoListener);
        }
    }

    public void setOnXVideoViewStateChangeListener(OnXVideoViewStateChangeListener onXVideoViewStateChangeListener) {
        this.onXVideoViewStateChangeListener = onXVideoViewStateChangeListener;
    }

    public interface OnXVideoViewStateChangeListener extends RtspListener {

        void onSurfaceTextureAvailable(Surface surface, SurfaceTexture surfaceTexture, int width, int height);

        void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height);

        boolean onSurfaceTextureDestroyed(SurfaceTexture surface);

        void onSurfaceTextureUpdated(Bitmap bitmap);

        void onSurfaceTextureUpdated(ByteBuffer bitmapData, int w, int h);

        class DefOnXVideoViewStateChangeListener implements OnXVideoViewStateChangeListener {


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


            @Override
            public void onSurfaceTextureAvailable(Surface surface, SurfaceTexture surfaceTexture, int width, int height) {

            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(Bitmap bitmap) {

            }

            @Override
            public void onSurfaceTextureUpdated(ByteBuffer bitmapData, int w, int h) {

            }


        }
    }
}
