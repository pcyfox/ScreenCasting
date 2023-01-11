//
// Created by LN on 2021/3/30.
//

#ifndef UDP_PLAYER_C_PLAYERBRIDGEENV_H
#define UDP_PLAYER_C_PLAYERBRIDGEENV_H

#include<jni.h>

class PlayerBridgeEnv {
public:
    JavaVM *vm{};
    jclass clazz = NULL;
    JNIEnv *env{};
    jobject object = NULL;
    jmethodID jMid_onStateChangeId = NULL;
};


#endif //UDP_PLAYER_C_PLAYERBRIDGEENV_H
