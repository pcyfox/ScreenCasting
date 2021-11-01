//
// Created by Dwayne on 20/11/24.
//


#ifndef AUDIO_PRACTICE_QUEUE_H
#define AUDIO_PRACTICE_QUEUE_H

#ifndef _COMMON
#define _COMMON

#include "queue"
#include "pthread.h"
#include "Player.h"
#include "PlayerResult.h"
#include "AVPacket.h"
#include <../include/Android_log.h>

#ifdef __cplusplus
extern "C" {
#endif
#ifdef __cplusplus
}

#endif
#endif //AUDIO_PRACTICE_QUEUE_Hyy

template<class T>

class AsyncQueue {
public:
    std::queue<T *> queuePacket;
    pthread_mutex_t mutexPacket;
    pthread_cond_t condPacket;
    int maxSize = 128;

public:
    AsyncQueue();

    AsyncQueue(int maxSize);

    ~AsyncQueue();

    int put(T *packet);

    int get(T **packet);

    int getQueueSize();

    void clear();

    void clearNow();

    void noticeQueue();


};

template<class _TYPE>
AsyncQueue<_TYPE>::AsyncQueue() {
    pthread_mutex_init(&mutexPacket, NULL);
    pthread_cond_init(&condPacket, NULL);
}

template<class _TYPE>
AsyncQueue<_TYPE>::~AsyncQueue() {
    clear();
    pthread_mutex_destroy(&mutexPacket);
    pthread_cond_destroy(&condPacket);
    LOGW("AsyncQueue Deleted");
}


template<class _TYPE>
int AsyncQueue<_TYPE>::getQueueSize() {
    int size = 0;
    pthread_mutex_lock(&mutexPacket);
    size = queuePacket.size();
    pthread_mutex_unlock(&mutexPacket);
    return size;
}

template<class _TYPE>
void AsyncQueue<_TYPE>::clear() {
    pthread_mutex_lock(&mutexPacket);
    clearNow();
    pthread_mutex_unlock(&mutexPacket);
}

template<class _TYPE>
void AsyncQueue<_TYPE>::clearNow() {
    while (!queuePacket.empty()) {
        AVPacket *element = queuePacket.front();
        queuePacket.pop();
        delete element;
        element = NULL;
    }
    LOGD("AsyncQueue clear over!");
}

template<class _TYPE>
void AsyncQueue<_TYPE>::noticeQueue() {
    pthread_cond_signal(&condPacket);
}

template<class _TYPE>
int AsyncQueue<_TYPE>::put(_TYPE *element) {
    pthread_mutex_lock(&mutexPacket);
    if (queuePacket.size() >= maxSize) {
        LOGW("queue size is too large,start to clean!");
        clearNow();
    }
    queuePacket.push(element);
    pthread_cond_signal(&condPacket);
    pthread_mutex_unlock(&mutexPacket);
    return PLAYER_RESULT_OK;
}

template<class _TYPE>
int AsyncQueue<_TYPE>::get(_TYPE **element) {
    pthread_mutex_lock(&mutexPacket);
    if (queuePacket.size() > 0) {
        *element = queuePacket.front();
        queuePacket.pop();
        pthread_mutex_unlock(&mutexPacket);
        return PLAYER_RESULT_OK;
    } else {
        *element = NULL;
        pthread_mutex_unlock(&mutexPacket);
        return PLAYER_RESULT_ERROR;
    }
}

template<class T>
AsyncQueue<T>::AsyncQueue(int maxSize) {
    this->maxSize = maxSize;
}


#endif