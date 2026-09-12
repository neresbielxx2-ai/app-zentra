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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zentra.z3d.core.ObjectType
import com.zentra.z3d.settings.Quality
import com.zentra.z3d.ui.components.ChipRow
import com.zentra.z3d.ui.components.PromptDialog
import com.zentra.z3d.ui.components.SectionTitle
import com.zentra.z3d.ui.components.ZentraSecondaryButton
import com.zentra.z3d.ui.components.ZentraSliderRow
import com.zentra.z3d.ui.components.ZentraTextField
import com.zentra.z3d.ui.editor.EditorViewModel
import com.zentra.z3d.ui.editor.Sheet
import com.zentra.z3d.ui.theme.ZentraColors

// ---------------- MENU ----------------

@Composable
fun MenuSheet(vm: EditorViewModel, onPickImport: () -> Unit) {
    var showSaveAs by remember { mutableStateOf(false) }
    val projName by vm.projectName.collectAsState()
    if (showSaveAs) {
        PromptDialog(
            title = "Salvar como", initial = "$projName copy", confirmText = "Salvar",
            onConfirm = { vm.saveAs(it); vm.openSheet.value = null; showSaveAs = false },
            onDismiss = { showSaveAs = false }
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        MenuRow(Icons.Default.Tune, "Propriedades do objeto") { vm.openSheet.value = Sheet.PROPS }
        MenuRow(Icons.Default.Layers, "Lista de objetos") { vm.openSheet.value = Sheet.OBJECTS }
        MenuRow(Icons.Default.Save, "Salvar projeto") { vm.save(); vm.openSheet.value = null }
        MenuRow(Icons.Default.DriveFileRenameOutline, "Salvar como...") { showSaveAs = true }
        MenuRow(Icons.Default.Download, "Importar modelo") { onPickImport() }
        MenuRow(Icons.Default.Upload, "Exportar modelo") { vm.openSheet.value = Sheet.EXPORT }
        MenuRow(Icons.Default.Settings, "Configurações") { vm.openSheet.value = Sheet.SETTINGS }
        MenuRow(Icons.Default.HelpOutline, "Ajuda rápida") { vm.openSheet.value = Sheet.HELP }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ZentraColors.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = ZentraColors.accent2)
        Spacer(Modifier.padding(6.dp))
        Text(label, color = ZentraColors.textMain, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------------- CONFIGURACOES ----------------

@Composable
fun SettingsSheet(vm: EditorViewModel) {
    val tick by vm.sceneVersion.collectAsState()
    @Suppress("UNUSED_EXPRESSION")
    tick
    val s = vm.settings
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        SectionTitle("QUALIDADE GRÁFICA")
        val qLabels = listOf("Auto", "Baixa", "Média", "Alta")
        val qValues = listOf(Quality.AUTO, Quality.LOW, Quality.MEDIUM, Quality.HIGH)
        ChipRow(qLabels, qValues.indexOf(s.quality), onSelect = { i ->
            vm.updateSettings { it.quality = qValues[i] }
        })
        Spacer(Modifier.height(8.dp))
        ToggleRow("Sombras suaves", s.shadows) { vm.updateSettings { it.shadows = !it.shadows } }
        ToggleRow("Mostrar grid", s.showGrid) { vm.updateSettings { it.showGrid = !it.showGrid } }
        ToggleRow("Estatísticas (FPS)", s.showStats) { vm.updateSettings { it.showStats = !it.showStats } }
        Spacer(Modifier.height(8.dp))
        SectionTitle("DESEMPENHO")
        ZentraSliderRow(
            "Limite de polígonos (importação)", s.polyLimit.toFloat(), 10_000f..500_000f,
            { vm.updateSettings { st -> st.polyLimit = it.toInt() } },
            format = { "${(it / 1000).toInt()}k tris" }
        )
        ZentraSliderRow(
            "Sensibilidade de órbita", s.orbitSensitivity, 0.2f..3f,
            { vm.updateSettings { st -> st.orbitSensitivity = it } }
        )
        SectionTitle("AUTOSAVE")
        ZentraSliderRow(
            "Autosave a cada (min)", s.autosaveMinutes.toFloat(), 1f..15f,
            { vm.updateSettings { st -> st.autosaveMinutes = it.toInt() } },
            format = { "${it.toInt()} min" }
        )
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------- IMPORT / EXPORT ----------------

@Composable
fun ImportSheet(vm: EditorViewModel, onPickFile: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        SectionTitle("IMPORTAR MODELO 3D")
        Text(
            "Formatos suportados: GLB, GLTF, OBJ e STL.\nO modelo será adicionado à cena atual.",
            color = ZentraColors.textDim, fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))
        ZentraSecondaryButton(
            "Escolher arquivo", onClick = onPickFile,
            icon = Icons.Default.Download, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ExportSheet(onExport: (ext: String, selectionOnly: Boolean) -> Unit) {
    var fmt by remember { mutableStateOf(1) } // 0 glb 1 gltf? padrao glb
    var scope by remember { mutableStateOf(0) } // 0 tudo, 1 selecao
    val exts = listOf("glb", "gltf", "obj", "stl")
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        SectionTitle("FORMATO")
        ChipRow(listOf("GLB", "GLTF", "OBJ", "STL"), fmt, onSelect = { fmt = it })
        Spacer(Modifier.height(8.dp))
        SectionTitle("ESCOPO")
        ChipRow(listOf("Cena inteira", "Somente seleção"), scope, onSelect = { scope = it })
        Spacer(Modifier.height(12.dp))
        ZentraSecondaryButton(
            "Exportar ${exts[fmt].uppercase()}", onClick = { onExport(exts[fmt], scope == 1) },
            icon = Icons.Default.Upload, modifier = Modifier.fillMaxWidth()
        )
        Text(
            "GLB/GLTF incluem materiais e texturas. OBJ/STL exportam geometria.",
            color = ZentraColors.textDim, fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------- OBJETOS ----------------

@Composable
fun ObjectsSheet(vm: EditorViewModel) {
    val tick by vm.sceneVersion.collectAsState()
    @Suppress("UNUSED_EXPRESSION")
    tick
    val scene = vm.lockedScene()
    val selection by vm.selection.collectAsState()
    val objects = scene?.objects ?: emptyList()

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        SectionTitle("OBJETOS (${objects.size})")
        if (objects.isEmpty()) {
            Text("Cena vazia.", color = ZentraColors.textDim, fontSize = 13.sp)
        } else {
            LazyColumn(Modifier.fillMaxWidth().height(320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(objects, key = { it.id }) { o ->
                    val sel = o.id in selection
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) ZentraColors.surface3 else ZentraColors.surface2)
                            .clickable { vm.selectObject(o.id) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(typeIcon(o.type), null, tint = ZentraColors.accent2)
                        Spacer(Modifier.padding(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                o.name, color = if (o.visible) ZentraColors.textMain else ZentraColors.textDim,
                                fontSize = 14.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Text(typeLabel(o.type), color = ZentraColors.textDim, fontSize = 11.sp)
                        }
                        IconButton(onClick = { vm.toggleVisibility(o.id) }) {
                            Icon(
                                if (o.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                "Visibilidade", tint = ZentraColors.textDim
                            )
                        }
                        IconButton(onClick = { vm.selectObject(o.id); vm.deleteSelected() }) {
                            Icon(Icons.Default.Delete, "Excluir", tint = ZentraColors.danger)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun typeIcon(t: ObjectType): ImageVector = when (t) {
    ObjectType.MESH -> Icons.Default.Category
    ObjectType.TEXT -> Icons.Default.TextFields
    ObjectType.EMPTY -> Icons.Default.CenterFocusWeak
    ObjectType.LIGHT -> Icons.Default.Lightbulb
    ObjectType.CAMERA -> Icons.Default.CameraAlt
}

private fun typeLabel(t: ObjectType): String = when (t) {
    ObjectType.MESH -> "Malha"
    ObjectType.TEXT -> Icons.Default.TextFields.let { "Texto" }
    ObjectType.EMPTY -> "Empty"
    ObjectType.LIGHT -> "Luz"
    ObjectType.CAMERA -> "Câmera"
}

// ---------------- AVANCADO ----------------

@Composable
fun AdvancedSheet(vm: EditorViewModel) {
    val tick by vm.sceneVersion.collectAsState()
    @Suppress("UNUSED_EXPRESSION")
    tick
    val scene = vm.lockedScene()
    val selection by vm.selection.collectAsState()
    val obj = scene?.find(selection.lastOrNull())
    var showParents by remember { mutableStateOf(false) }
    var cloneMirror by remember { mutableStateOf(true) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        if (obj == null) {
            Text("Nenhum objeto selecionado.", color = ZentraColors.textDim, fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))
            return
        }
        SectionTitle("INFORMAÇÕES")
        val tris = obj.mesh?.triCount() ?: 0
        val verts = obj.mesh?.vertices?.size ?: 0
        Text("Tipo: ${typeLabel(obj.type)}", color = ZentraColors.textDim, fontSize = 13.sp)
        if (obj.mesh != null) {
            Text("Vértices: $verts · Triângulos: $tris", color = ZentraColors.textDim, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))

        if (obj.type == ObjectType.TEXT) {
            SectionTitle("TEXTO")
            var textVal by remember(obj.id) { mutableStateOf(obj.text ?: "") }
            ZentraTextField(value = textVal, onValueChange = { textVal = it }, label = "Conteúdo")
            Spacer(Modifier.height(8.dp))
            ZentraSecondaryButton("Aplicar texto", onClick = { vm.setActiveText(textVal) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }

        if (obj.mesh != null) {
            SectionTitle("MIRROR (ESPELHAR)")
            ToggleRow("Clonar (manter original)", cloneMirror) { cloneMirror = !cloneMirror }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ZentraSecondaryButton("X", onClick = { vm.mirrorSelected(0, cloneMirror) }, modifier = Modifier.weight(1f))
                ZentraSecondaryButton("Y", onClick = { vm.mirrorSelected(1, cloneMirror) }, modifier = Modifier.weight(1f))
                ZentraSecondaryButton("Z", onClick = { vm.mirrorSelected(2, cloneMirror) }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }

        SectionTitle("HIERARQUIA")
        val parentName = obj.parentId?.let { scene?.find(it)?.name } ?: "Nenhum (raiz)"
        Text("Pai atual: $parentName", color = ZentraColors.textMain, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZentraSecondaryButton(
                "Definir pai", onClick = { showParents = !showParents },
                icon = Icons.Default.Check, modifier = Modifier.weight(1f)
            )
            ZentraSecondaryButton("Soltar", onClick = { vm.clearParentOfActive() }, modifier = Modifier.weight(1f))
        }
        if (showParents) {
            Spacer(Modifier.height(8.dp))
            val candidates = scene?.objects?.filter { it.id != obj.id } ?: emptyList()
            LazyColumn(Modifier.fillMaxWidth().height(180.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(candidates, key = { it.id }) { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ZentraColors.surface2)
                            .clickable {
                                vm.setParent(obj.id, c.id)
                                showParents = false
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(c.name, color = ZentraColors.textMain, fontSize = 13.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZentraSecondaryButton("Duplicar", onClick = { vm.duplicateSelected() }, modifier = Modifier.weight(1f))
            ZentraSecondaryButton("Excluir", onClick = { vm.deleteSelected(); vm.openSheet.value = null }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------- AJUDA ----------------

@Composable
fun HelpSheet() {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        SectionTitle("FLUXO BÁSICO")
        HelpText("1. Adicionar → crie um objeto (cubo, esfera...)")
        HelpText("2. Selecionar → toque no objeto")
        HelpText("3. Mover / Girar / Escala → arraste na tela")
        HelpText("4. Editar → selecione vértices, edges ou faces e use Extrude, Bevel...")
        HelpText("5. Material → aplique cores e texturas")
        HelpText("6. Salvar → menu no topo (disquete)")
        SectionTitle("GESTOS")
        HelpText("• 1 dedo (Selecionar): orbitar a câmera")
        HelpText("• 1 dedo (Mover/Girar/Escala): manipula o objeto")
        HelpText("• 2 dedos: pinch = zoom, arrastar = pan")
        HelpText("• 3 dedos: orbitar")
        SectionTitle("DICAS")
        HelpText("• Use a trava de eixo (X/Y/Z) para movimentos precisos")
        HelpText("• Focus Selected centraliza a câmera (mira na barra)")
        HelpText("• Animar: posicione a timeline, mova o objeto e toque em +")
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HelpText(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Info, null, tint = ZentraColors.accent2, modifier = Modifier.padding(end = 8.dp))
        Text(text, color = ZentraColors.textMain, fontSize = 13.sp)
    }
}
