//
// Created by LN on 2021/3/25.
//


#include "include/Sender.h"
#include "Android_log.h"
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/un.h>
#include <netinet/in.h>
#include <stddef.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <stdlib.h>

int socketClient = -1;
struct sockaddr_in *toAddress;

int sendData(unsigned char *data, unsigned int len) {
    if (socketClient < 0) return socketClient;

    if (sendto(socketClient, data, len, 0, (struct sockaddr *) toAddress,
               sizeof(struct sockaddr_in)) != len) {
        //closeSocket();
        return 0;
    }
    return 1;
}

void initSocket(char *ip, unsigned int port, int type) {
    if (socketClient >= 0) {
        closeSocket();
    }
    int protocol = type == UDP ? IPPROTO_UDP : IPPROTO_TCP;
    socketClient = socket(AF_INET, SOCK_DGRAM, protocol);
    toAddress = malloc(sizeof(struct sockaddr_in));
    if (!toAddress)return;
    toAddress->sin_family = AF_INET;
    toAddress->sin_addr.s_addr = inet_addr(ip);
    toAddress->sin_port = htons(port);
}

void closeSocket() {
    LOGI("Sender,closeSocket() called");
    if (socketClient < 0) {
        return;
    }
    close(socketClient);
    socketClient = -1;
    free(toAddress);
    toAddress = NULL;
}



