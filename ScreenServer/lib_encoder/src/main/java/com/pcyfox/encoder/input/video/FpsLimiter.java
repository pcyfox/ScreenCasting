package com.pcyfox.encoder.input.video;

import android.util.Log;

/**
 * Created by pedro on 11/10/18.
 */

public class FpsLimiter {
  private static final String TAG = "FpsLimiter";
  private long startTS = System.currentTimeMillis();
  private long ratioF = 1000 / 30;
  private long ratio = 1000 / 30;

  public void setFPS(int fps) {
    Log.d(TAG, "setFPS() called with: fps = [" + fps + "]");
    startTS = System.currentTimeMillis();
    ratioF = 1000 / fps;
    ratio = 1000 / fps;
  }

  public boolean limitFPS() {
    long lastFrameTimestamp = System.currentTimeMillis() - startTS;
    if (ratio < lastFrameTimestamp) {
      ratio += ratioF;
      return false;
    }
    return true;
  }
}
