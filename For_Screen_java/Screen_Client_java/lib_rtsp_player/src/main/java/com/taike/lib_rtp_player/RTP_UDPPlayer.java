package com.taike.lib_rtp_player;

import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

import com.taike.lib_rtp_player.rtsp.PlayerThreadPool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Created by Auser on 2018/5/28.
 */

public class RTP_UDPPlayer {
    private static final String TAG = "RTSPPlayer";
    //MediaCodec variable
    private volatile boolean isPlaying = false;
    private int videoPort = 2021;
    private DatagramSocket dataSocket;
    private MulticastSocket multicastSocket;
    private final int[] decodeSize = {0, 0};
    private String h264DataStorePath;
    private final Handler handler;
    static String multiCastHost = "239.0.0.200";
    private final VideoDecoder videoDecoder;
    private boolean isMultiBroadCastMod = true;
    private final static int MAX_UDP_PACKET_LEN = 65507;

    public RTP_UDPPlayer(Surface surface, int w, int h) {
        Log.d(TAG, "RTSP_UDPPlayer() called with: surface = [" + surface + "], w = [" + w + "], h = [" + h + "]");
        HandlerThread handlerThread = new HandlerThread("FUCK h264Data Handler");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        if (isMultiBroadCastMod) {
            initMultiBroadcast();
        }
        videoDecoder = new VideoDecoder(MediaFormat.MIMETYPE_VIDEO_AVC, surface, w, h);
    }

    private void initMultiBroadcast() {
        try {
            multicastSocket = new MulticastSocket(videoPort);
            InetAddress receiveAddress = InetAddress.getByName(multiCastHost);
            multicastSocket.joinGroup(receiveAddress);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /*
    开始播放
     */
    public void startPlay() {
        if (isPlaying) {
            Log.e(TAG, "start play failed.  player is playing");
        } else {
            isPlaying = true;
            startHandleData();
            startDecode();
        }
    }

    /*
     *停止播放
     */
    public void stopPlay() {
        Log.d(TAG, "stopPlay() called");
        isPlaying = false;
    }


    public void pause() {
    }

    public void setH264DataStorePath(String h264DataStorePath) {
        this.h264DataStorePath = h264DataStorePath;
    }


    /*
    开启解码线程
     */
    private void startDecode() {
        videoDecoder.startDecoder();
    }

    public String decode(ByteBuffer bb) {
        Charset charset = StandardCharsets.ISO_8859_1;
        return charset.decode(bb).toString();

    }

    private void submit(Runnable runnable) {
        PlayerThreadPool.getInstance().submit(runnable);
    }

    public int[] getDecodeSize() {
        return decodeSize;
    }

    /*
     *开启RTP or RTCP收包线程
     */
    private void startHandleData() {
        try {
            dataSocket = new DatagramSocket(videoPort);
            //dataSocket.setSoTimeout(1000);

        } catch (SocketException e) {
            e.printStackTrace();
        }
        submit(new Runnable() {
            @Override
            public void run() {
                try {
                    keepConnect();
                    receiveData();
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.d(TAG, "receive data from socket failed");
                } finally {
                    isPlaying = false;
                    if (dataSocket != null) {
                        try {
                            dataSocket.close();
                            Log.e(TAG, "dataSocket close ok.");
                        } catch (Exception e) {
                            dataSocket.close();
                            Log.e(TAG, "dataSocket close failed.", e);
                        }
                    }
                }
            }
        });
    }

    /**
     * 为了让服务器知道客户端还在活着
     */
    private void keepConnect() {
    }

    /**
     * RTSP发起/终结流媒体、RTP传输流媒体数据 、RTCP对RTP进行控制，同步。
     * CTC标准里没有对RTCP进行要求，因此在标准RTSP的代码中没有看到相关的部分。而在私有RTSP的代码中，有关控制、同步等，是在RTP Header中做扩展定义实现的
     * <p>
     * <p>
     * 补码、反码、原码
     * 原码：即真实的二进制编码
     * <p>
     * 补码：
     * 正数：补码=原码
     * 负数：补码=原码符号位不变其它位取反
     * <p>
     * 补码=反码+1
     *
     * <p>
     * java数字的二进制;
     * 正数是用原码来表示的
     * 负数是用补码来表示的
     * <p>
     * H.2364编码知识：
     * [1]DR帧一定是I帧，但I帧不一定是IDR帧；IDR帧的作用是立刻刷新,使错误不致传播,从IDR帧开始算新的序列开始编码。I帧有被跨帧参考的可能,IDR不会。
     * I帧不用参考任何帧，但是之后的P帧和B帧是有可能参考这个I帧之前的帧的。IDR就不允许这样,它还会清空SPS、PPS
     * <p>
     * RTP打包h.264:@see https://blog.csdn.net/shixin_0125/article/details/78798238
     * ---------------------------------------------------------------------------------------------------------------------------------------------------
     * RTP Header;
     * 第一个字节：V：RTP协议的版本号（2位）+P：填充标志(1位)+X：扩展标志(1位)+CC：CSRC计数器(4位)
     * 第二个字节：M: 标记(1位)+PT: 有效荷载类型(7位)
     * 第三、第四字节：序列号（16bit）
     * 第五、六、七、八：时戳(Timestamp)：占32位，必须使用90 kHz 时钟频率。时戳反映了该RTP报文的第一个八位组的采样时刻。接收者使用时戳来计算延迟和延迟抖动，并进行同步控制
     * 第九、十、十一、十二:同步信源(SSRC)标识符：占32位，用于标识同步信源。该标识符是随机选择的，参加同一视频会议的两个同步信源不能有相同的SSRC
     * h264 的vidieo 负载类型可以用 96
     * ------------------------------------------------------------------------------------------------------------------------------------------------------
     * 1.  单NALU:P帧或者B帧比较小的包，直接将NALU打包成RTP包进行传输    RTP header(12bytes) + NALU header (1byte) + NALU payload
     * 打包H264码流时，只需在帧前面加上12字节的RTP头
     * 2. 组合封包模式:多NALU: 特别小的包几个NALU放在一个RTP包中
     * <p>
     * 3.FragmentationUnits (FUs):   I帧长度超过MTU的，就必须要拆包组成RTP包了,有FU-A，FU-B
     * 前12字节为RTP Header  13、14字节分别为 FU indicator FU header (NALU Header的信息被拆分到这两个字节中)
     * 0                  1                  2                  3
     * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 01 2 3 4 5 6 7 8 9 0 1
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * | FU indicator |   FU header  |                              |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+                              |
     * |                                                              |
     * |                        FU payload                           |
     * |                                                              |
     * |                              +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |                              :...OPTIONAL RTP padding        |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * <p>
     * FU-A的分片格式
     * 数据比较大的H264视频包，被RTP分片发送。12字节的RTP头后面跟随的就是FU-A分片：
     * FU indicator有以下格式：
     * +---------------+
     * |0|1|2|3|4|5|6|7|
     * +-+-+-+-+-+-+-+-+
     * |F|NRI|  Type   |
     * +---------------+
     * FU指示字节的类型域 Type=28表示FU-A。。NRI域的值必须根据分片NAL单元的NRI域的值设置。
     * <p>
     * FU header的格式如下：
     * +---------------+
     * |0|1|2|3|4|5|6|7|
     * +-+-+-+-+-+-+-+-+
     * |S|E|R|  Type   |
     * +---------------+
     * S: 1 bit
     * 当设置成1,开始位指示分片NAL单元的开始。当跟随的FU荷载不是分片NAL单元荷载的开始，开始位设为0。
     * E: 1 bit
     * 当设置成1, 结束位指示分片NAL单元的结束，即, 荷载的最后字节也是分片NAL单元的最后一个字节。当跟随的FU荷载不是分片NAL单元的最后分片,结束位设置为0。
     * R: 1 bit
     * 保留位必须设置为0，接收者必须忽略该位。
     * Type: 5 bits
     * <p>
     * <p>
     * <p>
     * 拆包和解包
     * <p>
     * 拆包：当编码器在编码时需要将原有一个NAL按照FU-A进行分片，原有的NAL的单元头与分片后的FU-A的单元头有如下关系：
     * 原始的NAL头的前三位为FU indicator的前三位，原始的NAL头的后五位为FU header的后五位，FUindicator与FU header的剩余位数根据实际情况决定。
     * <p>
     * 解包：当接收端收到FU-A的分片数据，需要将所有的分片包组合还原成原始的NAL包时，FU-A的单元头与还原后的NAL的关系如下：
     * 还原后的NAL头的八位是由FU indicator的前三位加FU header的后五位组成，即：
     * nal_unit_type = (fu_indicator & 0xe0) | (fu_header & 0x1f)
     *
     * <p>
     * RTP header (12bytes)+ FU Indicator (1byte)  +  FU header(1 byte) + NALU payload
     * NALU头不见了，如何判断类型？实际上NALU头被分散填充到FU indicator和FU header里面了
     * bit位按照从左到右编号0-7来算，nalu头中0-2前三个bit放在(FU indicator)的0-2前三个bit中，后3-7五个bit放入(FU header)的后3-7五个中
     * <p>
     * 单 NALU 包比较简单，除了 RTP 头外，后面直接放上去掉开始码得 NALU 即可。如果要多个小包一起，又不想采用聚合包，那么小包之前得加上开始码，否则解码器不认识
     * <p>
     * 荷载结构
     *
     * @throws IOException
     * @throws InterruptedException
     */
    private void receiveData() throws IOException, InterruptedException {
        //RTP包中接收的264包是不含有0x00,0x00,0x00,0x01头的，这部分是rtp接收以后，另外再加上去的，解码的时候再做判断的
        byte frame_head_1 = 0x00;
        byte frame_head_2 = 0x00;
        byte frame_head_3 = 0x00;
        byte frame_head_4 = 0x01;

        byte frame_head_I = 0x65;
        byte frame_head_P = 0x61;

        //byte TYPE_P = 1;
        //byte TYPE_I = 5;
        byte TYPE_SEI = 6;

        byte TYPE_SPS = 7;
        byte TYPE_PPS = 8;
        int nalUnitHeader;
        long lastSq;
        long currSq = 0;

        byte[] receiveByte = new byte[MAX_UDP_PACKET_LEN];
        //从udp读取的数据长度
        //当前帧长度
        int frameLen = 0;
        //完整帧筛选用缓冲区
        int FRAME_MAX_LEN = 3 * 1024 * 1024;
        byte[] frame = new byte[FRAME_MAX_LEN];

        DatagramPacket dataPacket = new DatagramPacket(receiveByte, receiveByte.length);

        while (isPlaying) {
            try {
                if (isMultiBroadCastMod) {
                    multicastSocket.receive(dataPacket);
                } else {
                    dataSocket.receive(dataPacket);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }


            //RTP header占12byte
            int offHeadSize = dataPacket.getLength() - 12;
            //  Log.d(TAG, "receiveData() rtp data offHeadSize:" + offHeadSize);
            if (offHeadSize < 2) {
                isPlaying = false;
                Log.e(TAG, "udp port receive stream failed.");
                continue;
            }


            if (BuildConfig.DEBUG) {
                lastSq = currSq;
                //获取序列号,占16位 (&0xFF 是为了将数据转换为16进制, <<8 是为了把后8位留出来)
                currSq = ((receiveByte[2] & 0xFF) << 8) + (receiveByte[3] & 0xFF);
                if (lastSq != 0 && lastSq != currSq - 1) {
                    Log.e(TAG, "frame data maybe lost.lastSq=" + lastSq + ",currSq=" + currSq + ",count=" + (currSq - lastSq));
                    if (lastSq == currSq || currSq < lastSq) {
                        continue;
                    }
                }
            }


            //前12个byte为RTP Header
            if (frameLen + offHeadSize < FRAME_MAX_LEN) {
                //NAL单元占一个字节
                nalUnitHeader = receiveByte[12] & 0xFF;
                int nalUnitHeaderType = nalUnitHeader & 0x1f;

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "receiveData() called  type=" + nalUnitHeaderType + " currSq:" + currSq);
                }


                // String hex = bytesToHexString(receiveByte);

                //0x67=0110 0111->第0位禁止位（F）默认为0，当网络发现NALU有比特错误时才设置为1，第1、2位为重要性标志位（NRI）取值范围：00-11，SPS、PPS、SEI、I帧该值是11,后五位是NALU类型（TYPE）
                //&0x1F取后五位,即NALU类型 0x1F = 0001 1111
                //SPS:  0x67    header & 0x1F = 7
                //I Frame: 0x65  header & 0x1F = 5
                //PPS:  0x68    header & 0x1F = 8
                //P Frame: 0x41   header & 0x1F = 1
                //SEI:  0x66    header & 0x1F = 6
                if (nalUnitHeaderType == TYPE_SPS/*SPS*/
                        || nalUnitHeaderType == TYPE_PPS/*PPS*/
                        || nalUnitHeaderType == TYPE_SEI/*SEI*/) {

                    if (BuildConfig.DEBUG)
                        Log.d(TAG, " receiveData() called PPS | SPS SEI--------------------<");
                    //加上头部
                    receiveByte[8] = frame_head_1;
                    receiveByte[9] = frame_head_2;
                    receiveByte[10] = frame_head_3;
                    receiveByte[11] = frame_head_4;

                    byte[] idrFrame = Arrays.copyOfRange(receiveByte, 8, offHeadSize + 12);
                    videoDecoder.putData(idrFrame);
                    frameLen = 0;
                    if (isNeedSaveFrame()) {
                        saveH264Frame(idrFrame);
                    }
                } else {
                    switch (nalUnitHeaderType) {
                        case 28:
                            //28->FU-A分片类型NALU，此时nalUnitHeader就是FU indicator；该帧可能是I或者P帧
                            //String raw = bytesToHexString(receiveByte);
                            //Log.d(TAG, "receiveData() called FU-A Raw" + raw);

                            int FUHeader = receiveByte[13] & 0xff;
                            if (FUHeader == 0x85 || FUHeader == 0x81) {
                                receiveByte[9] = frame_head_1;
                                receiveByte[10] = frame_head_2;
                                receiveByte[11] = frame_head_3;
                                receiveByte[12] = frame_head_4;

                                if (FUHeader == 0x85) {
                                    //I帧的第一包
                                    receiveByte[13] = frame_head_I;
                                } else {
                                    //P帧的第一包
                                    receiveByte[13] = frame_head_P;
                                }

                                System.arraycopy(receiveByte, 9, frame, frameLen, offHeadSize + 3);
                                //String fuStar = bytesToHexString(frame);
                                //Log.d(TAG, "receiveData() called FU-A start" + fuStar);
                                frameLen += offHeadSize + 3;
                            } else {
                                System.arraycopy(receiveByte, 14, frame, frameLen, offHeadSize - 2);
                                frameLen += offHeadSize - 2;
                            }

                            if (FUHeader == 0x45 || FUHeader == 0x41) {//I帧或P帧分包结束
                                byte[] endFrame = Arrays.copyOfRange(frame, 0, frameLen);
                                videoDecoder.putData(endFrame);
                                frameLen = 0;
                           //     String fuend = bytesToHexString(frame);
                            //    Log.d(TAG, "receiveData() called FU-A end" + fuend);
                                if (isNeedSaveFrame()) {
                                    saveH264Frame(endFrame);
                                }
                            }
                            break;
                        case 24:
                            //STAP-A 单一时间组合包
                            //  Log.d(TAG, "receiveData() called STAP-A-------- \n" + hex);
                            int spsSize = (receiveByte[13] & 0xff << 8) + receiveByte[14] & 0xff;
                            //  Log.d(TAG, "receiveData() called STAP-A-------- spsSize=" + spsSize);
                            byte[] sps = new byte[spsSize + 4];
                            sps[0] = frame_head_1;
                            sps[1] = frame_head_2;
                            sps[2] = frame_head_3;
                            sps[3] = frame_head_4;
                            System.arraycopy(receiveByte, 15, sps, 4, spsSize);
                            videoDecoder.putData(sps);
                            //String spsHex = bytesToHexString(sps);
                            //   Log.d(TAG, "receiveData() called pps-------- \n" + spsHex);

                            int ppsSizeStart = 14 + spsSize + 1;
                            String ppss = String.format("------------new extra data i=%d,data= %s", ppsSizeStart, intToHex(receiveByte[ppsSizeStart]));
                            Log.i(TAG, ppss);
                            Log.i(TAG, "new extra data spsSize=" + spsSize);
                            int ppsSizeEnd = ppsSizeStart + 1;
                            int ppsSize = (receiveByte[ppsSizeStart] & 0xff << 8) + receiveByte[ppsSizeEnd] & 0xff;
                            Log.i(TAG, "new extra data ppsSize=" + ppsSize);
                            byte[] pps = new byte[ppsSize + 4];
                            pps[0] = frame_head_1;
                            pps[1] = frame_head_2;
                            pps[2] = frame_head_3;
                            pps[3] = frame_head_4;
                            System.arraycopy(receiveByte, ppsSizeEnd + 1, pps, 4, ppsSize);
                            videoDecoder.putData(pps);

                            if (isNeedSaveFrame()) {
                                saveH264Frame(sps);
                                saveH264Frame(pps);
                            }
                            //   String ppsHex = bytesToHexString(pps);
                            //      Log.d(TAG, "receiveData() called pps-------- \n" + ppsHex);
                            //    Log.d(TAG, "receiveData() called data-------- \n" + hex);
                            int retain = receiveByte.length - (ppsSizeEnd + ppsSize) - 1;
                            if (retain > 2) {
                                byte[] idr = new byte[retain + 5];
                                idr[0] = frame_head_1;
                                idr[1] = frame_head_2;
                                idr[2] = frame_head_3;
                                idr[3] = frame_head_4;
                                idr[4] = frame_head_I;
                                System.arraycopy(receiveByte, ppsSizeEnd + ppsSize + 1, idr, 5, retain);
                                //   String idrHex = bytesToHexString(idr);
                                videoDecoder.putData(idr);
                                if (isNeedSaveFrame()) {
                                    saveH264Frame(idr);
                                }
                            }
                            break;

                        case 1://P
                        case 5://I
                            int size = receiveByte.length - 12;
                            byte[] data = new byte[size + 5];
                            data[0] = frame_head_1;
                            data[1] = frame_head_2;
                            data[2] = frame_head_3;
                            data[3] = frame_head_4;
                            if (nalUnitHeaderType == 5) {
                                Log.d(TAG, "receiveData() called I ");
                                data[4] = frame_head_I;
                            } else {
                                data[4] = frame_head_P;
                                Log.d(TAG, "receiveData() called P ");
                            }
                            System.arraycopy(receiveByte, 13, data, 5, size - 1);
                            videoDecoder.putData(data);
                            if (isNeedSaveFrame()) {
                                saveH264Frame(data);
                            }
                            break;
                        default:
                            Log.e(TAG, "receiveData() unKnown frame,type=" + nalUnitHeaderType);
                    }
                }
            } else {
                Log.e(TAG, "receiveData() data to large ");
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

    public static String intToHex(int i) {
        int v = i & 0xFF;
        String hv = Integer.toHexString(v);
        if (hv.length() < 2) {
            return "0" + hv;
        } else {
            return hv;
        }
    }

    private boolean isNeedSaveFrame() {
        return !TextUtils.isEmpty(h264DataStorePath);
    }


    private void saveH264Frame(final byte[] frame) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                File file = new File(h264DataStorePath);
                if (file == null) {
                    return;
                }
                boolean isAppend = file.exists();
                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(file, isAppend);
                    fos.write(frame, 0, frame.length);
                    fos.flush();
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (fos != null) {
                        try {
                            fos.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }

            }
        });
    }

    public boolean isPlaying() {
        return isPlaying;
    }


    public void release() {
        Log.e(TAG, "release() called");
        isPlaying = false;
        videoDecoder.stopDecoder();
    }

    public void setVideoClientPorts(int[] videoClientPorts) {
    }

    public void setAudioClientPorts(int[] audioClientPorts) {
    }


}
