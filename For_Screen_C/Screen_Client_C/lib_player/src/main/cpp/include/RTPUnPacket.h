//
// Created by LN on 2021/3/1.
//

#ifndef UDP_PLAYER_RTPUNPACKET_H
#define UDP_PLAYER_RTPUNPACKET_H

#define HEAD_1  0x00
#define HEAD_2  0x00
#define HEAD_3  0x00
#define HEAD_4  0x01

#define TYPE_IDR  0x65
#define TYPE_P  0x61
#define TYPE_SPS  0x67
#define TYPE_PPS  0x68

#define START_CODE_LEN 4
#define RTP_HEAD_LEN  12


struct RTPDataInfo {
    unsigned long last_Sq;
    unsigned long curr_Sq;
    unsigned long start_time;
    unsigned long receive_count;
    unsigned int lost_count;
    float lost_rate;

} typedef *ReceiveDataInfo;

struct RtpPacketInfo {
    unsigned int length;
    unsigned int pkt_interval;
    unsigned long long curr_Sq;
    unsigned char type;
} typedef *RTPPkt;


struct TempPacket {
    unsigned char *data;
    unsigned int index;
    unsigned int len;
} typedef *TempPkt;

struct H264Packet {
    unsigned int length;
    unsigned char type;
    unsigned char *data;
} typedef *H264Pkt;


typedef void (*Callback)(H264Pkt  pkt);

int UnPacket(unsigned char *rtpPacket, const unsigned int length, const unsigned int maxFrameLen,
             unsigned int isLiteMod,
             const Callback callback);

#endif //UDP_PLAYER_RTPUNPACKET_H
