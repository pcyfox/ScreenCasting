//
// Created by LN on 2021/3/1.
//

#ifndef UDP_PLAYER_RTPUNPACKET_H
#define UDP_PLAYER_RTPUNPACKET_H


struct RtpPacketInfo {
    unsigned int length;
    unsigned int pkt_interval;
    unsigned long long curr_Sq;
    unsigned char type;
} typedef *RTPPkt;



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
