package com.pedro.rtsp.rtp.packets;

import android.media.MediaCodec;
import android.util.Log;

import com.pedro.rtsp.rtsp.RtpFrame;
import com.pedro.rtsp.utils.RtpConstants;

import java.nio.ByteBuffer;

/**
 * Created by pedro on 27/11/18.
 * <p>
 * RFC 3984
 */
public class H264Packet extends BasePacket {
    private final byte[] header = new byte[5];
    private byte[] STAP_A;
    private final VideoPacketCallback videoPacketCallback;
    private boolean sendKeyFrame = false;
    private static final String TAG = "H264Packet";

    public H264Packet(byte[] sps, byte[] pps, VideoPacketCallback videoPacketCallback) {
        super(RtpConstants.clockVideoFrequency);
        this.videoPacketCallback = videoPacketCallback;
        channelIdentifier = (byte) 2;
        setSpsPps(sps, pps);
    }

    @Override
    public void createAndSendPacket(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
        // We read a NAL units from ByteBuffer and we send them
        // NAL units are preceded with 0x00000001
        byteBuffer.rewind();
        byteBuffer.get(header, 0, 5);
        long ts = bufferInfo.presentationTimeUs * 1000L;
        int naluLength = bufferInfo.size - byteBuffer.position() + 1;
        int type = header[4] & 0x1F;
        boolean isKeyFrame = false;
        if (type == RtpConstants.IDR || bufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {//I 帧
            byte[] buffer = getBuffer(STAP_A.length + RtpConstants.RTP_HEADER_LENGTH);
            updateTimeStamp(buffer, ts);
            markPacket(buffer); //mark end frame
            System.arraycopy(STAP_A, 0, buffer, RtpConstants.RTP_HEADER_LENGTH, STAP_A.length);
            updateSeq(buffer);
            RtpFrame rtpFrame = new RtpFrame(buffer, ts, STAP_A.length + RtpConstants.RTP_HEADER_LENGTH, rtpPort, rtcpPort, channelIdentifier);
            rtpFrame.setKeyFrame(true);
            rtpFrame.setSequence(getSeq());
            videoPacketCallback.onVideoFrameCreated(rtpFrame);
            sendKeyFrame = true;
            isKeyFrame = true;
        }

        if (sendKeyFrame) {
            // Small NAL unit => Single NAL unit
            if (naluLength <= maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 2) {
                if (isKeyFrame) {
                    Log.d(TAG, "createAndSendPacket() called with:small key frame ");
                }
                int cont = naluLength - 1;
                int length = Math.min(cont, bufferInfo.size - byteBuffer.position());
                //构建RTP Header
                byte[] buffer = getBuffer(length + RtpConstants.RTP_HEADER_LENGTH + 1);
                //设置NALU header
                buffer[RtpConstants.RTP_HEADER_LENGTH] = header[4];
                byteBuffer.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 1, length);
                updateTimeStamp(buffer, ts);
                markPacket(buffer); //mark end frame
                updateSeq(buffer);
                //   String small=bytesToHexString(buffer);
                RtpFrame rtpFrame = new RtpFrame(buffer, ts, naluLength + RtpConstants.RTP_HEADER_LENGTH, rtpPort, rtcpPort, channelIdentifier);
                rtpFrame.setSequence(getSeq());
                videoPacketCallback.onVideoFrameCreated(rtpFrame);
            } else {
                // Large NAL unit => Split nal unit
                // Set FU-A header
                header[1] = (byte) (header[4] & 0x1F);  // FU header type
                header[1] += 0x80; // set start bit to 1
                // Set FU-A indicator
                header[0] = (byte) ((header[4] & 0x60) & 0xFF); // FU indicator NRI
                header[0] += 28;
                int sum = 1;
                while (sum < naluLength) {
                    int cont = Math.min(naluLength - sum, maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 2);
                    int length = Math.min(cont, bufferInfo.size - byteBuffer.position());
                    byte[] buffer = getBuffer(length + RtpConstants.RTP_HEADER_LENGTH + 2);

                    buffer[RtpConstants.RTP_HEADER_LENGTH] = header[0];
                    buffer[RtpConstants.RTP_HEADER_LENGTH + 1] = header[1];
                    updateTimeStamp(buffer, ts);
                    byteBuffer.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 2, length);
                    sum += length;
                    // Last packet before next NAL
                    if (sum >= naluLength) {
                        // End bit on
                        buffer[RtpConstants.RTP_HEADER_LENGTH + 1] += 0x40;
                        markPacket(buffer); //mark end frame
                    }
                    updateSeq(buffer);
                    RtpFrame rtpFrame = new RtpFrame(buffer, ts, length + RtpConstants.RTP_HEADER_LENGTH + 2, rtpPort, rtcpPort, channelIdentifier);
                    rtpFrame.setSequence(getSeq());
                    videoPacketCallback.onVideoFrameCreated(rtpFrame);
                    // Switch start bit
                    header[1] = (byte) (header[1] & 0x7F);
                }
            }
        }
    }

    public static String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder("");
        if (src == null || src.length <= 0) {
            return null;
        }
        for (byte b : src) {
            int v = b & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv).append(" ");
        }
        return stringBuilder.toString();
    }

    //设置聚合方式:STAP-A单时间聚合
    private void setSpsPps(byte[] sps, byte[] pps) {
        String spsHex = bytesToHexString(sps);
        String ppsHex = bytesToHexString(pps);

        Log.d(TAG, "setSpsPps() called with: sps = [" + spsHex);
        Log.d(TAG, "setSpsPps() called with:  pps = [" + ppsHex + "]");

        STAP_A = new byte[sps.length + pps.length + 5];
        // STAP-A NAL header is 24
        STAP_A[0] = 24;

        //长度占两个字节
        // Write NALU 1 size into the array (NALU 1 is the SPS).
        STAP_A[1] = (byte) (sps.length >> 8);
        STAP_A[2] = (byte) (sps.length & 0xFF);

        // Write NALU 2 size into the array (NALU 2 is the PPS).
        STAP_A[sps.length + 3] = (byte) (pps.length >> 8);
        STAP_A[sps.length + 4] = (byte) (pps.length & 0xFF);

        // Write NALU 1 into the array, then write NALU 2 into the array.
        System.arraycopy(sps, 0, STAP_A, 3, sps.length);
        System.arraycopy(pps, 0, STAP_A, 5 + sps.length, pps.length);
    }

    @Override
    public void reset() {
        super.reset();
        sendKeyFrame = false;
    }
}
