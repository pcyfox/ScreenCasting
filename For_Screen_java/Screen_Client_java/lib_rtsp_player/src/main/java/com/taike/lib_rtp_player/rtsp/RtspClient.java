package com.taike.lib_rtp_player.rtsp;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by pedro on 10/02/17.
 */

public class RtspClient {
    private final String TAG = "RtspClient";
    private static final Pattern rtspUrlPattern = Pattern.compile("^rtsps?://([^/:]+)(?::(\\d+))*/([^/]+)/?([^*]*)$");
    private final Pattern rtspAuthPatten = Pattern.compile("^rtsp?://(\\w{1,20}):(\\w{1,20}.*?@1)*");
    private final RtspListener connectCheckerRtsp;
    //sockets objects
    private Socket connectionSocket;
    private BufferedReader reader;
    private BufferedWriter writer;
    //for tcp
    private OutputStream outputStream;
    private volatile boolean streaming = false;
    //for secure transport
    private boolean tlsEnabled = false;
    private String url;
    private final CommandsManager commandsManager;
    private int numRetry;
    private int reTries;
    private final Handler handler;
    private Runnable runnable;
    private final Object lock = new Object();

    public RtspClient(RtspListener connectCheckerRtsp) {
        this.connectCheckerRtsp = connectCheckerRtsp;
        commandsManager = new CommandsManager();
        handler = new Handler(Looper.getMainLooper());
    }

    public void setOnlyAudio(boolean onlyAudio) {
        commandsManager.setOnlyAudio(onlyAudio);
    }

    public void setProtocol(Protocol protocol) {
        commandsManager.setProtocol(protocol);
    }

    public void setAuthorization(String user, String password) {
        Log.d(TAG, "setAuthorization() called with: user = [" + user + "], password = [" + password + "]");
        commandsManager.setAuth(user, password);
    }

    public void setReTries(int reTries) {
        numRetry = reTries;
        this.reTries = reTries;
    }

    public boolean shouldRetry(String reason) {
        boolean validReason = !reason.contains("Endpoint malformed");
        return validReason && reTries > 0;
    }

    public boolean isStreaming() {
        return streaming;
    }

    public void setUrl(String url) {
        Log.d(TAG, "setUrl() called with: url = [" + url + "]");
        if (TextUtils.isEmpty(url)) {
            Log.e(TAG, "setUrl() called with: url = [" + url + "]");
            return;
        }
        Matcher rtspMatcher = rtspAuthPatten.matcher(url);
        if (rtspMatcher.find()) {
            String user = rtspMatcher.group(1);
            String psw = rtspMatcher.group(2);
            if (!TextUtils.isEmpty(psw)) {
                psw = psw.substring(0, psw.length() - 2);
            }
            if (!TextUtils.isEmpty(user)) {
                setAuthorization(user, psw);
            }
            this.url = url.replace(user + ":" + psw + "@", "");
            return;
        }
        this.url = url;
    }

    public void setSampleRate(int sampleRate) {
        commandsManager.setSampleRate(sampleRate);
    }

    public String getHost() {
        return commandsManager.getHost();
    }

    public int getPort() {
        return commandsManager.getPort();
    }

    public String getPath() {
        return commandsManager.getPath();
    }

    public RtspListener getConnectCheckerRtsp() {
        return connectCheckerRtsp;
    }

    public void setSPSandPPS(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
        commandsManager.setVideoInfo(sps, pps, vps);
    }

    public String getUrl() {
        return url;
    }

    public void setIsStereo(boolean isStereo) {
        commandsManager.setIsStereo(isStereo);
    }

    public void connect() {
        if (TextUtils.isEmpty(url) || streaming) {
            return;
        }
        Matcher rtspMatcher = rtspUrlPattern.matcher(url);
        if (rtspMatcher.matches()) {
            tlsEnabled = rtspMatcher.group(0).startsWith("rtsps");
        } else {
            streaming = false;
            connectCheckerRtsp.onConnectionFailedRtsp("Endpoint malformed, should be: rtsp://ip:port/appname/streamname");
            return;
        }

        String host = rtspMatcher.group(1);
        int port = Integer.parseInt((rtspMatcher.group(2) != null) ? rtspMatcher.group(2) : "554");
        String group4 = rtspMatcher.group(4);

        if (!TextUtils.isEmpty(group4)) {
            String path = "/" + rtspMatcher.group(3) + "/" + rtspMatcher.group(4);
            commandsManager.setUrl(host, port, path);
        } else {
            String path = "/" + rtspMatcher.group(3);
            commandsManager.setUrl(host, port, path);
        }
        PlayerThreadPool.getInstance().submit(new Runnable() {
            @Override
            public void run() {
                try {
                    connectionSocket = new Socket();
                    SocketAddress socketAddress =
                            new InetSocketAddress(commandsManager.getHost(), commandsManager.getPort());
                    connectionSocket.connect(socketAddress, 5000);
                    connectionSocket.setSoTimeout(5000);
                    reader = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
                    outputStream = connectionSocket.getOutputStream();
                    writer = new BufferedWriter(new OutputStreamWriter(outputStream));
                    String options = commandsManager.createOptions();
                    writer.write(options);
                    writer.flush();
                    commandsManager.getResponse(reader, connectCheckerRtsp, false, false);

                    //检测是否授权
                    String describe = commandsManager.createDescribe();
                    writer.write(describe);
                    writer.flush();
                    String dpResp = commandsManager.getResponse(reader, connectCheckerRtsp, false, false);
                    int describeStatus = commandsManager.getResponseStatus(dpResp);
                    if (describeStatus == 403) {
                        connectCheckerRtsp.onConnectionFailedRtsp("Error configure stream, access denied");
                        Log.e(TAG, "Response 403, access denied");
                        return;
                    } else if (describeStatus == 401) {//未授权
                        if (commandsManager.getUser() == null || commandsManager.getPassword() == null) {
                            connectCheckerRtsp.onAuthErrorRtsp();
                            return;
                        } else {
                            //开始请求授权
                            String describeWithAuth = commandsManager.createDescribeWithAuth(dpResp);
                            writer.write(describeWithAuth);
                            writer.flush();
                            String authResp = commandsManager.getResponse(reader, connectCheckerRtsp, false, false);
                            int statusAuth = commandsManager.getResponseStatus(authResp);
                            Log.d(TAG, "onAuthSuccessRtsp  result;" + authResp);
                            //授权结果
                            if (statusAuth == 401) {
                                connectCheckerRtsp.onAuthErrorRtsp();
                                return;
                            } else if (statusAuth == 200) {//授权成功
                                startToConnect();
                            }
                        }
                    } else if (describeStatus== 200) {
                        startToConnect();
                    }
                    streaming = true;
                    reTries = numRetry;
                    connectCheckerRtsp.onConnectionSuccessRtsp();
                } catch (IOException | NullPointerException e) {
                    e.printStackTrace();
                    connectCheckerRtsp.onConnectionFailedRtsp("Error configure stream Exception:, " + e.getMessage());
                    streaming = false;
                }
            }
        });
    }

    private void startToConnect() throws IOException {
        connectCheckerRtsp.onAuthSuccessRtsp();
        //建立连接
        writer.write(commandsManager.createSetup(commandsManager.getTrackVideo()));
        writer.flush();
        String setupResp = commandsManager.getResponse(reader, connectCheckerRtsp, false, true);
        int setUpStatus = commandsManager.getResponseStatus(setupResp);
        if (setUpStatus == 200) {
            doPlay();
        } else {
            connectCheckerRtsp.onPlayError(commandsManager.getSessionId(), commandsManager.getVideoClientPorts());
        }
    }


    private void doPlay() {
        if (writer == null || connectionSocket == null || connectionSocket.isClosed()) {
            return;
        }
        try {
            writer.flush();
            writer.write(commandsManager.createPlay());
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        String sendPlayResp = commandsManager.getResponse(reader, connectCheckerRtsp, false, true);
        int sendPlayStatus = commandsManager.getResponseStatus(sendPlayResp);
        if (sendPlayStatus == 200) {
            connectCheckerRtsp.onCanPlay(commandsManager.getSessionId(), commandsManager.getVideoClientPorts());
        } else {
            connectCheckerRtsp.onPlayError(commandsManager.getSessionId(), commandsManager.getVideoClientPorts());
        }
    }

    public void play() {
        PlayerThreadPool.getInstance().submit(new Runnable() {
            @Override
            public void run() {
                doPlay();
            }
        });
    }

    public void pause() {
        PlayerThreadPool.getInstance().submit(new Runnable() {
            @Override
            public void run() {
                doPause();
            }
        });
    }

    private void doPause() {
        if (writer == null || connectionSocket == null || connectionSocket.isClosed()) {
            return;
        }
        try {
            writer.flush();
            writer.write(commandsManager.createPause());
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        String sendPlayResp = commandsManager.getResponse(reader, connectCheckerRtsp, false, true);
        int sendPlayStatus = commandsManager.getResponseStatus(sendPlayResp);
        if (sendPlayStatus == 200) {
            connectCheckerRtsp.onPauseOver(commandsManager.getSessionId(), commandsManager.getVideoClientPorts());
        } else {
            connectCheckerRtsp.onPauseError(commandsManager.getSessionId(), commandsManager.getVideoClientPorts());
        }
    }

    public void write(String param) {
        if (TextUtils.isEmpty(param) || writer == null) {
            return;
        }
        try {
            writer.write(param);
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 当发送SETUP后服务端会返回协商好的SDP，其中最重要的一段：
     * a=fmtp:96 profile-level-id=420029; packetization-mode=1; sprop-parameter-sets=Z00AMpY1QFAB403AQEBQAABwgAAV+QBA,aO48gA==
     * packetization-mode：决定RTP分包模式：
     * 1、单一NAL单元模式（ Single NAL unit mode）：packetization-mode = 0 或者无此字段时缺省
     * 2、非交错模式（Non-interleaved mode）: packetization-mode = 1
     * 3、交错模式（Interleaved mode）: packetization-mode = 2
     */
    public void sendGetParam() {
        write(commandsManager.createGetParameter());
    }

    public void disconnect() {
        handler.removeCallbacks(runnable);
        disconnect(true);
    }

    private void disconnect(final boolean clear) {
        streaming = false;
        PlayerThreadPool.getInstance().submit(new Runnable() {
            @Override
            public void run() {
                try {
                    if (writer != null) {
                        writer.write(commandsManager.createTeardown());
                        writer.flush();
                        String sendPlayResp = commandsManager.getResponse(reader, connectCheckerRtsp, false, false);
                        int sendPlayStatus = commandsManager.getResponseStatus(sendPlayResp);
                        if (sendPlayStatus == 200) {
                            connectCheckerRtsp.onStopOver(commandsManager.getSessionId(), commandsManager.getVideoClientPorts());
                        } else {
                            connectCheckerRtsp.onStopError(commandsManager.getSessionId(), commandsManager.getVideoClientPorts());
                        }
                        if (clear) {
                            commandsManager.clear();
                        } else {
                            commandsManager.retryClear();
                        }
                    }

                    if (connectionSocket != null && !connectionSocket.isClosed()) {
                        connectionSocket.close();
                    }
                    writer = null;
                    connectionSocket = null;
                } catch (IOException e) {
                    if (clear) {
                        commandsManager.clear();
                    } else {
                        commandsManager.retryClear();
                    }
                    Log.e(TAG, "disconnect error", e);
                }
            }
        });
        if (clear) {
            reTries = 0;
            connectCheckerRtsp.onDisconnectRtsp();
        }
    }

    public void setVideoClientPorts(int[] videoClientPorts) {
        commandsManager.setVideoClientPorts(videoClientPorts);
    }

    public void setAudioClientPorts(int[] audioClientPorts) {
        commandsManager.setAudioClientPorts(audioClientPorts);
    }


    public void reConnect(long delay) {
        reTries--;
        disconnect(false);
        runnable = new Runnable() {
            @Override
            public void run() {
                connect();
            }
        };
        handler.postDelayed(runnable, delay);
    }
}

