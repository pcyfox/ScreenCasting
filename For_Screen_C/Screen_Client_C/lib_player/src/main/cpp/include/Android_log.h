#define TAG "screen-client"

#include <android/log.h>

#define IS_DEBUG  1

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

