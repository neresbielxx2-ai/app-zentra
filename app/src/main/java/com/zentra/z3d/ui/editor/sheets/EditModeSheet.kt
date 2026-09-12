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
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zentra.z3d.ui.components.ZentraSecondaryButton
import com.zentra.z3d.ui.components.ZentraSliderRow
import com.zentra.z3d.ui.editor.EditComp
import com.zentra.z3d.ui.editor.EditorViewModel
import com.zentra.z3d.ui.theme.ZentraColors

/** Painel persistente do Edit Mode (acima da toolbar). */
@Composable
fun EditModePanel(vm: EditorViewModel, modifier: Modifier = Modifier) {
    val comp by vm.editComp.collectAsState()
    val vSel by vm.editSel.collectAsState()
    val eSel by vm.editEdgesSel.collectAsState()
    var expanded by remember { mutableStateOf(true) }
    var extrudeDist by remember { mutableFloatStateOf(0.6f) }
    var insetAmt by remember { mutableFloatStateOf(0.3f) }
    var bevelAmt by remember { mutableFloatStateOf(0.15f) }

    Column(
        modifier
            .fillMaxWidth()
            .background(ZentraColors.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompChip("Vértice", Icons.Default.Grain, comp == EditComp.VERTEX) { vm.setEditComp(EditComp.VERTEX) }
            CompChip("Edge", Icons.Default.ShowChart, comp == EditComp.EDGE) { vm.setEditComp(EditComp.EDGE) }
            CompChip("Face", Icons.Default.CropSquare, comp == EditComp.FACE) { vm.setEditComp(EditComp.FACE) }
            Spacer(Modifier.weight(1f))
            Text(
                when (comp) {
                    EditComp.VERTEX -> "${vSel.size} sel."
                    EditComp.EDGE -> "${eSel.size} sel."
                    EditComp.FACE -> "${vSel.size} sel."
                },
                color = ZentraColors.textDim, fontSize = 12.sp
            )
            Text(
                if (expanded) "▲" else "▼",
                color = ZentraColors.accent2, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .padding(8.dp)
            )
        }

        if (expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniButton("Selecionar tudo", Icons.Default.SelectAll, Modifier.weight(1f)) { vm.selectAllEdit() }
                    MiniButton("Limpar", Icons.Default.Close, Modifier.weight(1f)) { vm.clearEditSelection() }
                }
                Spacer(Modifier.height(6.dp))
                ZentraSliderRow("Distância do Extrude", extrudeDist, 0.05f..3f, { extrudeDist = it })
                ZentraSliderRow("Quantidade do Inset", insetAmt, 0.05f..0.9f, { insetAmt = it })
                ZentraSliderRow("Tamanho do Bevel", bevelAmt, 0.02f..1f, { bevelAmt = it })
                Spacer(Modifier.height(4.dp))
                val faceOnly = comp == EditComp.FACE
                OpRow(
                    OpDef("Extrude", Icons.Default.OpenWith, faceOnly) { vm.extrudeSel(extrudeDist) },
                    OpDef("Inset", Icons.Default.CropSquare, faceOnly) { vm.insetSel(insetAmt) }
                )
                OpRow(
                    OpDef("Bevel", Icons.Default.RoundedCorner, true) { vm.bevelSel(bevelAmt) },
                    OpDef("Subdivide", Icons.Default.GridOn, true) { vm.subdivideSel() }
                )
                OpRow(
                    OpDef("Loop Cut", Icons.Default.CallSplit, faceOnly) { vm.loopCutSel() },
                    OpDef("Merge", Icons.Default.MergeType, true) { vm.mergeSel() }
                )
                OpRow(
                    OpDef("Dissolve", Icons.Default.UnfoldMore, true) { vm.dissolveSel() },
                    OpDef("Deletar", Icons.Default.Delete, true) { vm.deleteElemSel() }
                )
                OpRow(
                    OpDef("Separar", Icons.Default.Flip, faceOnly) { vm.separateSel() },
                    null
                )
                Spacer(Modifier.height(6.dp))
                ZentraSecondaryButton(
                    "Voltar ao Object Mode", onClick = { vm.exitEditMode() },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CompChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.padding(0.dp)) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = ZentraColors.accent,
            selectedLabelColor = Color.White,
            containerColor = ZentraColors.surface2,
            labelColor = ZentraColors.textDim
        ),
        border = null
    )
}

@Composable
private fun MiniButton(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ZentraColors.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = ZentraColors.accent2, modifier = Modifier.padding(end = 6.dp))
        Text(label, color = ZentraColors.textMain, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

private data class OpDef(val label: String, val icon: ImageVector, val enabled: Boolean, val run: () -> Unit)

@Composable
private fun OpRow(a: OpDef, b: OpDef?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OpButton(a, Modifier.weight(1f))
        if (b != null) OpButton(b, Modifier.weight(1f))
        else Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun OpButton(def: OpDef, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (def.enabled) ZentraColors.surface2 else ZentraColors.surface2.copy(alpha = 0.4f))
            .clickable(enabled = def.enabled, onClick = def.run)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            def.icon, null,
            tint = if (def.enabled) ZentraColors.accent2 else ZentraColors.surface3,
            modifier = Modifier.padding(end = 6.dp)
        )
        Text(
            def.label,
            color = if (def.enabled) ZentraColors.textMain else ZentraColors.textDim.copy(alpha = 0.5f),
            fontSize = 13.sp, fontWeight = FontWeight.Medium
        )
    }
}
