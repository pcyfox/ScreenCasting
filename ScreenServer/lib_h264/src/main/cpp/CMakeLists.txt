# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html
cmake_minimum_required(VERSION 3.10.2)
set(CMAKE_BUILD_TYPE "Debug")

project("rtp_lib")

include_directories(include)
include_directories(pack/include)
include_directories(socket/include)

add_library( # Sets the name of the library.
        rtp_lib# Sets the library as a shared library.
        SHARED #动态库
        # Provides a relative path to your source file(s).
        jni/Bridge.cpp
        pack/H264Pack.c
        socket/Sender.c
        )

find_library(android-log-lib log)

target_link_libraries(rtp_lib
        android
        mediandk
        log)