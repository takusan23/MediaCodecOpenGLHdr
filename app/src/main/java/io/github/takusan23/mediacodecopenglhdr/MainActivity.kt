package io.github.takusan23.mediacodecopenglhdr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.takusan23.mediacodecopenglhdr.ui.theme.MediaCodecOpenGLHdrTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediaCodecOpenGLHdrTheme {
                MainScreen()
            }
        }
    }
}

