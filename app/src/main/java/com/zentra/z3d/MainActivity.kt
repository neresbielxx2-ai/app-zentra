package com.zentra.z3d

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.zentra.z3d.ui.editor.EditorScreenHost
import com.zentra.z3d.ui.home.HomeScreen
import com.zentra.z3d.ui.theme.ZentraColors
import com.zentra.z3d.ui.theme.ZentraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZentraTheme {
                var projectId by rememberSaveable { mutableStateOf<String?>(null) }
                var session by rememberSaveable { mutableIntStateOf(0) }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(ZentraColors.bg)
                ) {
                    val pid = projectId
                    if (pid == null) {
                        HomeScreen(
                            onOpenProject = {
                                projectId = it
                                session += 1
                            }
                        )
                    } else {
                        EditorScreenHost(
                            projectId = pid,
                            session = session,
                            onExit = { projectId = null }
                        )
                    }
                }
            }
        }
    }
}
