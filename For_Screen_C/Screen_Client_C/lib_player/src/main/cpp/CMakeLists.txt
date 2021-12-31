# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html
cmake_minimum_required(VERSION 3.10.2)
set(CMAKE_BUILD_TYPE "Debug")

project("udp_player")

include_directories(include)
include_directories(player/include)
include_directories(jni/include)
include_directories(jb/include)

add_library( # Sets the name of the library.
        udp_player# Sets the library as a shared library.
        SHARED #动态库
        # Provides a relative path to your source file(s).
        jni/PlayerBridge.cpp
        jni/PlayerBridgeEnv.cpp
        jb/JitterBuffer.cpp
        player/Player.cpp
        player/AVPacket.cpp
        player/StateListener.cpp
        player/PlayerInfo.cpp
        player/RTPUnpack.c
        )

find_library(android-log-lib log)

target_link_libraries(udp_player
        android
        mediandk
        log)