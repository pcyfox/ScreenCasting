//
// Created by LN on 2021/1/8.
//

#ifndef TCTS_EDU_APP_RECODER_UTILS_H
#define TCTS_EDU_APP_RECODER_UTILS_H

#include <sys/time.h>

static int64_t getCurrentTime()
{
    struct timeval tv;
    gettimeofday(&tv, NULL); //该函数在sys/time.h头文件中
    return tv.tv_sec * 1000 + tv.tv_usec / 1000;
}
//向前32位写入数据
#define AV_WB32(p, val) do {                 \
        uint32_t d = (val);                     \
        ((uint8_t*)(p))[3] = (d);               \
        ((uint8_t*)(p))[2] = (d)>>8;            \
        ((uint8_t*)(p))[1] = (d)>>16;           \
        ((uint8_t*)(p))[0] = (d)>>24;           \
    } while(0)

//读取前两个字节
#define AV_RB16(x)                           \
    ((((const uint8_t*)(x))[0] << 8) |          \
      ((const uint8_t*)(x))[1])




#endif //TCTS_EDU_APP_RECODER_UTILS_H
