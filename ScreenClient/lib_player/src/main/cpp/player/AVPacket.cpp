//
// Created by LN on 2021/3/9.
//

#include "include/AVPacket.h"
#include "malloc.h"

AVPacket::~AVPacket() {
    if (data != NULL) {
        free(data);
        data = NULL;
    }
}
