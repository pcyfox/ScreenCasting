//
// Created by LN on 2021/3/11.
//


#ifndef UDP_PLAYER_SERVER_C_H264PACK_H
#define UDP_PLAYER_SERVER_C_H264PACK_H


struct PackResult {
    unsigned int h264StartCodeLen;
    unsigned int length;
    unsigned int pkt_interval;
    unsigned int curr_Sq;
    unsigned char packet_NAL_unit_type;
    unsigned char *data;
} typedef *Result;

void FreeResult(Result result);

typedef void (*Callback)(Result
                         result);

static unsigned long cq = 0;

int PackRTP(unsigned char *h264Pkt, const unsigned int length, const unsigned int maxPktLen,
            const unsigned long ts, unsigned int clock, int isLiteMod,
            Callback callback);


void UpdateSPS_PPS(unsigned char *sps, int spsLen, unsigned char *pps, int ppsLen);

Result GetSPS_PPS_RTP_STAP_Pkt(const unsigned long ts, unsigned long clock);

void PrintCurrentSq(Result result) ;

void clear();

#endif //UDP_PLAYER_SERVER_C_H264PACK_H
