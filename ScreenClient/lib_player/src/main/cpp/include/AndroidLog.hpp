#ifndef  ANDROID_LOG_H
#define  ANDROID_LOG_H

#define TAG "screen-client"

#include <android/log.h>

#define IS_DEBUG  0

#define LOGV(...) if(IS_DEBUG)__android_log_print(ANDROID_LOG_VERBOSE, TAG, __VA_ARGS__)
#define LOGD(...) if(IS_DEBUG) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

#define LOGVX(tag, ...) if(IS_DEBUG)__android_log_print(ANDROID_LOG_VERBOSE, tag, __VA_ARGS__)
#define LOGDX(tag, ...) if(IS_DEBUG)__android_log_print(ANDROID_LOG_DEBUG, tag, __VA_ARGS__)
#define LOGWX(tag, ...) __android_log_print(ANDROID_LOG_WARN, tag, __VA_ARGS__)
#define LOGEX(tag, ...) __android_log_print(ANDROID_LOG_ERROR, tag, __VA_ARGS__)
#define LOGIX(tag, ...) __android_log_print(ANDROID_LOG_INFO, tag, __VA_ARGS__)


static void printCharsHex(char *data, int length, int printLen, char *tag) {
    if (!IS_DEBUG)return;
    LOGD("-------------%s-length=%d,printLen=%d------------->",
         tag,
         length, printLen);
    if (printLen > length) {
        return;
    }
    for (int i = 0; i < printLen; ++i) {
        LOGD("----------printChars() %s:i=%d,char=%02x", tag, i, *(data + i));
    }
}


#endif