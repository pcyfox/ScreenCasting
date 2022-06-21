//
// Created by LN on 2021/3/11.
//

#include "include/H264Pack.h"
#include <ResultCode.h>
#include <stddef.h>
#include <malloc.h>
#include <string.h>
#include <stdlib.h>
#include <Android_log.h>
#include <limits.h>

#define  TIME_BASE 1000000L
//#define  TIME_BASE 1000000000L
#define  RTP_HEADER_LEN  12


/**
******************************************************************
*
RTP_FIXED_HEADER:

0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|V=2|P|X|  CC   |M|     PT      |       sequence number         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                           timestamp                           |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|           synchronization source (SSRC) identifier            |
+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
|            contributing source (CSRC) identifiers             |
|                             ....                              |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| FU indicator  |   FU header   |                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+                               |
|                                                               |
|                         FU payload                            |
|                                                               |
|                               +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                               :...OPTIONAL RTP padding        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

FU-A:
							+---------------+
+-+-+-+-+-+-+-+-+           |0|1|2|3|4|5|6|7|
| FU indicator  |  < == >   +-+-+-+-+-+-+-+-+
+-+-+-+-+-+-+-+-+           |F|NRI|  Type   |
							+---------------+

							+---------------+
+-+-+-+-+-+-+-+-+           |0|1|2|3|4|5|6|7|
| FU header     |   < == >  +-+-+-+-+-+-+-+-+
+-+-+-+-+-+-+-+-+           |S|E|R|  Type   |
							+---------------+


————————————————
*SSRC:同步信源是指产生媒体流的信源，例如麦克风、摄像机、RTP混合器等；它通过RTP报头中的一个32位数字SSRC标识符来标识，而不依赖于网络地址，
* 接收者将根据SSRC标识符来区分不同的信源，进行RTP报文的分组。
*CSRC:特约信源是指当混合器接收到一个或多个同步信源的RTP报文后，经过混合处理产生一个新的组合RTP报文，
*并把混合器作为组合RTP报文的 SSRC,而将原来所有的SSRC都作为CSRC传送给接收者，使接收者知道组成组合报文的各个SSRC。
 *
 *FU indicator :
 *F、 NRI 来自 NAL Header 中的 F、 NRI
 *FU-A的Type是固定的28

 *FU Header：
    S: Start，说明是分片的第一包,当设置成1,指示分片NAL单元的开始。0表示不是开始的包
    E: End，如果是分片的最后一包，设置为 1，否则为0
    R: Remain，保留位，默认是 0
    Type: 是 NAlU Type，是关键帧是5，P帧是1
 *
 *
 * H264基本码流由一些列的NALU组成。原始的NALU单元组成：
 * [start code] + [NALU header] + [NALU payload]；
 *
 * NALU Header (F+NIR+Type):
 +---------------+
 |0|1|2|3|4|5|6|7|
 +-+-+-+-+-+-+-+-+
 |F|NRI| Type    |
 +---------------+

 * forbidden_zero_bit(1bit) + nal_ref_idc(2bit) + nal_unit_type(5bit)
 * F：禁止为，0表示正常，1表示错误，一般都是0
 * NRI：重要级别，11表示非常重要。
 * TYPE：表示该NALU的类型是什么
 *
 * NALU通常的类型有
 * |-F-NRI-Type-|
 * P:0 11 00001  =0110 0001=0x61
 * P:0 10 00001  =0100 0001=0x41
 *
 * I:0 11 00101  =0110 0101=0x65
 * P:0 10 00101  =0100 0101=0x45
 *
 *
 *FU indicator :
 *  0 11 11100   =0x7c
 *  0 10 11100   =0x5c
 *
 *I帧FU-A分片
 *FU Header:
 *--S-E-R-type-|
 *  1 0 0 00101  =0x85  (I帧分片开始)
 *  0 1 0 00101  =0x45  (I帧分片结束)
 *
 *  0 0 0 00001  =0x01  (I帧分片中间片段)
 *  0 0 0 00101  =0x01  (p帧分片中间片段)
 *
 *  1 0 0 00001  =0x81  (p帧分片开始)
 *  0 1 0 00001  =0x41  (p帧分片结束)
 *
 ******************************************************************
*/
struct Packet {
    unsigned char *pkt;
    unsigned int len;
} typedef *STAP_A, *Pkt;


static STAP_A stapA = NULL;
static Pkt screenInfo = NULL;
static int ssrc = 0;
static unsigned int headerLen = RTP_HEADER_LEN;

unsigned int max(unsigned int a, unsigned int b) {
    return a > b ? a : b;
}

unsigned int min(unsigned int a, unsigned int b) {
    return a < b ? a : b;
}

void setLong(unsigned char *buffer, unsigned long n, int begin, int end) {
    for (end--; end >= begin; end--) {
        buffer[end] = n % 256;
        n >>= 8;
    }
}


void freeSTAP() {
    if (stapA != NULL) {
        free(stapA->pkt);
        stapA = NULL;
    }
}


int GetStartCodeLen(const unsigned char *pkt) {
    if (pkt[3] == 1 && pkt[0] == 0 && pkt[1] == 0 && pkt[2] == 0) {
        return 4;
    } else if (pkt[2] == 1 && pkt[0] == 0 && pkt[1] == 0) {
        return 3;
    } else {
        return 0;
    }
}


Result
AddRTPHeader(const unsigned long ts, const unsigned long marker_bit, unsigned long clock,
             const unsigned char *data,
             unsigned int len) {


    Result result = malloc(sizeof(struct PackResult));
    if (result == NULL) {
        LOGE("GetSPS_PPS_RTP_STAP_Pkt(),malloc UnpackResult fail");
        return NULL;
    }
    result->length = len + headerLen;
    result->data = (unsigned char *) calloc(result->length, sizeof(char));

    if (result->data == NULL) {
        LOGE("GetSPS_PPS_RTP_STAP_Pkt(),malloc UnpackResult data fail");
        return NULL;
    }

    result->h264StartCodeLen = GetStartCodeLen(data);
    /**
     * -------------------第1个字节---------------------------------
     * V(2 bit) 版本号通常为2，即：10
     * P(1 bit) 填充标志，P-1,则在包尾部填充一个或多个额外的8bit，此处取P=0
     * X(1 bit) 扩抓标志位，X=1，则在RTP头后跟随一个扩展报头
     * CC(4 bit) 特约信源(CSRC)计数器：每个CSRC标识符占32位，可以有0～15个。每个CSRC标识了包含在该RTP报文有效载荷中的所有特约信源
     *
     * -------------------第2个字节---------------------------------
     * M(1 bit) 标记，不同的有效载荷有不同的含义，对于视频，标记一帧的结束；对于音频，标记会话的开始。
     * PT(7 bit) PlayLoad Type,有效载荷类型，用于说明RTP报文中有效载荷的类型，如GSM音频、JPEM图像等,在流媒体中大部分是用来区分音频流和视频流的，这样便于客户端进行解析：
     *264的PT值为96
     *有些负载类型由于诞生的较晚，没有具体的PT值，只能使用动态（dynamic）PT值，即96到127，这就是为什么大家普遍指定H264的PT值为96
     *
     * -------------------第3、4字节---------------------------------
     *sequence number(16 bit) 序列号，用于标识发送者所发送的RTP报文的序列号，每发送一个报文，序列号增1。这个字段当下层的承载协议用UDP的时候，网络状况不好的时候可以用来检查丢包。同时出现网络抖动的情况可以用来对数据进行重新排序，在helix服务器中这个字段是从0开始的，同时音频包和视频包的sequence是分别记数的
     *
     * -------------------第5、6、7、8字节---------------------------------
     *Timestamp(32bit) 时间戳，时戳反映了该RTP报文的第一个八位组的采样时刻。接收者使用时戳来计算延迟和延迟抖动，并进行同步控制
     *
     * -------------------第9、10、11、12---------------------------------
     * SSRC(32 bit)  同步信源标识符：用于标识同步信源。该标识符是随机选择的，参加同一视频会议的两个同步信源不能有相同的SSRC
     *
     * -------------------第13、14、15、16---------------------------------
     * CSRC(32 bit) 特约信源标识符：每个CSRC标识符占32位，可以有0～15个。每个CSRC标识了包含在该RTP报文有效载荷中的所有特约信源
     *
     */

    unsigned char *RTPData = result->data;
    RTPData[0] = 0x80;//V P X CC->10 0 0 0000=0x80\十进制的28
    // H264 payload  is dynamic  payload(96-127) and marker bit
    RTPData[1] = 0x60 + (marker_bit << 7);
    //add sequence number
    setLong(RTPData, ++cq, 2, 4);
    //添加4bit的的时间戳
    unsigned long timestamp = ts * clock / TIME_BASE;
    setLong(RTPData, timestamp, 4, 8);

    if (ssrc == 0) {
        ssrc = rand();
    }
    //同步信源(SSRC)标识符：占32位，用于标识同步信源。该标识符是随机选择的，参加同一视频会议的两个同步信源不能有相同的SSRC
    setLong(RTPData, ssrc, 8, 12);
    memcpy(RTPData + headerLen, data + result->h264StartCodeLen, len - result->h264StartCodeLen);
    return result;
}


Result GetSPS_PPS_RTP_STAP_Pkt(const unsigned long ts, unsigned long clock) {
    if (stapA != NULL) {
        return AddRTPHeader(ts, 1, clock, stapA->pkt, stapA->len);
    }
    return NULL;
}


Result GetScreenInfo(const unsigned long ts, unsigned long clock) {
    if(screenInfo){
        return AddRTPHeader(ts, 1, clock, screenInfo->pkt, screenInfo->len);
    }

    return NULL;
}


void GetFU_A_Pkt(const unsigned long ts, unsigned long clock, unsigned char *h264Pkt,
                 unsigned int length, unsigned int maxPktLen, Callback callback) {

}

int PackRTP(unsigned char *h264Pkt,
            const unsigned int length,
            const unsigned int maxPktLen,
            const unsigned long ts,
            unsigned int clock,
            int tag,
            Callback callback) {

    int8_t naluType = h264Pkt[4] & 0x1F;
    headerLen = RTP_HEADER_LEN;

    //如果遇到I帧，则先通过STAP-A单聚合方式创建一个包含sps、pps的RTP包发送出去
    if (naluType == 5) {
        callback(GetScreenInfo(ts, clock));
        callback(GetSPS_PPS_RTP_STAP_Pkt(ts, clock));
    }

    int startCodeLen = GetStartCodeLen(h264Pkt);
    if (cq >= ULONG_MAX) {
        cq = 0;
    }

    // 小于规定的最，直接打包成RTP包，不管是什么类型的帧,即单NALU模式
    if (length <= maxPktLen - headerLen) {
        Result result = AddRTPHeader(ts, 1, clock, h264Pkt, length);
        callback(result);
        //printCharsHex(result->data, length, 20, "Single Raw Small");
    } else {
        //create FU-A pkt
        unsigned int currentIndex = 0;
        unsigned int remainLen;

        while (currentIndex < length) {
            remainLen = length - currentIndex;
            if (remainLen <= 0) {
                break;
            }
            unsigned int bufSize = maxPktLen - headerLen;
            unsigned char *buf = calloc(bufSize, sizeof(char));
            //1、add RTP header
            Result result = AddRTPHeader(ts, currentIndex == 0 ? 0 : 1, clock, buf, bufSize);
            free(buf);
            buf = NULL;
            unsigned char NALU_header = h264Pkt[4];

            //2、set FU-Indicator at  NALU-Header position
            //FU-Indicator的F、 NRI 来自 NAL Header 中的 F、 NRI
            //FU-A类型分片的Type是固定的28
            //取NALU Header中的前三位（11100000=0ce0）加上type=28=0x1c;
            result->data[headerLen] = (NALU_header & 0xe0) | 0x1c;

            //3、set FU-Header
            //RTP Header(12 bit) +FU indicator(1 bit) +FU header(1 bit)
            unsigned int copyLen = min(remainLen, maxPktLen - headerLen - 2);
            if (currentIndex + copyLen == length) {
                result->data[headerLen + 1] = 0x40 | naluType;//mark end
            } else {
                if (currentIndex == 0) {
                    currentIndex = startCodeLen + 1;
                    result->data[headerLen + 1] = 0x80 | naluType;//mark start
                } else {
                    result->data[headerLen + 1] = 0x00 | naluType;//mark mid
                }
            }
            //LOGD("FU-A - length=%d,currentIndex=%d,copyLen=%d,result.len=%d", length, currentIndex,copyLen,result->length);
            memcpy(result->data + headerLen + 2, h264Pkt + currentIndex, copyLen);
            //    printCharsHex(result->data, result->length, 16, "FU-A After Packed");
            callback(result);
            currentIndex += copyLen;
        }
    }

    free(h264Pkt);
    return RESULT_OK;
}

/**
 * 将SPS及PPS存储到一个全局变量中(以RTP STAP-A 的方式打包在一起)
 *
 * STAP-A类型组合封包：
 * RTP Header（12 bit） +STAP Header（1 bit） +NALU1 Size（2 bit） +NALU1+ NALU2 Size（2 bit） +NALU2....
*/
void UpdateSPS_PPS(unsigned char *spsData, int spsLen, unsigned char *ppsData, int ppsLen) {
    if (stapA == NULL) {
        stapA = (STAP_A) malloc(sizeof(struct STAP_A_SPS_PPS_Pkt));
    } else {
        free(stapA->pkt);
    }

    int startCodeLen = GetStartCodeLen(spsData);

    __uint16_t spsSize = (__uint16_t) spsLen - startCodeLen;
    __uint16_t ppsSize = (__uint16_t) ppsLen - startCodeLen;

    //5= STAP Header Len(1bit)+SPS Size Len(2bit)+PPS Size Len(2bit)
    stapA->len = spsSize + ppsSize + 5;
    stapA->pkt = (unsigned char *) calloc(stapA->len, sizeof(char));
    //STAP-A unit  Type=24:0x18
    stapA->pkt[0] = 24;
    //SPS size used 2 bit
    stapA->pkt[1] = spsSize >> 8;//前8位
    stapA->pkt[2] = spsSize & 0xFF;//后8位
    //printCharsHex(spsData,spsSize+startCodeLen,spsSize+startCodeLen,"sps");
    //copy sps
    memcpy(stapA->pkt + 3, spsData + startCodeLen, spsSize);
    //printCharsHex(stapA->pkt,stapA->len,spsSize+3,"stapA1");
    //PPS size used 2 bit
    stapA->pkt[spsSize + 3] = ppsSize >> 8;//前8位
    stapA->pkt[spsSize + 4] = ppsSize & 0xFF;//后8位
    //copy  pps
    memcpy(stapA->pkt + 5 + spsSize, ppsData + startCodeLen, ppsSize);
    // printCharsHex(stapA->pkt,stapA->len,stapA->len,"stapA");
    int i = 0;
}


void FreeResult(Result result) {
    if (result != NULL) {
        free(result->data);
        free(result);
        result = NULL;
    }
}


void clear() {
    cq = 0;
    freeSTAP();
}

void UpdateScreen(int w, int h) {
    if (!screenInfo) {
        screenInfo = malloc(sizeof(Pkt));
        screenInfo->pkt = calloc(5, sizeof(char));
        screenInfo->pkt[0]=0x24;
    }

    screenInfo->pkt[1] = w>> 8;//前8位
    screenInfo->pkt[2] = w& 0xFF;//后8位

    screenInfo->pkt[3] = h>> 8;//前8位
    screenInfo->pkt[4] = h& 0xFF;//后8位

}
