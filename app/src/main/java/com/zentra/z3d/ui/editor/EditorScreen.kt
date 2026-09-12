package com.zentra.z3d.ui.editor

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zentra.z3d.ui.components.ConfirmDialog
import com.zentra.z3d.ui.components.PromptDialog
import com.zentra.z3d.ui.editor.sheets.AddObjectSheet
import com.zentra.z3d.ui.editor.sheets.AdvancedSheet
import com.zentra.z3d.ui.editor.sheets.AnimationBar
import com.zentra.z3d.ui.editor.sheets.CameraSheet
import com.zentra.z3d.ui.editor.sheets.EditModePanel
import com.zentra.z3d.ui.editor.sheets.ExportSheet
import com.zentra.z3d.ui.editor.sheets.HelpSheet
import com.zentra.z3d.ui.editor.sheets.ImportSheet
import com.zentra.z3d.ui.editor.sheets.LightSheet
import com.zentra.z3d.ui.editor.sheets.MaterialSheet
import com.zentra.z3d.ui.editor.sheets.MenuSheet
import com.zentra.z3d.ui.editor.sheets.ObjectsSheet
import com.zentra.z3d.ui.editor.sheets.PropertiesSheet
import com.zentra.z3d.ui.editor.sheets.SettingsSheet
import com.zentra.z3d.ui.theme.ZentraColors

class EditorVMFactory(
    private val app: Application,
    private val projectId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return EditorViewModel(app, projectId) as T
    }
}

@Composable
fun EditorScreenHost(projectId: String, session: Int, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as Application
    val vm: EditorViewModel = viewModel(
        key = "editor_${projectId}_$session",
        factory = EditorVMFactory(app, projectId)
    )
    EditorScreen(vm = vm, onExit = onExit)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel, onExit: () -> Unit) {
    val hostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.snack.collect { msg -> hostState.showSnackbar(msg) }
    }

    val openSheet by vm.openSheet.collectAsState()
    val tool by vm.tool.collectAsState()
    val mode by vm.objMode.collectAsState()
    val busy by vm.isBusy.collectAsState()
    val pendingImport by vm.pendingImport.collectAsState()
    val recovery by vm.recoveryAvailable.collectAsState()
    val dirty by vm.dirty.collectAsState()
    val projName by vm.projectName.collectAsState()

    var showRenameProject by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var exportSelOnly by remember { mutableStateOf(false) }

    fun requestExit() {
        if (vm.openSheet.value != null) {
            vm.openSheet.value = null
            return
        }
        if (dirty) showExitDialog = true
        else onExit()
    }
    BackHandler { requestExit() }

    // ---------- launchers de arquivo ----------
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.openSheet.value = null
            vm.importModel(uri)
        }
    }
    val textureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importTexture(uri)
    }
    fun doExport(uri: android.net.Uri?, ext: String) {
        if (uri != null) {
            vm.openSheet.value = null
            vm.exportModel(uri, ext, exportSelOnly)
        }
    }
    val exportGlb = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("model/gltf-binary")) { doExport(it, "glb") }
    val exportGltf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("model/gltf+json")) { doExport(it, "gltf") }
    val exportObj = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { doExport(it, "obj") }
    val exportStl = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { doExport(it, "stl") }

    Box(Modifier.fillMaxSize().background(ZentraColors.bg)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    MainToolbar(
                        vm = vm, vertical = true,
                        modifier = Modifier.statusBarsPadding().navigationBarsPadding()
                    )
                    Column(Modifier.weight(1f).fillMaxSize()) {
                        Box(Modifier.statusBarsPadding()) {
                            EditorTopBar(
                                vm = vm,
                                onBack = { requestExit() },
                                onRenameProject = { showRenameProject = true }
                            )
                        }
                        ViewportWithOverlays(vm = vm, modifier = Modifier.weight(1f).fillMaxWidth())
                        if (tool == Tool.ANIMATE) AnimationBar(vm = vm)
                        if (mode == ObjMode.EDIT) EditModePanel(vm = vm)
                        else ContextBar(vm = vm, modifier = Modifier.navigationBarsPadding())
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.statusBarsPadding()) {
                        EditorTopBar(
                            vm = vm,
                            onBack = { requestExit() },
                            onRenameProject = { showRenameProject = true }
                        )
                    }
                    ViewportWithOverlays(vm = vm, modifier = Modifier.weight(1f).fillMaxWidth())
                    if (tool == Tool.ANIMATE) AnimationBar(vm = vm)
                    if (mode == ObjMode.EDIT) EditModePanel(vm = vm)
                    else ContextBar(vm = vm)
                    Box(Modifier.navigationBarsPadding()) {
                        MainToolbar(vm = vm, vertical = false)
                    }
                }
            }
        }

        // ---------- bottom sheets ----------
        if (openSheet != null) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { vm.openSheet.value = null },
                sheetState = sheetState,
                containerColor = ZentraColors.surface,
                scrimColor = ZentraColors.bg.copy(alpha = 0.6f)
            ) {
                when (openSheet) {
                    Sheet.ADD -> AddObjectSheet(vm)
                    Sheet.PROPS -> PropertiesSheet(vm, onEditMaterial = { vm.openSheet.value = Sheet.MATERIAL })
                    Sheet.MATERIAL -> MaterialSheet(vm, onPickTexture = { textureLauncher.launch(arrayOf("image/*")) })
                    Sheet.LIGHT -> LightSheet(vm)
                    Sheet.CAMERA -> CameraSheet(vm)
                    Sheet.MENU -> MenuSheet(vm, onPickImport = { importLauncher.launch(arrayOf("*/*")) })
                    Sheet.SETTINGS -> SettingsSheet(vm)
                    Sheet.IMPORT -> ImportSheet(vm, onPickFile = { importLauncher.launch(arrayOf("*/*")) })
                    Sheet.EXPORT -> ExportSheet(onExport = { ext, selOnly ->
                        exportSelOnly = selOnly
                        when (ext) {
                            "glb" -> exportGlb.launch("modelo.glb")
                            "gltf" -> exportGltf.launch("modelo.gltf")
                            "obj" -> exportObj.launch("modelo.obj")
                            else -> exportStl.launch("modelo.stl")
                        }
                    })
                    Sheet.OBJECTS -> ObjectsSheet(vm)
                    Sheet.ADVANCED -> AdvancedSheet(vm)
                    Sheet.HELP -> HelpSheet()
                    null -> {}
                }
            }
        }

        // ---------- dialogos ----------
        if (showRenameProject) {
            PromptDialog(
                title = "Renomear projeto", initial = projName, confirmText = "Renomear",
                onConfirm = { vm.renameProject(it); showRenameProject = false },
                onDismiss = { showRenameProject = false }
            )
        }
        if (showExitDialog) {
            ConfirmDialog(
                title = "Sair do editor?",
                message = "Há alterações não salvas. Deseja salvar antes de sair?",
                confirmText = "Salvar e sair",
                onConfirm = { vm.save(mostrarMsg = false); showExitDialog = false; onExit() },
                onDismiss = { showExitDialog = false }
            )
            // Opcao extra: sair sem salvar (toque fora = cancelar)
            // Implementada como segundo passo via snackbar? Simplificacao: botao dedicado abaixo
        }
        if (showExitDialog) {
            Box(
                Modifier.fillMaxSize().padding(bottom = 120.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    "Sair sem salvar",
                    color = ZentraColors.danger, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(ZentraColors.surface2)
                        .clickable {
                            vm.discardChanges()
                            showExitDialog = false
                            onExit()
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }
        pendingImport?.let { oc ->
            ConfirmDialog(
                title = "Modelo denso",
                message = "${oc.triCount} triângulos (limite ${vm.settings.polyLimit}). " +
                        "Deseja simplificar a malha na importação? " +
                        (oc.warnings.firstOrNull() ?: ""),
                confirmText = "Simplificar",
                onConfirm = { vm.confirmPendingImport(true) },
                onDismiss = { vm.cancelPendingImport() }
            )
            Box(
                Modifier.fillMaxSize().padding(bottom = 120.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    "Importar mesmo assim",
                    color = ZentraColors.accent2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(ZentraColors.surface2)
                        .clickable { vm.confirmPendingImport(false) }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }
        if (recovery) {
            ConfirmDialog(
                title = "Recuperar trabalho?",
                message = "Encontramos um autosave mais recente que o último salvamento.",
                confirmText = "Recuperar",
                onConfirm = { vm.acceptRecovery() },
                onDismiss = { vm.dismissRecovery() }
            )
        }
        if (busy != null) {
            Box(
                Modifier.fillMaxSize()
                    .background(ZentraColors.bg.copy(alpha = 0.7f))
                    .clickable(enabled = false, onClick = {}),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(ZentraColors.surface)
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = ZentraColors.accent)
                    Spacer(Modifier.height(12.dp))
                    Text(busy!!, color = ZentraColors.textMain, fontSize = 14.sp)
                }
            }
        }

        SnackbarHost(hostState = hostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** Viewport 3D com overlays (stats, dica, botao de propriedades). */
@Composable
fun ViewportWithOverlays(vm: EditorViewModel, modifier: Modifier = Modifier) {
    val tick by vm.sceneVersion.collectAsState()
    @Suppress("UNUSED_EXPRESSION")
    tick
    val stats by vm.stats.collectAsState()
    val selection by vm.selection.collectAsState()
    val showStats = vm.settings.showStats

    Box(modifier.background(ZentraColors.bg)) {
        ViewportPanel(vm = vm, modifier = Modifier.fillMaxSize())
        if (showStats) {
            Text(
                "${stats.fps.toInt()} fps · ${stats.tris} tris · ${stats.calls} calls",
                color = ZentraColors.textDim,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ZentraColors.bg.copy(alpha = 0.65f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Text(
            hintFor(vm),
            color = ZentraColors.textDim,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ZentraColors.bg.copy(alpha = 0.65f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        if (selection.isNotEmpty()) {
            SmallFloatingActionButton(
                onClick = { vm.openSheet.value = Sheet.PROPS },
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                containerColor = ZentraColors.accent,
                contentColor = androidx.compose.ui.graphics.Color.White
            ) {
                Icon(Icons.Default.Tune, "Propriedades")
            }
        }
    }
}
