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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zentra.z3d.core.PrimitiveKind
import com.zentra.z3d.ui.components.SectionTitle
import com.zentra.z3d.ui.editor.EditorViewModel
import com.zentra.z3d.ui.theme.ZentraColors

private data class AddItem(val kind: PrimitiveKind, val label: String, val icon: ImageVector)

private val meshItems = listOf(
    AddItem(PrimitiveKind.CUBE, "Cubo", Icons.Default.CropSquare),
    AddItem(PrimitiveKind.SPHERE, "Esfera", Icons.Default.Circle),
    AddItem(PrimitiveKind.CYLINDER, "Cilindro", Icons.Default.CropPortrait),
    AddItem(PrimitiveKind.CONE, "Cone", Icons.Default.ChangeHistory),
    AddItem(PrimitiveKind.PLANE, "Plano", Icons.Default.CropLandscape),
    AddItem(PrimitiveKind.TORUS, "Torus", Icons.Default.DonutLarge),
    AddItem(PrimitiveKind.CAPSULE, "Cápsula", Icons.Default.Category),
    AddItem(PrimitiveKind.TEXT, "Texto 3D", Icons.Default.TextFields),
    AddItem(PrimitiveKind.EMPTY, "Empty", Icons.Default.CenterFocusWeak)
)

private val lightItems = listOf(
    AddItem(PrimitiveKind.LIGHT_POINT, "Ponto", Icons.Default.Lightbulb),
    AddItem(PrimitiveKind.LIGHT_SPOT, "Spot", Icons.Default.FlashlightOn),
    AddItem(PrimitiveKind.LIGHT_DIR, "Direcional", Icons.Default.WbSunny),
    AddItem(PrimitiveKind.LIGHT_AREA, "Área", Icons.Default.GridOn),
    AddItem(PrimitiveKind.CAMERA, "Câmera", Icons.Default.CameraAlt)
)

@Composable
fun AddObjectSheet(vm: EditorViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        SectionTitle("MALHAS")
        ItemGrid(meshItems) { vm.addPrimitive(it) }
        Spacer(Modifier.height(8.dp))
        SectionTitle("LUZES E CÂMERA")
        ItemGrid(lightItems) { vm.addPrimitive(it) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ItemGrid(items: List<AddItem>, onPick: (PrimitiveKind) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (chunk in items.chunked(3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (item in chunk) {
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ZentraColors.surface2)
                            .clickable { onPick(item.kind) }
                            .padding(horizontal = 10.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(item.icon, null, tint = ZentraColors.accent2, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(item.label, color = ZentraColors.textMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
                repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
