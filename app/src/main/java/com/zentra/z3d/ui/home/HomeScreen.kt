package com.zentra.z3d.ui.home

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zentra.z3d.core.MaterialData
import com.zentra.z3d.core.Scene
import com.zentra.z3d.io.ModelImporter
import com.zentra.z3d.projects.ProjectInfo
import com.zentra.z3d.projects.ProjectStore
import com.zentra.z3d.ui.components.ConfirmDialog
import com.zentra.z3d.ui.components.PromptDialog
import com.zentra.z3d.ui.components.ZentraPrimaryButton
import com.zentra.z3d.ui.components.ZentraSecondaryButton
import com.zentra.z3d.ui.theme.ZentraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.Image as ComposeImage

@Composable
fun HomeScreen(onOpenProject: (String) -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as Application
    val store = remember { ProjectStore(app) }
    val scope = rememberCoroutineScope()
    val hostState = remember { SnackbarHostState() }

    var projects by remember { mutableStateOf<List<ProjectInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showNew by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ProjectInfo?>(null) }
    var deleteTarget by remember { mutableStateOf<ProjectInfo?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            val list = store.listProjects()
            withContext(Dispatchers.Main) {
                projects = list
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(notice) {
        notice?.let {
            hostState.showSnackbar(it)
            notice = null
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = "Importando modelo..."
        scope.launch(Dispatchers.IO) {
            try {
                val displayName = ModelImporter.displayName(app, uri)
                val base = displayName.substringBeforeLast('.').ifEmpty { "Modelo" }.take(48)
                val scene = Scene(base)
                scene.materials.add(MaterialData(name = "Material"))
                val matId = scene.materials.first().id
                val id = store.createProjectFromScene(base, scene)
                val outcome = ModelImporter.importFromUri(app, uri, matId) { sug, bytes ->
                    store.saveTexture(id, sug, bytes)
                }.getOrThrow()
                for (m in outcome.materials) {
                    m.name = scene.uniqueMaterialName(m.name)
                    scene.materials.add(m)
                }
                for (o in outcome.objects) {
                    o.name = scene.uniqueName(o.name)
                    if (o.materialId == null || scene.materials.none { it.id == o.materialId }) {
                        o.materialId = matId
                    }
                    o.touchMesh()
                    scene.objects.add(o)
                }
                if (scene.objects.isEmpty()) throw IllegalArgumentException("Nenhum objeto no arquivo")
                store.saveProject(id, scene).getOrThrow()
                withContext(Dispatchers.Main) {
                    busy = null
                    onOpenProject(id)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    busy = null
                    notice = "Falha na importação: ${e.message}"
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(ZentraColors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // ---------- cabecalho ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(ZentraColors.gradient),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Z", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text("ZENTRA 3D", color = ZentraColors.textMain, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text(
                        "Editor 3D mobile · Zentra XR",
                        color = ZentraColors.textDim, fontSize = 13.sp
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            // ---------- acoes ----------
            ZentraPrimaryButton(
                "＋  Novo projeto",
                onClick = { showNew = true },
                icon = Icons.Default.Add,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            ZentraSecondaryButton(
                "Importar modelo",
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                icon = Icons.Default.Download,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))

            // ---------- recentes ----------
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "PROJETOS RECENTES",
                    color = ZentraColors.textDim, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp
                )
                Text("${projects.size}", color = ZentraColors.textDim, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))

            if (loading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ZentraColors.accent)
                }
            } else if (projects.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(ZentraColors.surface)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Category, null, tint = ZentraColors.surface3, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Nenhum projeto ainda", color = ZentraColors.textMain, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Crie seu primeiro projeto 3D",
                            color = ZentraColors.textDim, fontSize = 13.sp
                        )
                    }
                }
            } else {
                // Grid responsivo em linhas (2 colunas, sem altura estimada)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (chunk in projects.chunked(2)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            for (p in chunk) {
                                Box(Modifier.weight(1f)) {
                                    ProjectCard(
                                        info = p,
                                        store = store,
                                        onOpen = { onOpenProject(p.id) },
                                        onRename = { renameTarget = p },
                                        onDuplicate = {
                                            scope.launch(Dispatchers.IO) {
                                                val r = store.duplicateProject(p.id)
                                                withContext(Dispatchers.Main) {
                                                    notice = if (r.isSuccess) "Projeto duplicado" else "Falha: ${r.exceptionOrNull()?.message}"
                                                    refresh()
                                                }
                                            }
                                        },
                                        onDelete = { deleteTarget = p }
                                    )
                                }
                            }
                            if (chunk.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        SnackbarHost(hostState, Modifier.align(Alignment.BottomCenter))

        if (busy != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(ZentraColors.bg.copy(alpha = 0.7f)),
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

        if (showNew) {
            PromptDialog(
                title = "Novo projeto", initial = "MeuProjeto", confirmText = "Criar",
                onConfirm = { name ->
                    showNew = false
                    busy = "Criando projeto..."
                    scope.launch(Dispatchers.IO) {
                        try {
                            val id = store.createProject(name.trim().ifEmpty { "MeuProjeto" })
                            withContext(Dispatchers.Main) {
                                busy = null
                                onOpenProject(id)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                busy = null
                                notice = "Falha ao criar: ${e.message}"
                            }
                        }
                    }
                },
                onDismiss = { showNew = false }
            )
        }
        renameTarget?.let { t ->
            PromptDialog(
                title = "Renomear projeto", initial = t.name, confirmText = "Renomear",
                onConfirm = { name ->
                    renameTarget = null
                    scope.launch(Dispatchers.IO) {
                        val r = store.renameProject(t.id, name.trim().ifEmpty { t.name })
                        withContext(Dispatchers.Main) {
                            if (r.isFailure) notice = "Falha: ${r.exceptionOrNull()?.message}"
                            refresh()
                        }
                    }
                },
                onDismiss = { renameTarget = null }
            )
        }
        deleteTarget?.let { t ->
            ConfirmDialog(
                title = "Excluir projeto?",
                message = "\"${t.name}\" será apagado permanentemente.",
                confirmText = "Excluir",
                danger = true,
                onConfirm = {
                    deleteTarget = null
                    scope.launch(Dispatchers.IO) {
                        val r = store.deleteProject(t.id)
                        withContext(Dispatchers.Main) {
                            notice = if (r.isSuccess) "Projeto excluído" else "Falha: ${r.exceptionOrNull()?.message}"
                            refresh()
                        }
                    }
                },
                onDismiss = { deleteTarget = null }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCard(
    info: ProjectInfo,
    store: ProjectStore,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var thumb by remember(info.id) { mutableStateOf<Bitmap?>(null) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(info.id) {
        withContext(Dispatchers.IO) {
            val bmp = store.loadThumbnail(info.id)
            withContext(Dispatchers.Main) { thumb = bmp }
        }
    }
    val dateStr = remember(info.updatedAt) {
        try {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(info.updatedAt))
        } catch (_: Exception) { "" }
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ZentraColors.surface)
            .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(ZentraColors.surface2)
        ) {
            val bmp = thumb
            if (bmp != null) {
                ComposeImage(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Category, null, tint = ZentraColors.surface3, modifier = Modifier.size(40.dp))
                }
            }
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, "Opções", tint = Color.White)
                }
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    modifier = Modifier.background(ZentraColors.surface2)
                ) {
                    DropdownMenuItem(text = { Text("Abrir", color = ZentraColors.textMain) }, onClick = { menu = false; onOpen() })
                    DropdownMenuItem(text = { Text("Renomear", color = ZentraColors.textMain) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Duplicar", color = ZentraColors.textMain) }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("Excluir", color = ZentraColors.danger) }, onClick = { menu = false; onDelete() })
                }
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                info.name, color = ZentraColors.textMain, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(dateStr, color = ZentraColors.textDim, fontSize = 11.sp)
        }
    }
}
