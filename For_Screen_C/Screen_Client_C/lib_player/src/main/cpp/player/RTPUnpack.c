//
// Created by LN on 2021/3/1.
//
#include <stdlib.h>
#include <string.h>
#include <Android_log.h>
#include <unistd.h>
#include <Utils.h>
#include <RTPUnPacket.h>
#include "malloc.h"

#define head_1  0x00
#define head_2  0x00
#define head_3  0x00
#define head_4  0x01
#define head_I  0x65
#define head_P  0x61
#define RTP_HEAD_LEN  12
#define RTP_LITE_HEADER_LEN  2


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
   * 常见的三种RTP包结构：
   * <p>
   * 1.  单NALU:P帧或者B帧比较小的包，直接将NALU打包成RTP包进行传输    RTP header(12bytes) + NALU header (1byte) + NALU payload
   * 打包H264码流时，只需在帧前面加上12字节的RTP头
   * <p>
   * 2. 组合封包模式:多NALU: 特别小的包几个NALU放在一个RTP包中
   * <p>
   * 3.FragmentationUnits (FUs):   I帧长度超过MTU的，就必须要拆包组成RTP包了,有FU-A，FU-B
   * 前12字节为RTP Header  13、14字节分别为 FU indicator FU header (NALU Header的信息被拆分到这两个字节中)
   * <p>
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
   * FU indicator有以下格式：
   * +---------------+
   * |0|1|2|3|4|5|6|7|
   * +-+-+-+-+-+-+-+-+
   * |F|NRI|  Type   |
   * +---------------+
   * FU指示字节的类型域 Type=28表示FU-A。NRI域的值必须根据分片NAL单元的NRI域的值设置。
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
   * 当设置成1, 结束位指示分片NAL单元的结束，即荷载的最后字节也是分片NAL单元的最后一个字节。当跟随的FU荷载不是分片NAL单元的最后分片,结束位设置为0。
   * R: 1 bit
   * 保留位必须设置为0，接收者必须忽略该位。
   * Type: 5 bits  5:IDR,1:P
   *
   *
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
   */

struct S_ReceiveDataInfo {
    unsigned long startTime;
    unsigned long receiveCount;
    unsigned int lostCount;
} typedef *ReceiveDataInfo;

static int isDebug = -1;
static unsigned long long last_Sq = 0;
static unsigned char *frame = NULL;
static unsigned int frameLen = 0;

ReceiveDataInfo receiveDataInfo = NULL;


void printCharsHex(char *data, int length, int printLen, char *tag) {
    LOGD("-----------------------------%s-printLen=%d--------------------------------------->", tag,
         printLen);
    if (printLen > length) {
        return;
    }
    for (int i = 0; i < printLen; ++i) {
        LOGD("------------printChars() TAG=%s:i=%d,char=%02x", tag, i, *(data + i));
    }
}

void ClearReceiveDataInfo() {
    receiveDataInfo->receiveCount = 0;
    receiveDataInfo->lostCount = 0;
}

int UnPacket(unsigned char *rtpPacket, const unsigned int length, const unsigned int maxFrameLen,
             unsigned int isLiteMod,/*是否为RTP青春版*/
             Callback callback) {

    if (isDebug && receiveDataInfo == NULL) {
        receiveDataInfo = malloc(sizeof(struct S_ReceiveDataInfo));
        receiveDataInfo->startTime = getCurrentTime();
        receiveDataInfo->receiveCount = 0;
    }

    unsigned int headerLen = isLiteMod == 1 ? RTP_LITE_HEADER_LEN : RTP_HEAD_LEN;
    LOGD("isLiteMod=%d",isLiteMod);
    int offHeadSize = length - headerLen;
    if (offHeadSize < 2) {
        LOGE("illegal data,packet is too small");
        return -1;
    }
    if (receiveDataInfo != NULL) {
        receiveDataInfo->receiveCount++;
    }

    UnpackResult result = (UnpackResult) malloc(sizeof(struct RtpUnpackResult));
    result->length = 0;
    result->data = NULL;

    unsigned long long currSq = isLiteMod ? ((rtpPacket[0] & 0xFF) << 8) + (rtpPacket[1] & 0xFF)
                                          : ((rtpPacket[2] & 0xFF) << 8) + (rtpPacket[3] & 0xFF);

    result->curr_Sq = currSq;
    if (last_Sq != 0 && currSq != 0) {
        result->pkt_interval = currSq - last_Sq;
        if (result->pkt_interval < 0) {
            result->pkt_interval = -result->pkt_interval;
        }
        if(result->pkt_interval==0){
            return -1;
        }
        if (isDebug && result->pkt_interval != 1) {
            receiveDataInfo->lostCount += result->pkt_interval;
            LOGW("maybe lost %d frame lastSq=%lld,currSq=%lld", result->pkt_interval, last_Sq,
                 currSq);
        }

        if (isDebug) {
            if (currSq <= 1) {
                ClearReceiveDataInfo();
            }
            int64_t currentTime = getCurrentTime();
            if (currentTime - receiveDataInfo->startTime >= 5 * 1000) {
                if (receiveDataInfo->lostCount > 0) {
                    LOGD("pkt lostCount-receiveCount=%d-%ld",
                         receiveDataInfo->lostCount, receiveDataInfo->receiveCount);
                }
                double lostRate =
                        (double) receiveDataInfo->lostCount /
                        (double) receiveDataInfo->receiveCount;
                if (lostRate > 0) {
                    LOGD("pkt lost rate=%.2lf", lostRate);
                }
                receiveDataInfo->startTime = currentTime;
                ClearReceiveDataInfo();
            }
        }
    }

    last_Sq = currSq;
    //第13个字节
    result->packet_NAL_unit_type = (rtpPacket[headerLen] & 0x1F);
    if (result->packet_NAL_unit_type != 28) {
        frameLen = 0;
        if (frame != NULL) {
            free(frame);
            frame = NULL;
        }
    }

    //LOGD("------------------ NAL TYPE %d--------------",result->packet_NAL_unit_type);
    switch (result->packet_NAL_unit_type) {
        case 1://P
        case 6:
        case 7:
        case 8:
        case 5: {
            unsigned char *data = (unsigned char *) calloc(offHeadSize + 4, sizeof(char));
            data[3] = head_4;
            memcpy(data + 4, rtpPacket + headerLen, offHeadSize);
            result->length = 4 + offHeadSize;
            result->data = data;
            callback(result);
            break;
        }

        case 24: { // STAP-A
            //--------------SPS------------------------------
            // LOGD("------------------SPS PPS------------------------------------------------");
            unsigned int spsSize = (rtpPacket[headerLen + 1] << 8) + rtpPacket[headerLen + 2];
            unsigned char *sps = (unsigned char *) calloc(spsSize + 4, sizeof(char));
            sps[3] = head_4;
            memcpy(sps + 4, rtpPacket + headerLen + 3, spsSize);
            result->length = spsSize + 4;
            result->data = sps;
            callback(result);

            //--------------PPS------------------------------
            unsigned int ppsSizeStart = headerLen + spsSize + 3;
            unsigned int ppsSizeEnd = ppsSizeStart + 1;
            int ppsSize = ((rtpPacket[ppsSizeStart] & 0xff) << 8) + rtpPacket[ppsSizeEnd] & 0xff;
            unsigned len = ppsSize + 4;
            unsigned char *pps = (unsigned char *) calloc(len, sizeof(char));
            pps[3] = head_4;
            memcpy(pps + 4, rtpPacket + ppsSizeEnd + 1, ppsSize);
            UnpackResult ppsResult = (UnpackResult) malloc(sizeof(struct RtpUnpackResult));
            ppsResult->length = len;
            ppsResult->data = pps;
            ppsResult->packet_NAL_unit_type = result->packet_NAL_unit_type;
            ppsResult->pkt_interval = result->pkt_interval;
            callback(ppsResult);

            //--------------IDR------------------------------
            int retain = length - (ppsSizeEnd + ppsSize) - 1;
            if (retain > 0) {
                LOGD("------STAP has other data %d", retain);
                unsigned char *idr = (unsigned char *) calloc(retain + 5, sizeof(char));
                idr[3] = head_4;
                idr[4] = head_I;
                memcpy(idr + 5, rtpPacket + ppsSizeEnd + ppsSize + 1, retain);
                UnpackResult dataResult = (UnpackResult) malloc(sizeof(struct RtpUnpackResult));
                dataResult->length = retain + 5;
                dataResult->data = idr;
                dataResult->packet_NAL_unit_type = result->packet_NAL_unit_type;
                dataResult->pkt_interval = result->pkt_interval;
                callback(dataResult);
            }
            break;
        }

        case 25:
        case 26:
        case 27: { // STAP-B, MTAP16, or MTAP24
            break;
        }

        case 28: { // FU-A
            if (frame == NULL) {
                frame = (unsigned char *) calloc(maxFrameLen, sizeof(char));
                if (frame == NULL) {
                    LOGE("FU-A frame calloc fail!,size=%d", maxFrameLen);
                    return -1;
                }
            }
            // For these NALUs, the first two bytes are the FU indicator （at 13） and the FU header (14).
            // If the start bit is set, we reconstruct the original NAL header into byte 1:
            int FU_Header = rtpPacket[headerLen + 1] & 0xFF;

            if (FU_Header == 0x85 || FU_Header == 0x81) {
                frame[3] = head_4;
                if (FU_Header == 0x85) {
                    //I Frame start
                    frame[4] = head_I;
                    result->packet_NAL_unit_type = head_I;
                } else {
                    //P Frame start
                    result->packet_NAL_unit_type = head_P;
                    frame[4] = head_P;
                }
                frameLen = 0;
                //  printCharsHex(rtpPacket, length, headerLen + 5, "PKT-raw");
                //14=RTP Header len +FU-Indicator+FU-Header
                memcpy(frame + frameLen + 5, rtpPacket + headerLen + 2, offHeadSize - 2);
                frameLen += offHeadSize + 3;
                //  printCharsHex(frame, length, headerLen + 5, "PKT-copy");
            } else {
                //14=RTP Header len +FU-Indicator+FU-Header
                memcpy(frame + frameLen, rtpPacket + headerLen + 2, offHeadSize - 2);
                frameLen += offHeadSize - 2;
            }

            if (frameLen >= maxFrameLen) {
                frameLen = 0;
                break;
                LOGE("FU-A pack data error,frameLen>=maxFrameLen!");
            }
            //0100 000-type:S=0 E=1 分片帧结束 R=0:
            //IDR帧结束：0100 0001
            //P帧结束：  0100 0005
            if (FU_Header == 0x45 || FU_Header == 0x41) {//I帧或P帧分包结束
                result->data = (unsigned char *) calloc(frameLen, sizeof(char));
                memcpy(result->data, frame, frameLen);
                result->length = frameLen;
                callback(result);
                memset(frame, 0, frameLen);
                frameLen = 0;
            }
            break;
        }
        default: {
            LOGW("not support NAL ,type=%d", result->packet_NAL_unit_type);
        }
    }
    free(rtpPacket);
    rtpPacket = NULL;
    return 1;
}
