package com.zentra.z3d.ui.editor.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zentra.z3d.core.LightKind
import com.zentra.z3d.core.ObjectType
import com.zentra.z3d.core.PrimitiveKind
import com.zentra.z3d.core.Rgba
import com.zentra.z3d.ui.components.EmptyHint
import com.zentra.z3d.ui.components.SectionTitle
import com.zentra.z3d.ui.components.ZentraSecondaryButton
import com.zentra.z3d.ui.editor.EditorViewModel
import com.zentra.z3d.ui.theme.ZentraColors

// ---------------- LUZES ----------------

@Composable
fun LightSheet(vm: EditorViewModel) {
    val tick by vm.sceneVersion.collectAsState()
    @Suppress("UNUSED_EXPRESSION")
    tick
    val scene = vm.lockedScene()
    val lights = scene?.objects?.filter { it.type == ObjectType.LIGHT } ?: emptyList()
    val selection by vm.selection.collectAsState()
    val current = lights.find { it.id == selection.lastOrNull() } ?: lights.firstOrNull()
    val light = current?.light

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        SectionTitle("LUZES DA CENA")
        if (lights.isEmpty()) {
            EmptyHint("Nenhuma luz. Adicione uma abaixo.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lights.forEach { o ->
                    val sel = o.id == current?.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) ZentraColors.surface3 else ZentraColors.surface2)
                            .clickable { vm.selectObject(o.id) }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lightbulb, null,
                            tint = androidx.compose.ui.graphics.Color(
                                o.light?.color?.r ?: 1f, o.light?.color?.g ?: 1f, o.light?.color?.b ?: 1f
                            )
                        )
                        Spacer(Modifier.padding(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text(o.name, color = ZentraColors.textMain, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                kindLabel(o.light?.kind), color = ZentraColors.textDim, fontSize = 12.sp
                            )
                        }
                        IconButton(onClick = { vm.selectObject(o.id); vm.deleteSelected() }) {
                            Icon(Icons.Default.Delete, "Excluir", tint = ZentraColors.danger)
                        }
                    }
                }
            }
        }

        if (current != null && light != null) {
            SectionTitle("COR DA LUZ")
            ColorEditor(
                color = light.color,
                onChange = { c -> vm.updateLight(current.id) { it.color = c } },
                onStrokeStart = { vm.pushUndo() },
                onStrokeEnd = { vm.commitLightEdit() }
            )
            SectionTitle("INTENSIDADE E ALCANCE")
            val maxI = if (light.kind == LightKind.DIRECTIONAL) 8f else 200f
            LightSlider("Intensidade", light.intensity, 0f..maxI, vm, current.id) { l, v -> l.intensity = v }
            if (light.kind != LightKind.DIRECTIONAL) {
                LightSlider("Alcance", light.range, 1f..100f, vm, current.id) { l, v -> l.range = v }
            }
            if (light.kind == LightKind.SPOT) {
                LightSlider("Abertura (graus)", light.spotAngleDeg, 10f..120f, vm, current.id) { l, v -> l.spotAngleDeg = v }
                LightSlider("Suavidade", light.spotBlend, 0f..1f, vm, current.id) { l, v -> l.spotBlend = v }
            }
            Text(
                "Posicione e mire a luz com Mover/Girar na viewport",
                color = ZentraColors.textDim, fontSize = 12.sp
            )
        }

        SectionTitle("ADICIONAR LUZ")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZentraSecondaryButton("Ponto", onClick = { vm.addPrimitive(PrimitiveKind.LIGHT_POINT) }, modifier = Modifier.weight(1f))
            ZentraSecondaryButton("Spot", onClick = { vm.addPrimitive(PrimitiveKind.LIGHT_SPOT) }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZentraSecondaryButton("Direcional", onClick = { vm.addPrimitive(PrimitiveKind.LIGHT_DIR) }, modifier = Modifier.weight(1f))
            ZentraSecondaryButton("Área", onClick = { vm.addPrimitive(PrimitiveKind.LIGHT_AREA) }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun kindLabel(kind: LightKind?): String = when (kind) {
    LightKind.POINT -> "Point Light"
    LightKind.SPOT -> "Spot Light"
    LightKind.DIRECTIONAL -> "Directional Light"
    LightKind.AREA -> "Area Light"
    null -> "Luz"
}

@Composable
private fun LightSlider(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    vm: EditorViewModel, objId: String,
    apply: (com.zentra.z3d.core.LightData, Float) -> Unit
) {
    var stroking by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = ZentraColors.textMain, fontSize = 13.sp)
            Text(String.format("%.1f", value), color = ZentraColors.accent2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = {
                if (!stroking) {
                    stroking = true
                    vm.pushUndo()
                }
                vm.updateLight(objId) { l -> apply(l, it) }
            },
            onValueChangeFinished = { stroking = false; vm.commitLightEdit() },
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = ZentraColors.accent,
                activeTrackColor = ZentraColors.accent,
                inactiveTrackColor = ZentraColors.surface3
            )
        )
    }
}

// ---------------- CAMERAS ----------------

@Composable
fun CameraSheet(vm: EditorViewModel) {
    val tick by vm.sceneVersion.collectAsState()
    @Suppress("UNUSED_EXPRESSION")
    tick
    val scene = vm.lockedScene()
    val cams = scene?.objects?.filter { it.type == ObjectType.CAMERA } ?: emptyList()
    val selection by vm.selection.collectAsState()
    val current = cams.find { it.id == selection.lastOrNull() } ?: cams.firstOrNull()
    val cam = current?.camera

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        SectionTitle("CÂMERAS")
        if (cams.isEmpty()) {
            EmptyHint("Nenhuma câmera no projeto.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cams.forEach { o ->
                    val sel = o.id == current?.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) ZentraColors.surface3 else ZentraColors.surface2)
                            .clickable { vm.selectObject(o.id) }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CameraAlt, null,
                            tint = if (o.camera?.isMain == true) ZentraColors.accent2 else ZentraColors.textDim
                        )
                        Spacer(Modifier.padding(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text(o.name, color = ZentraColors.textMain, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (o.camera?.isMain == true) "Principal" else "Secundária",
                                color = ZentraColors.textDim, fontSize = 12.sp
                            )
                        }
                        IconButton(onClick = { vm.selectObject(o.id); vm.deleteSelected() }) {
                            Icon(Icons.Default.Delete, "Excluir", tint = ZentraColors.danger)
                        }
                    }
                }
            }
        }

        if (current != null && cam != null) {
            SectionTitle("LENTE")
            CamSlider("Campo de visão (FOV)", cam.fovDeg, 10f..120f, vm, current.id) { c, v -> c.fovDeg = v }
            CamSlider("Near", cam.near, 0.01f..5f, vm, current.id) { c, v -> c.near = v }
            CamSlider("Far", cam.far, 10f..1000f, vm, current.id) { c, v -> c.far = v }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ZentraSecondaryButton(
                    "View Camera", onClick = { vm.selectObject(current.id); vm.viewThroughCamera() },
                    icon = Icons.Default.Videocam, modifier = Modifier.weight(1f)
                )
                ZentraSecondaryButton(
                    "Tornar principal", onClick = { vm.setMainCamera(current.id) },
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "Posicione a câmera com Mover/Girar na viewport",
                color = ZentraColors.textDim, fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(Modifier.height(12.dp))
        ZentraSecondaryButton(
            "Adicionar câmera", onClick = { vm.addPrimitive(PrimitiveKind.CAMERA) },
            icon = Icons.Default.Add, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CamSlider(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    vm: EditorViewModel, objId: String,
    apply: (com.zentra.z3d.core.CameraData, Float) -> Unit
) {
    var stroking by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = ZentraColors.textMain, fontSize = 13.sp)
            Text(String.format("%.1f", value), color = ZentraColors.accent2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = {
                if (!stroking) {
                    stroking = true
                    vm.pushUndo()
                }
                vm.updateCameraObj(objId) { o -> o.camera?.let { c -> apply(c, it) } }
            },
            onValueChangeFinished = { stroking = false; vm.commitLightEdit() },
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = ZentraColors.accent,
                activeTrackColor = ZentraColors.accent,
                inactiveTrackColor = ZentraColors.surface3
            )
        )
    }
}
