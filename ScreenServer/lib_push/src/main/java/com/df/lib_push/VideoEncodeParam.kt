package com.df.lib_push

data class VideoEncodeParam(
    var w: Int = 1920,
    var h: Int = 1080,
    var fps: Int = 25,
    var dpi: Int = 480,
    // Biterate = Width * Height * FrameRate * Factor
    var bitRate: Int = (w * h * fps * 0.12).toInt(),
    var iFrameInterval: Int = 5
) {
    var q: Float = 0.06f
    var avcProfile = -1
    var avcProfileLevel = -1
}
