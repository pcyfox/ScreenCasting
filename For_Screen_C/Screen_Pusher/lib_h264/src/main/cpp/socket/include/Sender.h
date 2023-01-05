//
// Created by LN on 2021/3/25.
//

#ifndef UDP_PLAYER_SERVER_C_SENDER_H
#define UDP_PLAYER_SERVER_C_SENDER_H
enum SocketType {
    TCP, UDP
};


void initSocket(char *ip, unsigned int port, int type);
void closeSocket();

int sendData(unsigned char *data, unsigned int len);


#endif //UDP_PLAYER_SERVER_C_SENDER_H
