//
// Created by LN on 2021/1/8.
//

#include "include/PlayerInfo.h"

PlayerInfo::PlayerInfo() {
    pthread_mutex_init(&mutex, NULL);
    pthread_cond_init(&cond, NULL);
    playState = UN_USELESS;
    LOGD("-------PlayerInfo created---------");
}

PlayerInfo::~PlayerInfo() {
    LOGW("-------PlayerInfo Delete Start---------");
    if (window != NULL) {
        ANativeWindow_release(window);
        window = NULL;
    }
    if (AMediaCodec != NULL) {
        AMediaCodec_delete(AMediaCodec);
        AMediaCodec = NULL;
    }

    pthread_mutex_destroy(&mutex);
    pthread_cond_destroy(&cond);
    decode_thread = 0;
    LOGW("-------PlayerInfo Delete Over---------");
}

void PlayerInfo::SetPlayState(PlayState s) volatile {
    playState = s;
    if (stateListener) {
        stateListener(playState);
    }
    LOGD("PlayerInfo SetPlayState() :%d", s);
}

PlayState PlayerInfo::GetPlayState() {
    return playState;
}

void PlayerInfo::SetStateListener(void (*listener)(PlayState)) {
    this->stateListener = listener;
}

void PlayerInfo::SetDeCodecStateListener(void (*listener)(int)) {
    this->decodecStateListener = listener;
}

void PlayerInfo::SetDeCodecState(int state) const {
    if (decodecStateListener != nullptr)decodecStateListener(state);
}


