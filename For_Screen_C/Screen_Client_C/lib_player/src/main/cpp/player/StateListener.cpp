//
// Created by LN on 2021/1/8.
//

#include "include/StateListener.h"
#include "AndroidLog.hpp"


void StateListener::onStateChange(PlayState state) {
    LOGI("onStateChange=%d", state);
}
