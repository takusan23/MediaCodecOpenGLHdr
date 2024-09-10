package io.github.takusan23.mediacodecopenglhdr.akaricore

import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

class AkariSurfaceTexture(private val initTexName: Int) {

    private val surfaceTexture = SurfaceTexture(initTexName)
    private val _isAvailableFrameFlow = MutableStateFlow(false)

    /** SurfaceTexture から新しいフレームが来ているかの Flow */
    val isAvailableFrameFlow = _isAvailableFrameFlow.asStateFlow()

    /** [SurfaceTexture]へ映像を渡す[Surface] */
    val surface = Surface(surfaceTexture)

    init {
        surfaceTexture.setOnFrameAvailableListener {
            // StateFlow はスレッドセーフが約束されているので
            _isAvailableFrameFlow.value = true
        }
    }

    /**
     * [SurfaceTexture.setDefaultBufferSize] を呼び出す
     * Camera2 API の解像度、SurfaceTexture の場合はここで決定する
     */
    fun setTextureSize(width: Int, height: Int) {
        surfaceTexture.setDefaultBufferSize(width, height)
    }

    /**
     * GL コンテキストを切り替え、テクスチャ ID の変更を行う。
     * GL スレッドから呼び出すこと。
     *
     * @param texName テクスチャ
     */
    fun attachGl(texName: Int) {
        surfaceTexture.detachFromGLContext()
        surfaceTexture.attachToGLContext(texName)
    }

    /** 新しいフレームが来るまで待って、[SurfaceTexture.updateTexImage]を呼び出す */
    suspend fun awaitUpdateTexImage() {
        // フラグが来たら折る
        _isAvailableFrameFlow.first { it /* == true */ }
        _isAvailableFrameFlow.value = false
        surfaceTexture.updateTexImage()
    }

    /** テクスチャが更新されていれば、[SurfaceTexture.updateTexImage]を呼び出す */
    fun checkAndUpdateTexImage() {
        val isAvailable = _isAvailableFrameFlow.value
        if (isAvailable) {
            _isAvailableFrameFlow.value = false
            surfaceTexture.updateTexImage()
        }
    }

    /** [SurfaceTexture.setDefaultBufferSize]を呼ぶ */
    fun setDefaultBufferSize(width: Int, height: Int) {
        surfaceTexture.setDefaultBufferSize(width, height)
    }

    /** [SurfaceTexture.getTransformMatrix]を呼ぶ */
    fun getTransformMatrix(mtx: FloatArray) {
        surfaceTexture.getTransformMatrix(mtx)
    }

    /**
     * 破棄する
     * GL スレッドから呼び出すこと（テクスチャを破棄したい）
     */
    fun destroy() {
        val textures = intArrayOf(initTexName)
        GLES20.glDeleteTextures(1, textures, 0)
        surface.release()
        surfaceTexture.release()
    }
}