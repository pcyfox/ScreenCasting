package com.pedro.encoder.input.gl.render

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Build
import android.view.Surface
import androidx.annotation.RequiresApi
import com.pedro.encoder.input.gl.FilterAction
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender

/**
 * Created by pedro on 20/3/22.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
class MainRender {
  private val cameraRender = CameraRender()
  private val screenRender = ScreenRender()
  private var width = 0
  private var height = 0
  private var previewWidth = 0
  private var previewHeight = 0
  private var context: Context? = null
  private var filterRenders: MutableList<BaseFilterRender> = ArrayList()

  fun initGl(context: Context, encoderWidth: Int, encoderHeight: Int, previewWidth: Int, previewHeight: Int) {
    this.context = context
    width = encoderWidth
    height = encoderHeight
    this.previewWidth = previewWidth
    this.previewHeight = previewHeight
    cameraRender.initGl(width, height, context, previewWidth, previewHeight)
    screenRender.setStreamSize(encoderWidth, encoderHeight)
    screenRender.setTexId(cameraRender.texId)
    screenRender.initGl(context)
  }

  fun drawOffScreen() {
    cameraRender.draw()
    for (baseFilterRender in filterRenders) baseFilterRender.draw()
  }

  fun drawScreen(width: Int, height: Int, keepAspectRatio: Boolean, mode: Int, rotation: Int,
    flipStreamVertical: Boolean, flipStreamHorizontal: Boolean) {
    screenRender.draw(width, height, keepAspectRatio, mode, rotation, flipStreamVertical,
      flipStreamHorizontal)
  }

  fun drawScreenEncoder(width: Int, height: Int, isPortrait: Boolean, rotation: Int,
    flipStreamVertical: Boolean, flipStreamHorizontal: Boolean) {
    screenRender.drawEncoder(width, height, isPortrait, rotation, flipStreamVertical,
      flipStreamHorizontal)
  }

  fun drawScreenPreview(width: Int, height: Int, isPortrait: Boolean, keepAspectRatio: Boolean,
    mode: Int, rotation: Int, flipStreamVertical: Boolean, flipStreamHorizontal: Boolean) {
    screenRender.drawPreview(width, height, isPortrait, keepAspectRatio, mode, rotation,
      flipStreamVertical, flipStreamHorizontal)
  }

  fun release() {
    cameraRender.release()
    for (baseFilterRender in filterRenders) baseFilterRender.release()
    filterRenders.clear()
    screenRender.release()
  }

  private fun setFilter(position: Int, baseFilterRender: BaseFilterRender) {
    val id = filterRenders[position].previousTexId
    val renderHandler = filterRenders[position].renderHandler
    filterRenders[position].release()
    filterRenders[position] = baseFilterRender
    filterRenders[position].previousTexId = id
    filterRenders[position].initGl(width, height, context, previewWidth, previewHeight)
    filterRenders[position].renderHandler = renderHandler
  }

  private fun addFilter(baseFilterRender: BaseFilterRender) {
    filterRenders.add(baseFilterRender)
    baseFilterRender.initGl(width, height, context, previewWidth, previewHeight)
    baseFilterRender.initFBOLink()
    reOrderFilters()
  }

  private fun addFilter(position: Int, baseFilterRender: BaseFilterRender) {
    filterRenders.add(position, baseFilterRender)
    baseFilterRender.initGl(width, height, context, previewWidth, previewHeight)
    baseFilterRender.initFBOLink()
    reOrderFilters()
  }

  private fun clearFilters() {
    for (baseFilterRender in filterRenders) {
      baseFilterRender.release()
    }
    filterRenders.clear()
    reOrderFilters()
  }

  private fun removeFilter(position: Int) {
    filterRenders.removeAt(position).release()
    reOrderFilters()
  }

  private fun removeFilter(baseFilterRender: BaseFilterRender) {
    baseFilterRender.release()
    filterRenders.remove(baseFilterRender)
    reOrderFilters()
  }

  private fun reOrderFilters() {
    for (i in filterRenders.indices) {
      val texId = if (i == 0) cameraRender.texId else filterRenders[i - 1].texId
      filterRenders[i].previousTexId = texId
    }
    val texId = if (filterRenders.isEmpty()) {
      cameraRender.texId
    } else {
      filterRenders[filterRenders.size - 1].texId
    }
    screenRender.setTexId(texId)
  }

  fun setFilterAction(filterAction: FilterAction?, position: Int, baseFilterRender: BaseFilterRender) {
    when (filterAction) {
      FilterAction.SET -> if (filterRenders.size > 0) {
        setFilter(position, baseFilterRender)
      } else {
        addFilter(baseFilterRender)
      }
      FilterAction.SET_INDEX -> setFilter(position, baseFilterRender)
      FilterAction.ADD -> addFilter(baseFilterRender)
      FilterAction.ADD_INDEX -> addFilter(position, baseFilterRender)
      FilterAction.CLEAR -> clearFilters()
      FilterAction.REMOVE -> removeFilter(baseFilterRender)
      FilterAction.REMOVE_INDEX -> removeFilter(position)
      else -> {}
    }
  }

  fun filtersCount(): Int {
    return filterRenders.size
  }

  fun setPreviewSize(previewWidth: Int, previewHeight: Int) {
    for (i in filterRenders.indices) {
      filterRenders[i].setPreviewSize(previewWidth, previewHeight)
    }
  }

  fun enableAA(AAEnabled: Boolean) {
    screenRender.isAAEnabled = AAEnabled
  }

  fun isAAEnabled(): Boolean {
    return screenRender.isAAEnabled
  }

  fun updateFrame() {
    cameraRender.updateTexImage()
  }

  fun getSurfaceTexture(): SurfaceTexture {
    return cameraRender.surfaceTexture
  }

  fun getSurface(): Surface {
    return cameraRender.surface
  }

  fun setCameraRotation(rotation: Int) {
    cameraRender.setRotation(rotation)
  }

  fun setCameraFlip(isFlipHorizontal: Boolean, isFlipVertical: Boolean) {
    cameraRender.setFlip(isFlipHorizontal, isFlipVertical)
  }
}
