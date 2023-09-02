//
// Created by LN on 2021/3/1.
//

#include "include/PlayerBridge.h"

#include <jni.h>
#include <malloc.h>
#include <string.h>
#include <android/native_window_jni.h>

#include <StateListener.h>
#include <iostream>
#include "PlayerResult.h"


Player *player;
PlayerBridgeEnv playerEnv;
int changeState = -1;

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    playerEnv.vm = vm;
    return JNI_VERSION_1_6;
}

void *notifyPlayStateChange(void *s) {
    if (playerEnv.object != NULL) {
        JNIEnv *env = NULL;
        int ret = playerEnv.vm->AttachCurrentThread(&env, NULL);
        if (ret == 0 && env) {
            int iState = *(int *) s;
            env->CallVoidMethod(playerEnv.object, playerEnv.jMid_onStateChangeId, iState);
        } else {
            LOGE("onStateChange() get jEnv error");
        }
        playerEnv.vm->DetachCurrentThread();
    }
    return nullptr;
}


void notifyDecodeStateChange(int state) {
    if (playerEnv.object != nullptr) {
        JNIEnv *env = nullptr;
        int ret = playerEnv.vm->AttachCurrentThread(&env, nullptr);
        if (ret == 0 && env) {
            env->CallVoidMethod(playerEnv.object, playerEnv.jMid_onDecodeStateChangeId, state);
        } else {
            LOGE("onStateChange() get jEnv error");
        }
        playerEnv.vm->DetachCurrentThread();
    }
}


const void *onStateChange(PlayState state) {
    if (playerEnv.object != nullptr && state) {
        pthread_t thread = NULL;
        pthread_create(&thread, nullptr, notifyPlayStateChange, &state);
        pthread_setname_np(thread, "onStateChange");
    }
    return nullptr;
}


const void *onDecodeStateChange(int state) {
    notifyDecodeStateChange(state);
    return nullptr;
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_pcyfox_lib_1udp_1player_NativePlayer_init(JNIEnv *env, jobject thiz,
                                                   jboolean is_debug) {
    player = new Player();
    player->SetDebug(is_debug);
    playerEnv.object = env->NewGlobalRef(thiz);
    jclass clazz = env->GetObjectClass(thiz);
    playerEnv.clazz = clazz;
    playerEnv.env = env;
    playerEnv.jMid_onStateChangeId = env->GetMethodID(clazz, "onPlayerStateChange", "(I)V");
    playerEnv.jMid_onDecodeStateChangeId = env->GetMethodID(clazz, "onDecodeStateChange", "(I)V");
    return PLAYER_RESULT_OK;
}



extern "C"
JNIEXPORT jint JNICALL
Java_com_pcyfox_lib_1udp_1player_NativePlayer_configPlayer(JNIEnv *env, jobject thiz,
                                                           jobject surface,
                                                           jint w, jint h) {

    if (player == nullptr) {
        LOGE("configPlayer() called fail!,player is null");
        return PLAYER_RESULT_ERROR;
    }
    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        PLAYER_RESULT_ERROR;
    }
    Player::SetStateChangeListener(reinterpret_cast<void (*)(PlayState)>(onStateChange));
    Player::SetDecodecStateChangeListener(reinterpret_cast<void (*)(int)>(onDecodeStateChange));
    return player->Configure(window, w, h);
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_pcyfox_lib_1udp_1player_NativePlayer_play(JNIEnv *env, jobject thiz) {
    if (player == NULL) {
        LOGE("player not init,it is null");
        return PLAYER_RESULT_ERROR;
    }
    return player->Play();
}

int stop() {
    if (player == NULL) {
        LOGE("player not init,it is null");
        return PLAYER_RESULT_ERROR;
    }
    int ret = player->Stop();
    return ret;
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_pcyfox_lib_1udp_1player_NativePlayer_stop(JNIEnv *env, jobject thiz) {
    return stop();
}



extern "C"
JNIEXPORT jint JNICALL
Java_com_pcyfox_lib_1udp_1player_NativePlayer_pause(JNIEnv *env, jobject thiz) {

    if (player == NULL) {
        LOGE("player not init,it is null");
        return PLAYER_RESULT_ERROR;
    }
    return player->Pause(0);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_pcyfox_lib_1udp_1player_NativePlayer_handlePkt(JNIEnv *env, jobject thiz,
                                                        jbyteArray pkt, int len,
                                                        int maxFrameLen,
                                                        jboolean isLiteMode) {

    if (pkt == nullptr || len <= 0) return PLAYER_RESULT_ERROR;
    if (player == nullptr) {
        LOGE("player is not init,it is null");
        return PLAYER_RESULT_ERROR;
    }
    char *data = (char *) malloc(len);
    if (!data)return PLAYER_RESULT_ERROR;
    env->GetByteArrayRegion(pkt, 0, len, (jbyte *) data);
    return player->HandleRTPPkt((char *) data, len, maxFrameLen, isLiteMode);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pcyfox_lib_1udp_1player_NativePlayer_release(JNIEnv *env, jobject thiz) {
    if (stop() == PLAYER_RESULT_OK) {
        player->Release();
        delete player, player = NULL;
    }
    delete &playerEnv;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pcyfox_lib_1udp_1player_NativePlayer_test(JNIEnv *env, jobject thiz) {
    int str1[10];
    int str2[10];

    for (int i = 0; i < 10; i++) {
        str1[i] = -1;
    }

    for (int i = 0; i < 10; i++) {
        str2[i] = i;
    }
    memcpy(str1 + 2, str2 + 2, 3*sizeof (int));

    for (int i: str1) {
        LOGD("-------test-------- %d", i);
    }
}