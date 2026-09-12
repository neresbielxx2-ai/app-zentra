package com.zentra.z3d.ui.editor.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zentra.z3d.ui.components.EmptyHint
import com.zentra.z3d.ui.components.PromptDialog
import com.zentra.z3d.ui.components.SectionTitle
import com.zentra.z3d.ui.components.ZentraSecondaryButton
import com.zentra.z3d.ui.components.ZentraSliderRow
import com.zentra.z3d.ui.editor.EditorViewModel
import com.zentra.z3d.ui.editor.Sheet
import com.zentra.z3d.ui.theme.ZentraColors

@Composable
fun PropertiesSheet(vm: EditorViewModel, onEditMaterial: () -> Unit) {
    val tick by vm.sceneVersion.collectAsState()
    @Suppress("UNUSED_EXPRESSION")
    tick
    val selection by vm.selection.collectAsState()
    val id = selection.lastOrNull()
    val scene = vm.lockedScene()
    val obj = if (scene != null && id != null) scene.find(id) else null

    var showRename by remember { mutableStateOf(false) }
    if (showRename && obj != null) {
        PromptDialog(
            title = "Renomear objeto", initial = obj.name, confirmText = "Renomear",
            onConfirm = { vm.renameActive(it); showRename = false },
            onDismiss = { showRename = false }
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        if (obj == null) {
            EmptyHint("Nenhum objeto selecionado.\nToque em um objeto na viewport.")
            ZentraSecondaryButton(
                "Ver lista de objetos", onClick = { vm.openSheet.value = Sheet.OBJECTS },
                icon = Icons.Default.Layers, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            return
        }

        // ---------- Transform ----------
        SectionTitle("TRANSFORM")
        TransformEditor(vm, obj.transform.position.x, obj.transform.position.y, obj.transform.position.z, kind = 0, range = -10f..10f)
        TransformEditor(vm, obj.transform.rotation.x, obj.transform.rotation.y, obj.transform.rotation.z, kind = 1, range = -180f..180f)
        TransformEditor(vm, obj.transform.scale.x, obj.transform.scale.y, obj.transform.scale.z, kind = 2, range = 0.1f..5f)

        // ---------- Aparencia ----------
        SectionTitle("APARÊNCIA")
        val mat = scene?.materialOf(obj)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                mat?.name ?: "Sem material",
                color = ZentraColors.textMain, fontSize = 14.sp, fontWeight = FontWeight.Medium
            )
            ZentraSecondaryButton("Editar material", onClick = onEditMaterial)
        }

        // ---------- Objeto ----------
        SectionTitle("OBJETO")
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(obj.name, color = ZentraColors.textMain, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                androidx.compose.material3.IconButton(onClick = { showRename = true }) {
                    Icon(Icons.Default.DriveFileRenameOutline, "Renomear", tint = ZentraColors.accent2)
                }
                androidx.compose.material3.IconButton(onClick = { vm.deleteSelected(); }) {
                    Icon(Icons.Default.Delete, "Excluir", tint = ZentraColors.danger)
                }
            }
        }
        ToggleRow("Visível", obj.visible) { vm.toggleVisibility(obj.id) }
        ToggleRow("Bloqueado", obj.locked) { vm.toggleLock(obj.id) }

        Spacer(Modifier.height(8.dp))
        ZentraSecondaryButton(
            "Avançado", onClick = { vm.openSheet.value = Sheet.ADVANCED },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TransformEditor(
    vm: EditorViewModel,
    x: Float, y: Float, z: Float,
    kind: Int,
    range: ClosedFloatingPointRange<Float>
) {
    val title = when (kind) {
        0 -> "Posição"
        1 -> "Rotação"
        else -> "Escala"
    }
    Text(title, color = ZentraColors.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    AxisSlider(vm, "X", x, kind, 0, range, ZentraColors.axisX)
    AxisSlider(vm, "Y", y, kind, 1, range, ZentraColors.axisY)
    AxisSlider(vm, "Z", z, kind, 2, range, ZentraColors.axisZ)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun AxisSlider(
    vm: EditorViewModel,
    label: String, value: Float, kind: Int, axis: Int,
    range: ClosedFloatingPointRange<Float>,
    color: androidx.compose.ui.graphics.Color
) {
    var stroking by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 8.dp)
        )
        androidx.compose.material3.Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = {
                if (!stroking) {
                    stroking = true
                    vm.beginStroke()
                }
                vm.setTransformComponent(kind, axis, it)
            },
            onValueChangeFinished = {
                stroking = false
                vm.commitTransformEdit()
            },
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = ZentraColors.surface3
            )
        )
        Text(
            String.format("%.2f", value),
            color = ZentraColors.textDim, fontSize = 12.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = ZentraColors.textMain, fontSize = 14.sp)
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                checkedTrackColor = ZentraColors.accent,
                uncheckedTrackColor = ZentraColors.surface3
            )
        )
    }
}
