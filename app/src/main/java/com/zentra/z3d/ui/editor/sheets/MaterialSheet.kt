package com.zentra.z3d.ui.editor.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zentra.z3d.core.Rgba
import com.zentra.z3d.ui.components.SectionTitle
import com.zentra.z3d.ui.components.ZentraSecondaryButton
import com.zentra.z3d.ui.editor.EditorViewModel
import com.zentra.z3d.ui.theme.ZentraColors

@Composable
fun MaterialSheet(vm: EditorViewModel, onPickTexture: () -> Unit) {
    val tick by vm.sceneVersion.collectAsState()
    @Suppress("UNUSED_EXPRESSION")
    tick
    val scene = vm.lockedScene()
    val mat = vm.activeMaterial()

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // ---------- lista de materiais ----------
        SectionTitle("MATERIAIS DO PROJETO")
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(scene?.materials ?: emptyList(), key = { it.id }) { m ->
                    val selected = m.id == mat?.id
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) ZentraColors.accent else ZentraColors.surface2)
                            .then(
                                if (selected) Modifier else Modifier.border(1.dp, ZentraColors.surface3, RoundedCornerShape(12.dp))
                            )
                            .clickable { vm.setActiveMaterial(m.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(m.baseColor.r, m.baseColor.g, m.baseColor.b))
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            m.name, fontSize = 13.sp,
                            color = if (selected) Color.White else ZentraColors.textMain,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
            IconButton(onClick = { vm.createMaterial() }) {
                Icon(Icons.Default.Add, "Novo material", tint = ZentraColors.accent2)
            }
        }

        if (mat == null) {
            Spacer(Modifier.height(24.dp))
            return
        }

        // ---------- preview + aplicar ----------
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ZentraColors.surface2)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(mat.baseColor.r, mat.baseColor.g, mat.baseColor.b))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(mat.name, color = ZentraColors.textMain, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    "Rug ${String.format("%.2f", mat.roughness)} · Met ${String.format("%.2f", mat.metallic)}",
                    color = ZentraColors.textDim, fontSize = 12.sp
                )
            }
            IconButton(onClick = { vm.assignMaterialToSelected(mat.id) }) {
                Icon(Icons.Default.Check, "Aplicar ao selecionado", tint = ZentraColors.success)
            }
            IconButton(onClick = { vm.deleteMaterial(mat.id) }) {
                Icon(Icons.Default.Delete, "Excluir material", tint = ZentraColors.danger)
            }
        }
        Text(
            "Toque em ✓ para aplicar ao objeto selecionado",
            color = ZentraColors.textDim, fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp)
        )

        // ---------- cor base ----------
        SectionTitle("COR BASE")
        ColorEditor(
            color = mat.baseColor,
            onChange = { c -> vm.updateMaterial(mat.id) { it.baseColor = c } },
            onStrokeStart = { vm.beginMaterialStroke() },
            onStrokeEnd = { vm.commitMaterialEdit() }
        )

        // ---------- propriedades ----------
        SectionTitle("ACABAMENTO")
        MatSlider("Rugosidade (Roughness)", mat.roughness, 0f..1f, vm, mat.id) { m, v -> m.roughness = v }
        MatSlider("Metálico (Metallic)", mat.metallic, 0f..1f, vm, mat.id) { m, v -> m.metallic = v }
        MatSlider("Opacidade", mat.opacity, 0.05f..1f, vm, mat.id) { m, v -> m.opacity = v }
        MatSlider("Brilho emissivo", mat.emissionStrength, 0f..4f, vm, mat.id) { m, v -> m.emissionStrength = v }

        SectionTitle("COR EMISSIVA")
        ColorEditor(
            color = mat.emission,
            onChange = { c -> vm.updateMaterial(mat.id) { it.emission = c } },
            onStrokeStart = { vm.beginMaterialStroke() },
            onStrokeEnd = { vm.commitMaterialEdit() }
        )

        // ---------- textura ----------
        SectionTitle("TEXTURA")
        if (mat.textureFile != null) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(mat.textureFile!!, color = ZentraColors.textMain, fontSize = 13.sp, modifier = Modifier.weight(1f))
                ZentraSecondaryButton("Trocar", onClick = onPickTexture)
                Spacer(Modifier.size(8.dp))
                ZentraSecondaryButton("Remover", onClick = { vm.removeTexture() })
            }
            Spacer(Modifier.height(8.dp))
            MatSlider("Repetição U", mat.uvScale.x, 0.1f..8f, vm, mat.id) { m, v -> m.uvScale.x = v }
            MatSlider("Repetição V", mat.uvScale.y, 0.1f..8f, vm, mat.id) { m, v -> m.uvScale.y = v }
            MatSlider("Deslocamento U", mat.uvOffset.x, -2f..2f, vm, mat.id) { m, v -> m.uvOffset.x = v }
            MatSlider("Deslocamento V", mat.uvOffset.y, -2f..2f, vm, mat.id) { m, v -> m.uvOffset.y = v }
        } else {
            ZentraSecondaryButton(
                "Importar imagem da galeria", onClick = onPickTexture,
                icon = Icons.Default.Image, modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MatSlider(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    vm: EditorViewModel, matId: String,
    apply: (com.zentra.z3d.core.MaterialData, Float) -> Unit
) {
    var stroking by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = ZentraColors.textMain, fontSize = 13.sp)
            Text(String.format("%.2f", value), color = ZentraColors.accent2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = {
                if (!stroking) {
                    stroking = true
                    vm.beginMaterialStroke()
                }
                vm.updateMaterial(matId) { m -> apply(m, it) }
            },
            onValueChangeFinished = { stroking = false; vm.commitMaterialEdit() },
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = ZentraColors.accent,
                activeTrackColor = ZentraColors.accent,
                inactiveTrackColor = ZentraColors.surface3
            )
        )
    }
}

private val presetColors = listOf(
    "#FFFFFF", "#1A1D29", "#FF5C5C", "#FFB020", "#3ED598",
    "#38E1FF", "#7C5CFF", "#FF7AD9", "#8A6D4B", "#9AA3B8"
)

@Composable
fun ColorEditor(
    color: Rgba,
    onChange: (Rgba) -> Unit,
    onStrokeStart: () -> Unit,
    onStrokeEnd: () -> Unit
) {
    var stroking by remember { mutableStateOf(false) }
    fun emit(r: Float, g: Float, b: Float) {
        if (!stroking) {
            stroking = true
            onStrokeStart()
        }
        onChange(Rgba(r, g, b, 1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presetColors.forEach { hex ->
            val c = Color(android.graphics.Color.parseColor(hex))
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .clickable {
                        onStrokeStart()
                        onChange(Rgba(c.red, c.green, c.blue, 1f))
                        onStrokeEnd()
                    }
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    ColorChannel("R", color.r, Color(1f, 0.4f, 0.4f),
        onChange = { emit(it, color.g, color.b) },
        onFinished = { stroking = false; onStrokeEnd() })
    ColorChannel("G", color.g, Color(0.4f, 1f, 0.5f),
        onChange = { emit(color.r, it, color.b) },
        onFinished = { stroking = false; onStrokeEnd() })
    ColorChannel("B", color.b, Color(0.4f, 0.6f, 1f),
        onChange = { emit(color.r, color.g, it) },
        onFinished = { stroking = false; onStrokeEnd() })
}

@Composable
private fun ColorChannel(
    label: String, value: Float, color: Color,
    onChange: (Float) -> Unit, onFinished: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp))
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = color, activeTrackColor = color, inactiveTrackColor = ZentraColors.surface3
            )
        )
    }
}
