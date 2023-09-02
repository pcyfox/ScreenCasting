#include <stdlib.h>
#include <string.h>
#include <AndroidLog.hpp>
#include <unistd.h>
#include <Utils.h>
#include <RTPUnPacket.h>
#include "malloc.h"


/**
  *
   * RTSP:负责发起/终结流媒体
   * RTP:负责传输流媒体数据
   * RTCP:负责对RTP进行控制，同步,包信息统计：如收发延迟时间，丢包率等。
   *
   * CTC标准里没有对RTCP进行要求，因此在标准RTSP的代码中没有看到相关的部分。而在私有RTSP的代码中，有关控制、同步等，是在RTP Header中做扩展定义实现的
   *
   *
   * 流媒体传输层：UDP
   * 在流媒体实践中,通常会使用UDP作为传输层协议，发送RTP及RTCP数据，并且基于RTCP数据信息来进行带宽预测、拥塞控制、调控码流。
   * 虽然Http具有带宽预测、拥塞控制等功能，但是很少采用它作为RTC场景的传输层，原因是在实施直播流中，丢掉一些数据包是可以忍受的
   *
   * 通常：
   * 丢包率<2%:网络状况很好
   * 2%<丢包率<10%:网络正常
   * 丢包率>10%:网络状况不好
   *
   *
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
   * java数字的二进制;
   * 正数是用原码来表示的
   * 负数是用补码来表示的
   *
   * H.2364编码知识：
   * [1]DR帧一定是I帧，但I帧不一定是IDR帧；IDR帧的作用是立刻刷新,使错误不致传播,从IDR帧开始算新的序列开始编码。I帧有被跨帧参考的可能,IDR不会。
   * I帧不用参考任何帧，但是之后的P帧和B帧是有可能参考这个I帧之前的帧的。IDR就不允许这样,它还会清空SPS、PPS
   * GOP:从一个IDR帧到下一个IDR帧之间的帧序列叫做。
   * <p>
   * RTP打包h.264:@see https://blog.csdn.net/shixin_0125/article/details/78798238


   * ---------------------------------------------------------------------------------------------------------------------------------------------------
   *
   * RTP Header;
   * 第一个字节：V：RTP协议的版本号（2位）+P：填充标志(1位)+X：扩展标志(1位)+CC：CSRC计数器(4位)
   * 第二个字节：M: 标记(1位)+PT: 有效荷载类型(7位)
   * 第三、第四字节：序列号（16bit）
   * 第五、六、七、八：时戳(Timestamp)：占32位，必须使用90 kHz 时钟频率。时戳反映了该RTP报文的第一个八位组的采样时刻。接收者使用时戳来计算延迟和延迟抖动，并进行同步控制
   * 第九、十、十一、十二:同步信源(SSRC)标识符：占32位，用于标识同步信源。该标识符是随机选择的，参加同一视频会议的两个同步信源不能有相同的SSRC
   * h264 的vidieo 负载类型可以用 96
   * ------------------------------------------------------------------------------------------------------------------------------------------------------
   *
   * 常见的三种RTP包结构：
   *
   *
   *
   * 1、 单NALU RTP包:
   * RTP Header+NALU+.........
   * RTP header(12bytes) +(NALU header (1byte) + NALU payload)+......
   *
   * P帧或者B帧比较小的包，直接将NALU打包成RTP包进行传输
   * 打包H264码流时，只需在帧前面加上12字节的RTP头
   *
   * 2. 组合封包模式:多NALU RTP包, 特别小的包几个NALU放在一个RTP包中,如PPS、SPS
   * TYPE=24:STAP-A
   * TYPE=25:STAP-B
   * 如：STAP-A类型组合封包：
   * RTP Header（12 bit） +STAP Header（1 bit） +NALU1 Size（2 bit） +NALU1+ NALU2 Size（2 bit） +NALU2....
   *
   * STAP Header 格式与 NALU Header 格式相同,后五位为TYPE
   *

   *
   * 3.FragmentationUnits (FUs):帧长度超过一定的阀值，就必须要拆包组成RTP包了,有FU-A，FU-B
   * TYPE=28:FU-A
   * TYPE=29:FU-B
   *
   * RTP Header(12 bit) +FU indicator(1 bit) +FU header(1 bit)
   *
   * (NALU中的Header会被剔除，并且NALU Header的信息被拆分到这两个字节中，其中
   * NALU-Header的前三位放在FU-Indicator的前三位
   * 最重要NALU-Header的TYPE放在 FU-Header的type中)
   *
   *
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
   *
   * FU indicator与NALU Heaer格式类似，后五位为TYPE，有以下格式：
   * +---------------+
   * |0|1|2|3|4|5|6|7|
   * +-+-+-+-+-+-+-+-+
   * |F|NRI|  Type   |
   * +---------------+
   *
   * Type=28:表示FU-A
   * Type=29:表示FU-B
   *
   * NRI域的值必须根据分片NAL单元的NRI域的值设置。
   *
   * FU header的格式如下：
   * +---------------+
   * |0|1|2|3|4|5|6|7|
   * +-+-+-+-+-+-+-+-+
   * |S|E|R|  Type   |
   * +---------------+
   * S: 1 bit
   * 当设置成1,开始位指示分片NAL单元的开始。当跟随的FU荷载不是分片NAL单元荷载的开始，开始位设为0。
   * E: 1 bit
   * 当设置成1, 结束位指示分片NAL单元的结束，即荷载的最后字节也是分片NAL单元的最后一个字节。当跟随的FU荷载不是分片NAL单元的最后分片,结束位设置为0。
   * R: 1 bit
   * 保留位必须设置为0，接收者必须忽略该位。
   * Type: 5 bits ，就是NALU Type： 5:IDR,1:P...
   1
   *
   * 拆包和解包
   * 拆包：当编码器在编码时需要将原有一个NAL按照FU-A进行分片，原有的NAL的单元头与分片后的FU-A的单元头有如下关系：
   * 原始的NAL头的前三位为FU indicator的前三位，原始的NAL头的后五位为FU header的后五位，FUindicator与FU header的剩余位数根据实际情况决定。
   *
   * 解包：当接收端收到FU-A的分片数据，需要将所有的分片包组合还原成原始的NAL包时，FU-A的单元头与还原后的NAL的关系如下：
   * 还原后的NAL头的八位是由FU indicator的前三位加FU header的后五位组成，即：
   * nal_unit_type = (fu_indicator & 0xe0) | (fu_header & 0x1f)
   *
   * RTP header (12bytes)+ FU Indicator (1byte)  +  FU header(1 byte) + NALU payload
   * NALU头不见了，如何判断类型？实际上NALU头被分散填充到FU indicator和FU header里面了
   * bit位按照从左到右编号0-7来算，nalu头中0-2前三个bit放在(FU indicator)的0-2前三个bit中，后3-7五个bit放入(FU header)的后3-7五个中
   *
   * 单NALU 包比较简单，除了RTP 头外，后面直接放上去掉起始码得NALU即可。如果要多个小包一起，又不想采用聚合包，那么小包之前得加上起始码，否则解码器不认识
   * 荷载结构
   *
   */

static TempPkt tempPkt = NULL;
static ReceiveDataInfo receiveDataInfo = NULL;


void ResetReceiveDataInfo() {
    receiveDataInfo->start_time = 0;
    receiveDataInfo->receive_count = 0;
    receiveDataInfo->lost_count = 0;
    receiveDataInfo->lost_rate = 0;
}

ReceiveDataInfo analysePkt(const char *rtpPacket) {
    if (receiveDataInfo == NULL) {
        receiveDataInfo = malloc(sizeof(struct RTPDataInfo));
        receiveDataInfo->start_time = getCurrentTime();
        receiveDataInfo->last_Sq = 0;
        receiveDataInfo->receive_count = 0;
    }

    long currSq = ((rtpPacket[2] & 0xFF) << 8) + (rtpPacket[3] & 0xFF);
    if (currSq <= 1) {
        ResetReceiveDataInfo();
    }
    receiveDataInfo->curr_Sq = currSq;

//    LOGD("analysePkt()--------->receive_count=%ld,curr_Sq %ld,lsat_Sq=%ld",
//         receiveDataInfo->receive_count, currSq, receiveDataInfo->last_Sq);
    receiveDataInfo->receive_count++;
    long lostCount = currSq - receiveDataInfo->last_Sq;
    if (currSq != 1 && lostCount != 1) {
        LOGW("analysePkt() maybe lost %ld frame lastSq=%ld,currSq=%ld", lostCount,
             receiveDataInfo->last_Sq, receiveDataInfo->curr_Sq);

        receiveDataInfo->lost_count += abs(lostCount);
    }

    int64_t currentTime = getCurrentTime();

    if (currentTime - receiveDataInfo->start_time >= 5 * 1000) {
        // LOGD("startTime=%ld,lastTime=%ld", receiveDataInfo->start_time, currentTime);
        if (receiveDataInfo->lost_count > 0) {
            receiveDataInfo->lost_rate =
                    (float) receiveDataInfo->lost_count /
                    (float) receiveDataInfo->receive_count;
            if (receiveDataInfo->lost_rate > 0.) {
                LOGD("analysePkt() pkt lost rate=%.2f", receiveDataInfo->lost_rate);
            }
        }
        ResetReceiveDataInfo();
        receiveDataInfo->start_time = currentTime;
    }
    receiveDataInfo->last_Sq = currSq;
    return receiveDataInfo;
}

void resetTempPkt() {
    if (tempPkt == NULL || tempPkt->data == NULL)return;
    memset(tempPkt->data, 0, tempPkt->len);
    tempPkt->index = 0;
    tempPkt->flag = -1;
}

int initTempPkt(int maxFrameLen) {
    if (tempPkt) {
        resetTempPkt();
        return 1;
    }

    if (maxFrameLen <= 0) return -1;
    tempPkt = (TempPkt) malloc(sizeof(struct TempPacket));
    if (!tempPkt)return -1;
    tempPkt->data = (unsigned char *) calloc(maxFrameLen, sizeof(char));
    tempPkt->len = maxFrameLen;
    tempPkt->flag = -1;
    if (!tempPkt->data) {
        LOGE("FU-A frame calloc fail!,size=%d", maxFrameLen);
        return -1;
    }
    return 1;
}

void freeTempPkt() {
    if (tempPkt == NULL) {
        return;
    }
    free(tempPkt->data);
    free(tempPkt);
    tempPkt = NULL;
}


int UnPacket(char *rtpData, const int length, const unsigned int maxFrameLen,
             unsigned int tag, Callback callback) {

    int offHeadSize = length - RTP_HEAD_LEN;
    if (offHeadSize <= 2) {
        LOGE("illegal data,packet is too small");
        return -1;
    }
    analysePkt(rtpData);

    RTPPkt rtpPkt = (RTPPkt) malloc(sizeof(struct RtpPacketInfo));
    rtpPkt->length = 0;
    unsigned long long currSq = ((rtpData[2] & 0xFF) << 8) + (rtpData[3] & 0xFF);
    rtpPkt->curr_Sq = currSq;
    //第13个字节
    int rtpType = rtpPkt->type = (rtpData[RTP_HEAD_LEN] & 0x1F);
    //printCharsHex(rtpData, length, 20, "---RTP---");
    switch (rtpType) {
        // STAP-A:RTP Header（12 bit） +STAP Header（1 bit） +NALU1 Size（2 bit） +NALU1+.....
        case 24: {
            //   printCharsHex(rtpData, length, 20, "---STAP-RTP---");
            LOGD("---------------------SPS-PPS----------------------");
            //--------------SPS------------------------------
            H264Pkt spsPkt = (H264Pkt) malloc(sizeof(struct H264Packet));
            unsigned int spsSize = (rtpData[RTP_HEAD_LEN + 1] << 8) + rtpData[RTP_HEAD_LEN + 2];
            char *sps = (char *) calloc(spsSize + START_CODE_LEN, sizeof(char));
            sps[3] = HEAD_4;
            memcpy(sps + START_CODE_LEN, rtpData + RTP_HEAD_LEN + 3, spsSize);
            spsPkt->length = spsSize + START_CODE_LEN;
            spsPkt->data = sps;
            spsPkt->type = TYPE_SPS;
            callback(spsPkt);

            //--------------PPS------------------------------
            H264Pkt ppsPkt = (H264Pkt) malloc(sizeof(struct H264Packet));
            int ppsSizeStart = RTP_HEAD_LEN + 3 + spsSize;
            int ppsSizeEnd = ppsSizeStart + 1;
            int ppsSize = ((rtpData[ppsSizeStart] & 0xff) << 8) + rtpData[ppsSizeEnd] & 0xff;
            unsigned len = ppsSize + START_CODE_LEN;
            char *pps = (char *) calloc(len, sizeof(char));
            pps[3] = HEAD_4;
            memcpy(pps + START_CODE_LEN, rtpData + ppsSizeEnd + 1, ppsSize);
            ppsPkt->length = len;
            ppsPkt->data = pps;
            ppsPkt->type = TYPE_PPS;
            callback(ppsPkt);

            //--------------IDR------------------------------
            int retain = length - ppsSizeEnd + ppsSize - 1;
            if (retain > 0) {
                //maybe a P frame
                char *idr = (char *) calloc(retain + 5, sizeof(char));
                idr[3] = HEAD_4;
                idr[4] = TYPE_IDR;
                memcpy(idr + 5, rtpData + ppsSizeEnd + ppsSize + 1, retain);
                //    printCharsHex(idr,length+5,length,"idr");
                H264Pkt idrPkt = (H264Pkt) malloc(sizeof(struct H264Packet));
                idrPkt->length = retain + 5;
                idrPkt->data = idr;
                idrPkt->type = TYPE_IDR;
                callback(idrPkt);
            }
            break;
        }
            // FU-A: RTP header (12bytes)+ FU Indicator (1byte)  +  FU header(1 byte) + NALU payload
        case 28: {
            //printCharsHex(rtpData, length, 20, "---FU-A RAW  RTP---");
            // For these NALUs, the first two bytes are the FU indicator （at 13） and the FU header (14).
            // If the start bit is set, we reconstruct the original NAL header into byte 1:
            char FU_Header = rtpData[RTP_HEAD_LEN + 1];//after FU Indicator
            int startCode = FU_Header >> 7;
            int endCode = (FU_Header & 0x40) >> 6;
            //LOGD("---FU-A maxFrameLen=%d,startCode=%d,endCode=%d", maxFrameLen, startCode, endCode);
            if (tempPkt != NULL && tempPkt->index >= tempPkt->len) {
                LOGE("---FU-A pack data error,frameLen>=maxFrameLen!");
                break;
            }

            //start
            if (startCode == 1) {
                LOGD("----------FU-A pack start----------->");
                if (initTempPkt((int) maxFrameLen + START_CODE_LEN) < 0) {
                    LOGE("---FU-A pack data error,cao not init temp pkt!");
                    return -1;
                }
                tempPkt->flag = 0;
                tempPkt->data[3] = HEAD_4;
                int NALU_Type = FU_Header & 0x1F;
                if (NALU_Type == 5) {//I Frame start
                    rtpPkt->type = tempPkt->data[START_CODE_LEN] = TYPE_IDR;
                } else if (NALU_Type == 1) {//P Frame start
                    rtpPkt->type = tempPkt->data[START_CODE_LEN] = TYPE_P;
                } else {
                    LOGW("maybe some thing is wrong in this start packet,type=%d", NALU_Type);
                }
                //printCharsHex(rtpData, length, 20, "PKT-raw");
                //FU payload start at :RTP Header len +FU-Indicator(1byte)+FU-Header(1byte)=14
                int copyLen = offHeadSize - 2;
                memcpy(tempPkt->data + START_CODE_LEN, rtpData + RTP_HEAD_LEN + 2, copyLen);
                tempPkt->index = START_CODE_LEN + copyLen;
                //printCharsHex(tempPkt, length, 20, "---FU-A  START PackedRTP---");
                break;
            }//

            //end or mid
            if (endCode == 1 || startCode == 0 && endCode == 0) {
                if (endCode != 1)
                    LOGD("----------FU-A pack mid-----------");

                if (tempPkt == NULL || tempPkt->data == NULL || tempPkt->flag == 2 ||
                    tempPkt->flag < 0) {
                    LOGW("not found star pkt!");
                    break;
                }
                tempPkt->flag = 1;
                if (tempPkt->index <= START_CODE_LEN) return -1;
                int copyLen = offHeadSize - 2;
                memcpy(tempPkt->data + tempPkt->index, rtpData + RTP_HEAD_LEN + 2, copyLen);
                tempPkt->index += copyLen;

                //en
                if (endCode == 1) {
                    LOGD("----------FU-A pack end-----------|");
                    if (tempPkt->flag != 1) break;
                    tempPkt->flag = 2;
                    if (tempPkt->index <= START_CODE_LEN) return -1;
                    H264Pkt h264_pkt = (H264Pkt) malloc(sizeof(struct H264Packet));
                    h264_pkt->data = (char *) calloc(tempPkt->index, sizeof(char));
                    memcpy(h264_pkt->data, tempPkt->data, tempPkt->index);
                    h264_pkt->length = tempPkt->index;
                    // printCharsHex(h264_pkt->data, h264_pkt->length, 20, "---FU-A  END PackedRTP---");
                    callback(h264_pkt);
                }
            }
            break;
        }
            //single NALU RTP
            // RTP header(12bytes) +(NALU header (1byte) + NALU payload)+......
        case 1:
        case 7:
        case 5: {
            if (rtpType == 7 && IS_DEBUG) {
                printCharsHex(rtpData, length, min(28, length), "---Single RTP---");
            }
            LOGD("---------------------Single RTP(I or P Frame)----------------------");
            //I\P
            char *data = (char *) calloc(offHeadSize + 4, sizeof(char));
            data[3] = HEAD_4;
            memcpy(data + 4, rtpData + RTP_HEAD_LEN, offHeadSize);
            H264Pkt pkt = (H264Pkt) malloc(sizeof(struct H264Packet));
            pkt->length = 4 + offHeadSize;
            pkt->data = data;
            pkt->type = rtpPkt->type;
            callback(pkt);
            break;
        }

        default: {
            LOGE("not support NALU type=%d", rtpPkt->type);
        }
    }
    free(rtpData);
    return 1;
}


void clear() {
    freeTempPkt();
    free(receiveDataInfo);
}
