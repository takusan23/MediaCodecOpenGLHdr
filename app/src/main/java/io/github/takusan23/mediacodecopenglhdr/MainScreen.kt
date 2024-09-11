package io.github.takusan23.mediacodecopenglhdr

import android.media.MediaRecorder
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.view.Surface
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.graphics.opengl.GLRenderer
import androidx.graphics.opengl.egl.EGLConfigAttributes
import androidx.graphics.opengl.egl.EGLManager
import androidx.graphics.opengl.egl.EGLSpec
import io.github.takusan23.mediacodecopenglhdr.akaricore.AkariVideoFrameTexture
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private const val MEDIA_RECORDER_WIDTH = 1920
private const val MEDIA_RECORDER_HEIGHT = 1080

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val videoUri = remember { mutableStateOf<Uri?>(null) }
    val surfaceView = remember { SurfaceView(context) }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { videoUri.value = it }
    )

    fun start() {
        val nonnullVideoUri = videoUri.value ?: return
        val mSTMatrix = FloatArray(16)

        var akariVideoFrameTexture: AkariVideoFrameTexture? = null
        var uniformLocation: GLUtil.UniformLocation? = null
        var previewSurfaceGlRenderer: GLRenderer.RenderTarget? = null

        val mediaRecorder = MediaRecorder(context).apply {
            // 呼び出し順が存在します
            // 音声トラックは録画終了時にやります
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.HEVC)
            setVideoEncodingBitRate(10_000_000)
            setVideoFrameRate(30)
            setVideoSize(MEDIA_RECORDER_WIDTH, MEDIA_RECORDER_HEIGHT)
            val videoRecordingFile = context.getExternalFilesDir(null)?.resolve("10bit_hdr_video_opengl_mediarecorder_${System.currentTimeMillis()}.mp4")
            setOutputFile(videoRecordingFile!!.path)
            prepare()
        }

        val glRenderer = GLRenderer(
            eglSpecFactory = { EGLSpec.V14ES3 },
            eglConfigFactory = {
                loadConfig(
                    EGLConfigAttributes {
                        include(EGLConfigAttributes.RGBA_1010102)
                        EGL14.EGL_RENDERABLE_TYPE to EGLExt.EGL_OPENGL_ES3_BIT_KHR
                        EGL14.EGL_SURFACE_TYPE to (EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT)
                        // EGLExt.EGL_RECORDABLE_ANDROID to 1 // MediaCodec に必要→いらんかも
                    }
                )!!
            }
        )

        glRenderer.registerEGLContextCallback(object : GLRenderer.EGLContextCallback {
            override fun onEGLContextCreated(eglManager: EGLManager) {
                // シェーダーのコンパイルを行う
                val createProgram = GLUtil.createProgram(
                    vertShader = GLUtil.TEN_BIT_VERTEX_SHADER,
                    fragShader = GLUtil.TEN_BIT_FRAGMENT_SHADER
                )
                // Uniform 変数へのハンドルを取得
                uniformLocation = GLUtil.loadLocations(programHandle = createProgram.program)
                // SurfaceTexture
                val externalTextureId = GLUtil.createTexture()
                // Uniform 変数にセットしていく
                GLUtil.useAndConfigureProgram(
                    createProgramResult = createProgram,
                    uniformLocation = uniformLocation!!,
                    externalTextureId = externalTextureId
                )

                // プレイヤーを準備して再生
                akariVideoFrameTexture = AkariVideoFrameTexture(externalTextureId)
                akariVideoFrameTexture?.prepareDecoder(context, nonnullVideoUri)
                akariVideoFrameTexture?.play()
                // フレーム更新で再描画
                akariVideoFrameTexture?.akariSurfaceTexture?.isAvailableFrameFlow
                    ?.onEach { previewSurfaceGlRenderer?.requestRender() }
                    ?.launchIn(scope)
            }

            override fun onEGLContextDestroyed(eglManager: EGLManager) {
                // do nothing
            }
        })

        glRenderer.start()
        mediaRecorder.start()

        // 3 秒後に終える
        scope.launch {
            delay(5_000)
            mediaRecorder.stop()
            mediaRecorder.release()
            scope.cancel()
            println("Complete!!!")
        }

        previewSurfaceGlRenderer = glRenderer.attach(mediaRecorder.surface, MEDIA_RECORDER_WIDTH, MEDIA_RECORDER_HEIGHT, object : GLRenderer.RenderCallback {
            override fun onDrawFrame(eglManager: EGLManager) {
                val akariSurfaceTexture = akariVideoFrameTexture?.akariSurfaceTexture
                if (akariSurfaceTexture != null) {
                    // テクスチャ更新
                    akariSurfaceTexture.checkAndUpdateTexImage()
                    akariSurfaceTexture.getTransformMatrix(mSTMatrix)
                    // OpenGL 描画
                    GLUtil.drawFrame(
                        outputWidth = MEDIA_RECORDER_WIDTH,
                        outputHeight = MEDIA_RECORDER_HEIGHT,
                        texMatrixLoc = uniformLocation!!.texMatrixLoc,
                        surfaceTransform = mSTMatrix
                    )
                }
            }

            override fun onSurfaceCreated(spec: EGLSpec, config: EGLConfig, surface: Surface, width: Int, height: Int): EGLSurface? {
                return spec.eglCreateWindowSurface(
                    config = config,
                    surface = surface,
                    // これで OpenGLES + SurfaceView で画面が HDR 表示になる
                    configAttributes = EGLConfigAttributes {
                        if (/*use10bitPipeline*/ true) {
                            GLUtil.EGL_GL_COLORSPACE_KHR to GLUtil.EGL_GL_COLORSPACE_BT2020_HLG_EXT
                        }
                    }
                )
            }
        })

//        previewSurfaceGlRenderer = glRenderer.attach(surfaceView, object : GLRenderer.RenderCallback {
//            override fun onDrawFrame(eglManager: EGLManager) {
//                val akariSurfaceTexture = akariVideoFrameTexture?.akariSurfaceTexture
//                if (akariSurfaceTexture != null) {
//                    // テクスチャ更新
//                    akariSurfaceTexture.checkAndUpdateTexImage()
//                    akariSurfaceTexture.getTransformMatrix(mSTMatrix)
//
//                    // OpenGL 描画
//                    GLUtil.drawFrame(
//                        outputWidth = surfaceView.width,
//                        outputHeight = surfaceView.height,
//                        texMatrixLoc = uniformLocation!!.texMatrixLoc,
//                        surfaceTransform = mSTMatrix
//                    )
//                }
//            }
//
//            override fun onSurfaceCreated(spec: EGLSpec, config: EGLConfig, surface: Surface, width: Int, height: Int): EGLSurface? {
//                return spec.eglCreateWindowSurface(
//                    config = config,
//                    surface = surface,
//                    // これで OpenGLES + SurfaceView で画面が HDR 表示になる
//                    configAttributes = EGLConfigAttributes {
//                        if (/*use10bitPipeline*/ true) {
//                            GLUtil.EGL_GL_COLORSPACE_KHR to GLUtil.EGL_GL_COLORSPACE_BT2020_HLG_EXT
//                        }
//                    }
//                )
//            }
//        })
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {

            Button(onClick = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }) {
                Text(text = "選択")
            }

            Button(onClick = { start() }) {
                Text(text = "実行")
            }

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.7f),
                factory = { surfaceView }
            )
        }
    }
}