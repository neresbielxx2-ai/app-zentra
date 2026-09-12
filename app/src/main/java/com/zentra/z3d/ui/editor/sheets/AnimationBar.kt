package com.zentra.z3d.ui.editor.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zentra.z3d.ui.editor.EditorViewModel
import com.zentra.z3d.ui.theme.ZentraColors

/** Timeline inferior de animacao (keyframes de Position/Rotation/Scale). */
@Composable
fun AnimationBar(vm: EditorViewModel, modifier: Modifier = Modifier) {
    val playing by vm.animPlaying.collectAsState()
    val time by vm.animTime.collectAsState()
    val dur by vm.animDuration.collectAsState()
    val tick by vm.sceneVersion.collectAsState()
    @Suppress("UNUSED_EXPRESSION")
    tick
    val selection by vm.selection.collectAsState()
    val scene = vm.lockedScene()
    val keyCount = scene?.animation?.trackFor(selection.lastOrNull() ?: "")?.keys?.size ?: 0

    Column(
        modifier
            .fillMaxWidth()
            .background(ZentraColors.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(onClick = { vm.rewind() }) {
                Icon(Icons.Default.SkipPrevious, "Voltar ao início", tint = ZentraColors.textMain)
            }
            IconButton(onClick = { vm.playPause() }) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (playing) "Pausar" else "Reproduzir",
                    tint = ZentraColors.accent2
                )
            }
            Text(
                "${String.format("%.1f", time / 1000f)}s",
                color = ZentraColors.textMain, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Slider(
                value = time.toFloat().coerceIn(0f, dur.toFloat().coerceAtLeast(1f)),
                onValueChange = { vm.seekTo(it.toLong()) },
                valueRange = 0f..dur.toFloat().coerceAtLeast(1f),
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = ZentraColors.accent2,
                    activeTrackColor = ZentraColors.accent,
                    inactiveTrackColor = ZentraColors.surface3
                )
            )
            Text(
                "$keyCount keys",
                color = ZentraColors.textDim, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            IconButton(onClick = { vm.addKey() }) {
                Icon(Icons.Default.AddCircle, "Adicionar keyframe", tint = ZentraColors.success)
            }
            IconButton(onClick = { vm.removeKey() }) {
                Icon(Icons.Default.RemoveCircle, "Remover keyframe", tint = ZentraColors.danger)
            }
        }
    }
}
