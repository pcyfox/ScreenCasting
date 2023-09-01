//
// Created by LN on 2021/1/4.
//

#ifndef PLAYER_PLAYER_H
#define PLAYER_PLAYER_H

#include <android/native_window_jni.h>
#include <media/NdkMediaCodec.h>
#include "StateListener.h"
#include "PlayerInfo.h"


class Player {

private:
    bool isDebug = false;

public:
    Player();

    ~Player();

    void SetDebug(bool isDebug);

    int Configure(ANativeWindow *window, int w, int h);

    int HandleRTPPkt(char *pkt, int pktLen, unsigned int maxFrameLen, int isLiteMod);

    int Play();

    int Pause(int delay);

    int Stop();

    int Release();

    static void SetStateChangeListener(void (*listener)(PlayState));

    static void SetDecodecStateChangeListener(void (*listener)(int));


private:
    int createAMediaCodec(AMediaCodec **mMediaCodec, unsigned int width, unsigned int height,
                          uint8_t *sps,
                          int spsSize,
                          uint8_t *pps, int ppsSize,
                          ANativeWindow *window, const char *mine);

    void static *Decode(void *info);


private :
    void StartDecodeThread();

};


#endif



