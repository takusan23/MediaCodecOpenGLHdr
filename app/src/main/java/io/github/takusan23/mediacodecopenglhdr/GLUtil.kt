/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.takusan23.mediacodecopenglhdr

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

object GLUtil {

    data class CreateProgramResult(
        val vertexShader: Int,
        val fragmentShader: Int,
        val program: Int
    )

    fun createProgram(vertShader: String, fragShader: String): CreateProgramResult {
        var vertexShader = -1
        var fragmentShader = -1
        var program = -1
        try {
            vertexShader = loadShader(
                GLES20.GL_VERTEX_SHADER,
                vertShader
            )
            fragmentShader = loadShader(
                GLES20.GL_FRAGMENT_SHADER,
                fragShader
            )
            program = GLES20.glCreateProgram()
            checkGlErrorOrThrow("glCreateProgram")
            GLES20.glAttachShader(program, vertexShader)
            checkGlErrorOrThrow("glAttachShader")
            GLES20.glAttachShader(program, fragmentShader)
            checkGlErrorOrThrow("glAttachShader")
            GLES20.glLinkProgram(program)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(
                program,
                GLES20.GL_LINK_STATUS,
                linkStatus,
                /*offset=*/
                0
            )
            check(linkStatus[0] == GLES20.GL_TRUE) {
                "Could not link program: " + GLES20.glGetProgramInfoLog(
                    program
                )
            }
        } catch (e: Exception) {
            if (vertexShader != -1) {
                GLES20.glDeleteShader(vertexShader)
            }
            if (fragmentShader != -1) {
                GLES20.glDeleteShader(fragmentShader)
            }
            if (program != -1) {
                GLES20.glDeleteProgram(program)
            }
            throw e
        }
        return CreateProgramResult(vertexShader, fragmentShader, program)
    }

    data class UniformLocation(
        val positionLoc: Int,
        val texCoordLoc: Int,
        val texMatrixLoc: Int,
        val samplerLoc: Int
    )

    fun loadLocations(programHandle: Int): UniformLocation {
        val positionLoc = GLES20.glGetAttribLocation(programHandle, "aPosition")
        checkLocationOrThrow(positionLoc, "aPosition")
        val texCoordLoc = GLES20.glGetAttribLocation(programHandle, "aTextureCoord")
        checkLocationOrThrow(texCoordLoc, "aTextureCoord")
        val texMatrixLoc = GLES20.glGetUniformLocation(programHandle, "uTexMatrix")
        checkLocationOrThrow(texMatrixLoc, "uTexMatrix")
        val samplerLoc = GLES20.glGetUniformLocation(programHandle, VAR_TEXTURE)
        checkLocationOrThrow(samplerLoc, VAR_TEXTURE)

        return UniformLocation(positionLoc, texCoordLoc, texMatrixLoc, samplerLoc)
    }

    fun createTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        checkGlErrorOrThrow("glGenTextures")
        val texId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        checkGlErrorOrThrow("glBindTexture $texId")
        GLES20.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_NEAREST.toFloat()
        )
        GLES20.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR.toFloat()
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        checkGlErrorOrThrow("glTexParameter")
        return texId
    }

    fun useAndConfigureProgram(
        createProgramResult: CreateProgramResult,
        uniformLocation: UniformLocation,
        externalTextureId: Int
    ) {
        // Select the program.
        GLES20.glUseProgram(createProgramResult.program)
        checkGlErrorOrThrow("glUseProgram")

        // Set the texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTextureId)
        GLES20.glUniform1i(uniformLocation.samplerLoc, 0)

        if (/*use10bitPipeline*/ true) {
            val vaos = IntArray(1)
            GLES30.glGenVertexArrays(1, vaos, 0)
            GLES30.glBindVertexArray(vaos[0])
            checkGlErrorOrThrow("glBindVertexArray")
        }

        val vbos = IntArray(2)
        GLES20.glGenBuffers(2, vbos, 0)
        checkGlErrorOrThrow("glGenBuffers")

        // Connect vertexBuffer to "aPosition".
        val coordsPerVertex = 2
        val vertexStride = 0
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbos[0])
        checkGlErrorOrThrow("glBindBuffer")
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            VERTEX_BUF.capacity() * SIZEOF_FLOAT,
            VERTEX_BUF,
            GLES20.GL_STATIC_DRAW
        )
        checkGlErrorOrThrow("glBufferData")

        // Enable the "aPosition" vertex attribute.
        GLES20.glEnableVertexAttribArray(uniformLocation.positionLoc)
        checkGlErrorOrThrow("glEnableVertexAttribArray")

        GLES20.glVertexAttribPointer(
            uniformLocation.positionLoc,
            coordsPerVertex,
            GLES20.GL_FLOAT,
            /*normalized=*/
            false,
            vertexStride,
            0
        )
        checkGlErrorOrThrow("glVertexAttribPointer")

        // Connect texBuffer to "aTextureCoord".
        val coordsPerTex = 2
        val texStride = 0
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbos[1])
        checkGlErrorOrThrow("glBindBuffer")

        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            TEX_BUF.capacity() * SIZEOF_FLOAT,
            TEX_BUF,
            GLES20.GL_STATIC_DRAW
        )
        checkGlErrorOrThrow("glBufferData")

        // Enable the "aTextureCoord" vertex attribute.
        GLES20.glEnableVertexAttribArray(uniformLocation.texCoordLoc)
        checkGlErrorOrThrow("glEnableVertexAttribArray")

        GLES20.glVertexAttribPointer(
            uniformLocation.texCoordLoc,
            coordsPerTex,
            GLES20.GL_FLOAT,
            /*normalized=*/
            false,
            texStride,
            0
        )
        checkGlErrorOrThrow("glVertexAttribPointer")
    }

    fun drawFrame(outputWidth: Int, outputHeight: Int, texMatrixLoc: Int, surfaceTransform: FloatArray) {
        GLES20.glViewport(
            0,
            0,
            outputWidth,
            outputHeight
        )
        GLES20.glScissor(
            0,
            0,
            outputWidth,
            outputHeight
        )

        GLES20.glUniformMatrix4fv(
            texMatrixLoc,
            /*count=*/
            1,
            /*transpose=*/
            false,
            surfaceTransform,
            /*offset=*/
            0
        )
        checkGlErrorOrThrow("glUniformMatrix4fv")

        // Draw the rect.
        GLES20.glDrawArrays(
            GLES20.GL_TRIANGLE_STRIP,
            /*firstVertex=*/
            0,
            /*vertexCount=*/
            4
        )
    }

    private fun checkLocationOrThrow(location: Int, label: String) {
        check(location >= 0) { "Unable to locate '$label' in program" }
    }

    private fun loadShader(shaderType: Int, source: String): Int {
        val shader = GLES20.glCreateShader(shaderType)
        checkGlErrorOrThrow("glCreateShader type=$shaderType")
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(
            shader,
            GLES20.GL_COMPILE_STATUS,
            compiled,
            /*offset=*/
            0
        )
        check(compiled[0] == GLES20.GL_TRUE) {
            Log.w("GLUtil.loadShader", "Could not compile shader: $source")
            try {
                return@check "Could not compile shader type " +
                        "$shaderType: ${GLES20.glGetShaderInfoLog(shader)}"
            } finally {
                GLES20.glDeleteShader(shader)
            }
        }
        return shader
    }

    private fun checkGlErrorOrThrow(op: String) {
        val error = GLES20.glGetError()
        check(error == GLES20.GL_NO_ERROR) { op + ": GL error 0x" + Integer.toHexString(error) }
    }

    private const val SIZEOF_FLOAT = 4

    private val VERTEX_BUF = floatArrayOf(
        // 0 bottom left
        -1.0f,
        -1.0f,
        // 1 bottom right
        1.0f,
        -1.0f,
        // 2 top left
        -1.0f,
        1.0f,
        // 3 top right
        1.0f,
        1.0f
    ).toBuffer()

    private val TEX_BUF = floatArrayOf(
        // 0 bottom left
        0.0f,
        0.0f,
        // 1 bottom right
        1.0f,
        0.0f,
        // 2 top left
        0.0f,
        1.0f,
        // 3 top right
        1.0f,
        1.0f
    ).toBuffer()

    private const val TAG = "ShaderCopy"
    private const val GL_THREAD_NAME = TAG

    private const val VAR_TEXTURE_COORD = "vTextureCoord"
    private val DEFAULT_VERTEX_SHADER =
        """
        uniform mat4 uTexMatrix;
        attribute vec4 aPosition;
        attribute vec4 aTextureCoord;
        varying vec2 $VAR_TEXTURE_COORD;
        void main() {
            gl_Position = aPosition;
            $VAR_TEXTURE_COORD = (uTexMatrix * aTextureCoord).xy;
        }
            """.trimIndent()

    val TEN_BIT_VERTEX_SHADER =
        """
        #version 300 es
        in vec4 aPosition;
        in vec4 aTextureCoord;
        uniform mat4 uTexMatrix;
        out vec2 $VAR_TEXTURE_COORD;
        void main() {
          gl_Position = aPosition;
          $VAR_TEXTURE_COORD = (uTexMatrix * aTextureCoord).xy;
        }
            """.trimIndent()

    private const val VAR_TEXTURE = "sTexture"
    private val DEFAULT_FRAGMENT_SHADER =
        """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 $VAR_TEXTURE_COORD;
        uniform samplerExternalOES $VAR_TEXTURE;
        void main() {
            gl_FragColor = texture2D($VAR_TEXTURE, $VAR_TEXTURE_COORD);
        }
            """.trimIndent()

    val TEN_BIT_FRAGMENT_SHADER =
        """
        #version 300 es
        #extension GL_EXT_YUV_target : require
        precision mediump float;
        uniform __samplerExternal2DY2YEXT $VAR_TEXTURE;
        in vec2 $VAR_TEXTURE_COORD;
        out vec3 outColor;
        
        vec3 yuvToRgb(vec3 yuv) {
          const vec3 yuvOffset = vec3(0.0625, 0.5, 0.5);
          const mat3 yuvToRgbColorTransform = mat3(
            1.1689f, 1.1689f, 1.1689f,
            0.0000f, -0.1881f, 2.1502f,
            1.6853f, -0.6530f, 0.0000f
          );
          return clamp(yuvToRgbColorTransform * (yuv - yuvOffset), 0.0, 1.0);
        }
        
        void main() {
          outColor = yuvToRgb(texture($VAR_TEXTURE, $VAR_TEXTURE_COORD).xyz);
        }
            """.trimIndent()

    const val EGL_GL_COLORSPACE_KHR = 0x309D
    const val EGL_GL_COLORSPACE_BT2020_HLG_EXT = 0x3540

    private val TEN_BIT_REQUIRED_EGL_EXTENSIONS = listOf(
        "EGL_EXT_gl_colorspace_bt2020_hlg",
        "EGL_EXT_yuv_surface"
    )

    private fun FloatArray.toBuffer(): FloatBuffer {
        val bb = ByteBuffer.allocateDirect(size * SIZEOF_FLOAT)
        bb.order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        fb.put(this)
        fb.position(0)
        return fb
    }

}