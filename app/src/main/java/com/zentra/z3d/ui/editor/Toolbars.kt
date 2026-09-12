package com.zentra.z3d.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zentra.z3d.ui.components.ToolButton
import com.zentra.z3d.ui.theme.ZentraColors

data class ToolDef(val tool: Tool, val icon: ImageVector, val label: String)

val mainTools = listOf(
    ToolDef(Tool.SELECT, Icons.Default.TouchApp, "Selecionar"),
    ToolDef(Tool.MOVE, Icons.Default.OpenWith, "Mover"),
    ToolDef(Tool.ROTATE, Icons.Default.RotateRight, "Girar"),
    ToolDef(Tool.SCALE, Icons.Default.ZoomOutMap, "Escala"),
    ToolDef(Tool.EDIT, Icons.Default.Edit, "Editar"),
    ToolDef(Tool.ADD, Icons.Default.Add, "Adicionar"),
    ToolDef(Tool.MATERIAL, Icons.Default.Palette, "Material"),
    ToolDef(Tool.LIGHT, Icons.Default.Lightbulb, "Luz"),
    ToolDef(Tool.CAMERA, Icons.Default.CameraAlt, "Câmera"),
    ToolDef(Tool.ANIMATE, Icons.Default.Timeline, "Animar")
)

@Composable
fun EditorTopBar(vm: EditorViewModel, onBack: () -> Unit, onRenameProject: () -> Unit) {
    val name by vm.projectName.collectAsState()
    val dirty by vm.dirty.collectAsState()
    val canUndo by vm.canUndo.collectAsState()
    val canRedo by vm.canRedo.collectAsState()
    val mode by vm.objMode.collectAsState()

    Row(
        Modifier
            .fillMaxWidth()
            .background(ZentraColors.surface)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, "Voltar", tint = ZentraColors.textMain)
        }
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onRenameProject)
                .padding(horizontal = 4.dp)
        ) {
            Text(
                name + if (dirty) " •" else "",
                color = ZentraColors.textMain,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (mode == ObjMode.EDIT) "Edit Mode" else "Object Mode",
                color = if (mode == ObjMode.EDIT) ZentraColors.warn else ZentraColors.textDim,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        IconButton(onClick = { vm.undo() }, enabled = canUndo) {
            Icon(Icons.Default.Undo, "Desfazer", tint = if (canUndo) ZentraColors.textMain else ZentraColors.surface3)
        }
        IconButton(onClick = { vm.redo() }, enabled = canRedo) {
            Icon(Icons.Default.Redo, "Refazer", tint = if (canRedo) ZentraColors.textMain else ZentraColors.surface3)
        }
        IconButton(onClick = { vm.save() }) {
            Icon(Icons.Default.Save, "Salvar", tint = ZentraColors.accent2)
        }
        IconButton(onClick = { vm.openSheet.value = Sheet.MENU }) {
            Icon(Icons.Default.Menu, "Menu", tint = ZentraColors.textMain)
        }
    }
}

@Composable
fun MainToolbar(vm: EditorViewModel, vertical: Boolean, modifier: Modifier = Modifier) {
    val cur by vm.tool.collectAsState()
    if (vertical) {
        LazyColumn(
            modifier = modifier.background(ZentraColors.surface),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(mainTools) { t ->
                ToolButton(
                    icon = t.icon, label = t.label, selected = cur == t.tool,
                    onClick = { vm.setTool(t.tool) },
                    modifier = Modifier.width(74.dp).height(58.dp)
                )
            }
        }
    } else {
        LazyRow(
            modifier = modifier
                .fillMaxWidth()
                .background(ZentraColors.surface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(mainTools) { t ->
                ToolButton(
                    icon = t.icon, label = t.label, selected = cur == t.tool,
                    onClick = { vm.setTool(t.tool) },
                    modifier = Modifier.width(70.dp).height(58.dp)
                )
            }
        }
    }
}

/** Barra contextual: trava de eixo + foco + vistas. */
@Composable
fun ContextBar(vm: EditorViewModel, modifier: Modifier = Modifier) {
    val lock by vm.axisLock.collectAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ZentraColors.bg)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Eixo", color = ZentraColors.textDim, fontSize = 12.sp)
        AxisChip("XYZ", lock == AxisLock.ALL) { vm.axisLock.value = AxisLock.ALL; vm.bump() }
        AxisChip("X", lock == AxisLock.X, ZentraColors.axisX) { vm.axisLock.value = AxisLock.X; vm.bump() }
        AxisChip("Y", lock == AxisLock.Y, ZentraColors.axisY) { vm.axisLock.value = AxisLock.Y; vm.bump() }
        AxisChip("Z", lock == AxisLock.Z, ZentraColors.axisZ) { vm.axisLock.value = AxisLock.Z; vm.bump() }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { vm.focusSelected() }, modifier = Modifier.height(40.dp)) {
            Icon(Icons.Default.CenterFocusStrong, "Focar seleção", tint = ZentraColors.accent2)
        }
        ViewPresetMenu(vm)
    }
}

@Composable
private fun AxisChip(label: String, selected: Boolean, color: Color = ZentraColors.accent, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color,
            selectedLabelColor = Color.White,
            containerColor = ZentraColors.surface2,
            labelColor = ZentraColors.textDim
        ),
        border = null
    )
}

@Composable
fun ViewPresetMenu(vm: EditorViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = false,
            onClick = { expanded = true },
            label = { Text("Vista", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Videocam, null, tint = ZentraColors.textDim) },
            colors = FilterChipDefaults.filterChipColors(
                containerColor = ZentraColors.surface2,
                labelColor = ZentraColors.textMain
            ),
            border = null
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(ZentraColors.surface2)
        ) {
            listOf("Front", "Back", "Left", "Right", "Top", "Bottom").forEach { name ->
                DropdownMenuItem(
                    text = { Text(name, color = ZentraColors.textMain) },
                    onClick = {
                        expanded = false
                        vm.cameraPreset(name.uppercase())
                    }
                )
            }
        }
    }
}

@Composable
fun hintFor(vm: EditorViewModel): String {
    val tool by vm.tool.collectAsState()
    val mode by vm.objMode.collectAsState()
    val hasSel by vm.selection.collectAsState()
    return when {
        mode == ObjMode.EDIT -> "Toque: selecionar · Arraste: mover · 2 dedos: zoom/pan"
        tool == Tool.SELECT && hasSel.isEmpty() -> "Toque num objeto · 1 dedo: orbitar · 2 dedos: zoom"
        tool == Tool.SELECT -> "1 dedo: orbitar · 2 dedos: zoom/pan · 3 dedos: orbitar"
        tool == Tool.MOVE -> "Arraste para mover · Trave o eixo acima"
        tool == Tool.ROTATE -> "Arraste para girar · Trave o eixo acima"
        tool == Tool.SCALE -> "Arraste para cima: aumentar · Para baixo: diminuir"
        else -> "1 dedo: orbitar · 2 dedos: zoom/pan"
    }
}
