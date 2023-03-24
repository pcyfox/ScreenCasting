//
// Created by LN on 2021/3/9.
//

#ifndef UDP_PLAYER_AVPACKET_H
#define UDP_PLAYER_AVPACKET_H


class AVPacket {
public:
    char *data;
    unsigned int size;
    int pts = -1;

public:
    ~AVPacket();
};


#endif //UDP_PLAYER_AVPACKET_H
