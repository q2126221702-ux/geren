package com.xuzheng.tiyuengine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xuzheng.tiyuengine.ui.QuizApp
import com.xuzheng.tiyuengine.ui.theme.TikuziyongTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TikuziyongTheme {
                QuizApp()
            }
        }
    }
}
