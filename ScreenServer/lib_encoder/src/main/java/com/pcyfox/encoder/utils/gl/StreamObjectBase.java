/*
 * Copyright (C) 2021 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pcyfox.encoder.utils.gl;

import android.graphics.Bitmap;

/**
 * Created by pedro on 9/10/17.
 */

public abstract class StreamObjectBase {

  public abstract int getWidth();

  public abstract int getHeight();

  public abstract int updateFrame();

  public abstract void recycle();

  public abstract int getNumFrames();

  public abstract Bitmap[] getBitmaps();
}
