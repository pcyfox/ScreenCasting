//
// Created by Dwayne on 20/11/24.
//


#ifndef FF_PLAYER_PLAYER_INFO
#define FF_PLAYER_PLAYER_INFO

#include <media/NdkMediaCodec.h>
#include "pthread.h"
#include "Player.h"
#include "AndroidLog.hpp"
#include "StateListener.h"
#include "AsyncQueue.hpp"
#include "AVPacket.h"


class PlayerInfo {

public:
    AMediaFormat *videoFormat = NULL;
    AMediaCodec *AMediaCodec = NULL;
    ANativeWindow *window = NULL;

    unsigned int width, height;
    AsyncQueue<AVPacket> packetQueue;
    pthread_t decode_thread = 0;

    unsigned int windowWith = 0;
    unsigned int windowHeight = 0;

    pthread_mutex_t mutex;
    pthread_cond_t cond;
    const char *mine = "video/avc";

    volatile enum PlayState playState = UN_USELESS;

    void (*stateListener)(PlayState) = NULL;

    void (*decodecStateListener)(int) = NULL;


public:

    PlayerInfo();

    ~PlayerInfo();

    PlayState GetPlayState();

    void SetPlayState(PlayState s) volatile;

    void SetStateListener(void (*stateListener)(PlayState));

    void SetDeCodecState(int state) const;

    void SetDeCodecStateListener(void (*listener)(int));

};

#endif //AUDIO_PRACTICE_QUEUE_H
