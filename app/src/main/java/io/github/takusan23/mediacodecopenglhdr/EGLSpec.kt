package io.github.takusan23.mediacodecopenglhdr

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import androidx.graphics.opengl.egl.EGLSpec

val EGLSpec.Companion.V14ES3: EGLSpec
    get() = object : EGLSpec by V14 {

        private val contextAttributes = intArrayOf(
            // GLES VERSION 3
            EGL14.EGL_CONTEXT_CLIENT_VERSION,
            3,
            // HWUI provides the ability to configure a context priority as well but that only
            // seems to be configured on SystemUIApplication. This might be useful for
            // front buffer rendering situations for performance.
            EGL14.EGL_NONE
        )

        override fun eglCreateContext(config: EGLConfig): EGLContext {
            return EGL14.eglCreateContext(
                EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY),
                config,
                // not creating from a shared context
                EGL14.EGL_NO_CONTEXT,
                contextAttributes,
                0
            )
        }
    }