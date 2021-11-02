//
// Created by LN on 2021/3/1.
//

#include "include/PlayerBridge.h"

#include <jni.h>
#include <malloc.h>
#include <string.h>
#include <android/native_window_jni.h>

#include <StateListener.h>
#include "PlayerResult.h"


Player *player;
PlayerBridgeEnv playerEnv;
int changeState = -1;

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    playerEnv.vm = vm;
    return JNI_VERSION_1_6;
}

void *ChangeState(void *s) {
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
    return NULL;
}


const void *onStateChange(PlayState state) {
    if (playerEnv.object != NULL) {
        pthread_t thread = NULL;
        changeState = state;
        pthread_create(&thread, NULL, ChangeState, &changeState);
        pthread_detach(pthread_self());
    }
    return NULL;
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_taike_lib_1udp_1player_NativePlayer_init(JNIEnv *env, jobject thiz,
                                                  jboolean is_debug) {
    player = new Player();
    player->SetDebug(is_debug);
    playerEnv.object = env->NewGlobalRef(thiz);
    jclass clazz = env->GetObjectClass(thiz);
    playerEnv.clazz = clazz;
    playerEnv.env = env;
    playerEnv.jMid_onStateChangeId = env->GetMethodID(clazz, "onPlayerStateChange", "(I)V");
    return PLAYER_RESULT_OK;
}



extern "C"
JNIEXPORT jint JNICALL
Java_com_taike_lib_1udp_1player_NativePlayer_configPlayer(JNIEnv *env, jobject thiz,
                                                          jobject surface,
                                                          jint w, jint h) {

    if (player == NULL) {
        LOGE("player not init,it is null");
        return PLAYER_RESULT_ERROR;
    }
    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        PLAYER_RESULT_ERROR;
    }
    player->SetStateChangeListener(reinterpret_cast<void (*)(PlayState)>(onStateChange));
    return player->Configure(window, w, h);
}




extern "C"
JNIEXPORT jint JNICALL
Java_com_taike_lib_1udp_1player_NativePlayer_changeSurface(JNIEnv *env, jobject thiz,
                                                           jobject surface, jint w,
                                                           jint h) {
    if (player == NULL) {
        LOGE("player not init,it is null");
        return PLAYER_RESULT_ERROR;
    }

    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        PLAYER_RESULT_ERROR;
    }
    return player->ChangeWindow(window, w, h);
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_taike_lib_1udp_1player_NativePlayer_play(JNIEnv *env, jobject thiz) {
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
    if (ret == PLAYER_RESULT_OK) {
        delete player;
        player = NULL;
    }
    return ret;
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_taike_lib_1udp_1player_NativePlayer_stop(JNIEnv *env, jobject thiz) {
    return stop();
}



extern "C"
JNIEXPORT jint JNICALL
Java_com_taike_lib_1udp_1player_NativePlayer_pause(JNIEnv *env, jobject thiz) {

    if (player == NULL) {
        LOGE("player not init,it is null");
        return PLAYER_RESULT_ERROR;
    }
    return player->Pause(0);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_taike_lib_1udp_1player_NativePlayer_handlePkt(JNIEnv *env, jobject thiz,
                                                       jbyteArray pkt, int len,
                                                       int maxFrameLen,
                                                       jboolean isLiteMode) {

    if (player == NULL) {
        LOGE("player not init,it is null");
        return PLAYER_RESULT_ERROR;
    }
    jbyte *data = (env->GetByteArrayElements(pkt, JNI_FALSE));
    auto *dataCopy = (unsigned char *) calloc(len, sizeof(char));
    memcpy(dataCopy, data, len);
    env->ReleaseByteArrayElements(pkt, data, JNI_FALSE);
    return player->HandleRTPPkt(dataCopy, len, maxFrameLen, isLiteMode);
}extern "C"

JNIEXPORT void JNICALL
Java_com_taike_lib_1udp_1player_NativePlayer_release(JNIEnv *env, jobject thiz) {
    stop();
    delete &playerEnv;
}