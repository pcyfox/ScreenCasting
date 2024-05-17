#include "Player.h"
#include "AndroidLog.hpp"
#include "include/Utils.h"
#include <android/native_window_jni.h>
#include <android/native_window.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include<queue>
#include <pthread.h>

#ifdef __cplusplus
extern "C" {
#include "../include/RTPUnPacket.h"
#endif
#ifdef __cplusplus
}
#endif

static PlayerInfo *playerInfo;
static long timeoutUs = 0;

Player::Player() {
    playerInfo = new PlayerInfo;
    playerInfo->SetPlayState(INITIALIZED);
    LOGD("init player info over");
}

int GetNALUType(AVPacket *packet) {
    int nalu_type = -1;
    const char *buf = packet->data;
    bool hasLongStartCode = buf[0] == 0 && buf[1] == 0 && buf[2] == 0 && buf[3] == 1;
    bool hasShortStartCode = buf[0] == 0 && buf[1] == 0 && buf[2] == 1;
    if (hasLongStartCode || hasShortStartCode) {
        if (hasShortStartCode) {
            nalu_type = buf[3] & 0xFF;
        } else {
            nalu_type = buf[4] & 0xFF;
        }
    }
    return nalu_type;
}


int
Player::createAMediaCodec(AMediaCodec **mMediaCodec,
                          unsigned int width,
                          unsigned int height,
                          uint8_t *sps,
                          int spsSize,
                          uint8_t *pps,
                          int ppsSize,
                          ANativeWindow *window,
                          const char *mine) {

    LOGI("createAMediaCodec() called width=%d,height=%d,spsSize=%d,ppsSize=%d,mine=%s\n", width,
         height,
         spsSize, ppsSize, mine);

    if (width * height <= 0) {
        LOGE("createAMediaCodec() not support video size");
        return PLAYER_RESULT_ERROR;
    }

    if (*mMediaCodec == nullptr) {
        AMediaCodec *mediaCodec = AMediaCodec_createDecoderByType(mine);
        if (!mediaCodec) {
            LOGE("createAMediaCodec() createDecoder fail!");
            return PLAYER_RESULT_ERROR;
        } else {
            LOGI("createAMediaCodec() createDecoder success!");
            *mMediaCodec = mediaCodec;
        }
    } else {
        AMediaCodec_flush(*mMediaCodec);
        AMediaCodec_stop(*mMediaCodec);
    }

    AMediaFormat *videoFormat = AMediaFormat_new();
    AMediaFormat_setString(videoFormat, "mime", mine);
    AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_WIDTH, width); // 视频宽度
    AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_HEIGHT, height); // 视频高度

    if (spsSize > 0) {
        AMediaFormat_setBuffer(videoFormat, "csd-0", sps, spsSize); // sps
    }
    if (ppsSize > 0) {
        AMediaFormat_setBuffer(videoFormat, "csd-1", pps, ppsSize); // pps
    }
    media_status_t status = AMediaCodec_configure(*mMediaCodec, videoFormat, window, NULL, 0);
    if (status != AMEDIA_OK) {
        LOGE("configure AMediaCodec fail!,ret=%d", status);
        AMediaCodec_delete(*mMediaCodec);
        mMediaCodec = nullptr;
        return PLAYER_RESULT_ERROR;
    } else {
        if (!playerInfo)return PLAYER_RESULT_ERROR;
        playerInfo->videoFormat = videoFormat;
        playerInfo->window = window;
        LOGD("createAMediaCodec() set AMediaCodec  configure success!");
    }
    return PLAYER_RESULT_OK;
}


int updateCodec(uint8_t *sps,
                int spsSize,
                uint8_t *pps,
                int ppsSize) {

    // AMediaCodec_flush(playerInfo->AMediaCodec);
    //AMediaCodec_stop(playerInfo->AMediaCodec);
    if (spsSize > 0) {
        AMediaFormat_setBuffer(playerInfo->videoFormat, "csd-0", sps, spsSize); // sps
    }
    if (ppsSize > 0) {
        AMediaFormat_setBuffer(playerInfo->videoFormat, "csd-1", pps, ppsSize); // pps
    }

    media_status_t status = AMediaCodec_configure(playerInfo->AMediaCodec, playerInfo->videoFormat,
                                                  playerInfo->window, nullptr, 0);
    if (status == AMEDIA_OK) {
        //   status = AMediaCodec_start(playerInfo->AMediaCodec);
    } else {
        LOGE("called updateCodec() error!");
    }
    return status;
}

void *Player::Decode(void *) {
    if (!playerInfo)return nullptr;
    AMediaCodec *codec = playerInfo->AMediaCodec;
    size_t out_size = 0;
    ssize_t outIndex = 0;
    AVPacket *packet = nullptr;
    while (playerInfo && playerInfo->GetPlayState() == STARTED) {
        playerInfo->packetQueue.get(&packet);
        if (packet == nullptr || packet->data == nullptr)continue;
        // 获取buffer的索引
        ssize_t index = AMediaCodec_dequeueInputBuffer(codec, timeoutUs);
        if (index >= 0) {
            int length = packet->size;
            uint8_t *inputBuf = AMediaCodec_getInputBuffer(codec, index, &out_size);
            if (inputBuf != nullptr && length <= out_size) {
                // 将待解码的数据copy到解码器（DSP）缓冲区中
                memcpy(inputBuf, packet->data, length);
                int64_t pts = packet->pts;
                if (pts < 0 || !pts) {
                    pts = getCurrentTime();
                }
                media_status_t status = AMediaCodec_queueInputBuffer(codec, index, 0, length, pts,
                                                                     0);
                if (status != AMEDIA_OK) {
                    LOGE("Decode queue input buffer error status=%d", status);
                }
            }
        } else {
            LOGE("Decode dequeue buffer error ");
            continue;
        }

        delete packet, packet = nullptr;

        do {
            auto *bufferInfo = (AMediaCodecBufferInfo *) malloc(sizeof(AMediaCodecBufferInfo));
            if (!bufferInfo) {
                return nullptr;
            }
            outIndex = AMediaCodec_dequeueOutputBuffer(codec, bufferInfo, timeoutUs);
            if (!playerInfo)return nullptr;
            if (outIndex >= 0) {
                AMediaCodec_releaseOutputBuffer(codec, outIndex, bufferInfo->size != 0);
                if (bufferInfo->flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
                    playerInfo->SetDeCodecState(AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
                    LOGE("Decode() video producer output EOS");
                    break;
                } else {
                    continue;
                }
            } else if (outIndex == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
                LOGW("Decode() video output buffers changed");
                playerInfo->SetDeCodecState(outIndex);
            } else if (outIndex == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
                LOGW("Decode() video output format changed");
                playerInfo->SetDeCodecState(outIndex);
            } else if (outIndex == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
                //LOGD("Decode() video no output buffer right now");
            } else {
                LOGE("Decode() unexpected info code: %zd", outIndex);
                playerInfo->SetDeCodecState(outIndex);
            }
            free(bufferInfo);
        } while (outIndex > 0);
    }
    LOGD("-------Decode over!---------");
    return nullptr;
}


void Player::StartDecodeThread() {
    LOGI("Start decode thread");
    if (!playerInfo)return;
    void *param = playerInfo;
    pthread_create(&playerInfo->decode_thread, NULL, Decode, param);
    pthread_setname_np(playerInfo->decode_thread, "decode_thread");
    pthread_detach(playerInfo->decode_thread);
}


void Player::SetDebug(bool debug) {
    LOGD("SetDebug() called with %d", debug);
    isDebug = debug;
}


int Player::Configure(ANativeWindow *window, int w, int h) {
    LOGD("Configure() called with: w=%d,h=%d", w, h);
    if (!playerInfo)return PLAYER_RESULT_ERROR;
    if (playerInfo->GetPlayState() != ERROR) {
        playerInfo->window = window;
        playerInfo->windowWith = w;
        playerInfo->windowHeight = h;
        int ret = createAMediaCodec(&playerInfo->AMediaCodec, playerInfo->windowWith,
                                    playerInfo->windowHeight,
                                    NULL,
                                    NULL,
                                    NULL,
                                    NULL,
                                    playerInfo->window, playerInfo->mine);

        if (ret == PLAYER_RESULT_ERROR) {
            return ret;
        } else {
            playerInfo->SetPlayState(PREPARED);
        }
    } else {
        LOGE("Configure() fail!,can't configure due to init player ERROR\n");
        return PLAYER_RESULT_ERROR;
    }
    return PLAYER_RESULT_OK;
}

void Player::SetStateChangeListener(void (*listener)(PlayState)) {
    if (!playerInfo)return;
    playerInfo->SetStateListener(listener);
}

void Player::SetDecodecStateChangeListener(void (*listener)(int)) {
    if (!playerInfo)return;
    playerInfo->decodecStateListener = listener;
}


int Player::Play() {
    LOGI("--------Play()  called-------");
    if (!playerInfo)return PLAYER_RESULT_ERROR;
    if (playerInfo->GetPlayState() == PAUSE) {
        playerInfo->SetPlayState(STARTED);
        return PLAYER_RESULT_OK;
    }
    if (playerInfo->GetPlayState() != PREPARED) {
        LOGE("play() fail,player is not PREPARED!\n");
        return PLAYER_RESULT_ERROR;
    }

    media_status_t status = AMediaCodec_start(playerInfo->AMediaCodec);
    if (status != AMEDIA_OK) {
        LOGE("play() error!,start AMediaCodec fail!\n");
        AMediaCodec_delete(playerInfo->AMediaCodec);
        playerInfo->AMediaCodec = NULL;
        return PLAYER_RESULT_ERROR;
    } else {
        LOGI("play(),AMediaCodec start success!!\n");
    }
    playerInfo->SetPlayState(STARTED);
    StartDecodeThread();
    return PLAYER_RESULT_OK;
}


int Player::Pause(int delay) {
    LOGI("--------Pause()  called-------");
    if (!playerInfo)return PLAYER_RESULT_ERROR;
    if (playerInfo->GetPlayState() != STARTED) {
        LOGE("--------Pause()  called-,fail player not started------");
        return PLAYER_RESULT_ERROR;
    }
    playerInfo->SetPlayState(PAUSE);
    return PLAYER_RESULT_OK;
}


int Player::Stop() {
    LOGI("--------Stop()  called-------");
    if (playerInfo == nullptr) {
        return PLAYER_RESULT_ERROR;
    }
    PlayState state = playerInfo->GetPlayState();
    if (state == STOPPED)return PLAYER_RESULT_OK;
    if (state != STARTED && state != PAUSE) {
        LOGW("Stop(),fail ,playerInfo is not started");
        return PLAYER_RESULT_ERROR;
    }
    playerInfo->SetPlayState(STOPPED);
    AMediaCodec_stop(playerInfo->AMediaCodec);

    delete playerInfo, playerInfo = nullptr;
    LOGI("--------Stop()  finish-------");
    return PLAYER_RESULT_OK;
}

int Player::Release() {
    LOGI("--------Stop()  called-------");
    Stop();
    Clear();
    LOGI("--------Stop()  finish-------");
    return 0;
}


void unpackCallback(H264Pkt result) {
    if (!playerInfo)return;
    auto *avPacket = new AVPacket();
    avPacket->data = result->data;
    avPacket->size = result->length;
    playerInfo->packetQueue.put(avPacket);
    free(result), result = nullptr;
}


int Player::HandleRTPPkt(char *pkt, int len, unsigned int maxFrameLen,
                         int isLiteMod) {
    if (!playerInfo) {
        LOGE("HandleRTPPkt() fail,playerInfo = null");
        return PLAYER_RESULT_ERROR;
    }

    PlayState state = playerInfo->GetPlayState();
    if (state != STARTED) {
        LOGE("HandleRTPPkt() fail,player not started!");
        return PLAYER_RESULT_ERROR;
    }
    return UnPacket(pkt, len, maxFrameLen, isLiteMod, unpackCallback);
}


Player::~Player() = default;


