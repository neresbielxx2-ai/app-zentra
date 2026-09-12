package com.zentra.z3d.ui.editor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zentra.z3d.animation.AnimationEngine
import com.zentra.z3d.core.*
import com.zentra.z3d.io.ModelExporter
import com.zentra.z3d.io.ModelImporter
import com.zentra.z3d.projects.ProjectStore
import com.zentra.z3d.render.CameraController
import com.zentra.z3d.render.Picking
import com.zentra.z3d.render.RenderBridge
import com.zentra.z3d.render.RenderStats
import com.zentra.z3d.render.ZentraRenderer
import com.zentra.z3d.settings.AppSettings
import com.zentra.z3d.settings.PerformanceManager
import com.zentra.z3d.settings.Quality
import com.zentra.z3d.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

enum class Tool { SELECT, MOVE, ROTATE, SCALE, EDIT, ADD, MATERIAL, LIGHT, CAMERA, ANIMATE }
enum class ObjMode { OBJECT, EDIT }
enum class EditComp { VERTEX, EDGE, FACE }
enum class AxisLock { ALL, X, Y, Z }
enum class Sheet { ADD, PROPS, MATERIAL, LIGHT, CAMERA, MENU, SETTINGS, IMPORT, EXPORT, OBJECTS, ADVANCED, HELP }

class EditorViewModel(app: Application, projectIdArg: String) : AndroidViewModel(app) {

    var projectId: String = projectIdArg
        private set

    val store = ProjectStore(app)
    val bridge = RenderBridge()
    val camera = CameraController()
    val undoRedo = UndoRedo()
    val anim = AnimationEngine()
    var rendererRef: ZentraRenderer? = null

    var settings: AppSettings = SettingsStore.load(app)
        private set

    // ---------- estado observavel ----------
    val sceneVersion = MutableStateFlow(0)
    val tool = MutableStateFlow(Tool.SELECT)
    val objMode = MutableStateFlow(ObjMode.OBJECT)
    val editComp = MutableStateFlow(EditComp.VERTEX)
    val selection = MutableStateFlow<Set<String>>(emptySet())
    val editSel = MutableStateFlow<Set<Int>>(emptySet())
    val editEdgesSel = MutableStateFlow<Set<Pair<Int, Int>>>(emptySet())
    val axisLock = MutableStateFlow(AxisLock.ALL)
    val openSheet = MutableStateFlow<Sheet?>(null)
    val animTime = MutableStateFlow(0L)
    val animPlaying = MutableStateFlow(false)
    val animDuration = MutableStateFlow(4000L)
    val isBusy = MutableStateFlow<String?>(null)
    val snack = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val stats = MutableStateFlow(RenderStats())
    val projectName = MutableStateFlow("Untitled")
    val dirty = MutableStateFlow(false)
    val canUndo = MutableStateFlow(false)
    val canRedo = MutableStateFlow(false)
    val effectiveQuality = MutableStateFlow(Quality.MEDIUM)
    val pendingImport = MutableStateFlow<ModelImporter.Outcome?>(null)
    val recoveryAvailable = MutableStateFlow(false)
    val loaded = MutableStateFlow(false)

    private var lastEditMs = 0L
    private var lastAutosaveMs = 0L
    private var animJob: Job? = null
    private var activeMatId: String? = null

    init {
        bridge.camera = camera
        effectiveQuality.value = PerformanceManager.effectiveQuality(settings, app)
        viewModelScope.launch(Dispatchers.IO) {
            val scene = try {
                store.loadProject(projectId).getOrThrow()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { say("Erro ao abrir: ${e.message}") }
                Scene.newDefault("Recuperado")
            }
            // Texturas para memoria
            for (mat in scene.materials) {
                mat.textureFile?.let { tf ->
                    store.loadTextureBitmap(projectId, tf)?.let { bmp ->
                        bridge.textures.setBitmap("file:$tf", bmp)
                    }
                }
            }
            val recovered = store.loadAutosaveIfNewer(projectId)
            withContext(Dispatchers.Main) {
                synchronized(bridge.lock) {
                    bridge.scene = scene
                    bridge.camera = camera
                }
                projectName.value = scene.name
                animDuration.value = scene.animation.durationMs
                applySettingsToBridge()
                syncBridgeSelection()
                bump()
                loaded.value = true
                if (recovered != null) recoveryAvailable.value = true
                startAutosaveLoop()
            }
        }
    }

    // ---------- utilidades ----------

    fun say(msg: String) { snack.tryEmit(msg) }

    fun lockedScene(): Scene? = synchronized(bridge.lock) { bridge.scene }

    private inline fun <T> withScene(block: (Scene) -> T): T? {
        val s = synchronized(bridge.lock) { bridge.scene } ?: return null
        return synchronized(bridge.lock) { block(s) }
    }

    fun bump() {
        syncBridgeSelection()
        canUndo.value = undoRedo.canUndo()
        canRedo.value = undoRedo.canRedo()
        sceneVersion.value++
    }

    fun pushUndo() {
        withScene { undoRedo.push(it) }
        canUndo.value = undoRedo.canUndo()
        canRedo.value = undoRedo.canRedo()
    }

    fun markDirty() {
        dirty.value = true
        lastEditMs = System.currentTimeMillis()
    }

    fun activeObject(): SceneObject? {
        val id = selection.value.lastOrNull() ?: return null
        return withScene { it.find(id) }
    }

    fun activeMaterial(): MaterialData? {
        return withScene { s ->
            activeMatId?.let { id -> s.materials.find { m -> m.id == id } }
                ?: activeObject()?.let { s.materialOf(it) }
                ?: s.materials.firstOrNull()
        }
    }

    fun setActiveMaterial(id: String) {
        activeMatId = id
        bump()
    }

    fun syncBridgeSelection() {
        synchronized(bridge.lock) {
            bridge.selectedIds = selection.value
            bridge.activeId = selection.value.lastOrNull()
            bridge.editMode = objMode.value == ObjMode.EDIT
            bridge.editComponent = editComp.value.ordinal
            bridge.editSelection = editSel.value
            bridge.editEdges = editEdgesSel.value
            bridge.toolGizmo = when (tool.value) {
                Tool.MOVE -> 1; Tool.ROTATE -> 2; Tool.SCALE -> 3; else -> 0
            }
            bridge.axisLock = axisLock.value.ordinal
        }
    }

    private fun applySettingsToBridge() {
        synchronized(bridge.lock) {
            bridge.showGrid = settings.showGrid
            bridge.shadows = settings.shadows
            bridge.quality = effectiveQuality.value
        }
    }

    // ---------- ferramentas / modos ----------

    fun setTool(t: Tool) {
        tool.value = t
        when (t) {
            Tool.EDIT -> {
                if (selection.value.isEmpty()) {
                    say("Selecione um objeto para editar")
                    tool.value = Tool.SELECT
                    return
                }
                val ok = withScene { s ->
                    val o = s.find(selection.value.lastOrNull())
                    o?.mesh != null && (o.type == ObjectType.MESH || o.type == ObjectType.TEXT)
                } == true
                if (!ok) {
                    say("Edit Mode funciona em malhas")
                    tool.value = Tool.SELECT
                    return
                }
                objMode.value = ObjMode.EDIT
                editSel.value = emptySet()
                editEdgesSel.value = emptySet()
            }
            Tool.ADD -> openSheet.value = Sheet.ADD
            Tool.MATERIAL -> openSheet.value = Sheet.MATERIAL
            Tool.LIGHT -> openSheet.value = Sheet.LIGHT
            Tool.CAMERA -> openSheet.value = Sheet.CAMERA
            Tool.ANIMATE -> { /* timeline visivel na tela */ }
            else -> objMode.value = ObjMode.OBJECT
        }
        if (t != Tool.EDIT && objMode.value == ObjMode.EDIT &&
            t != Tool.MOVE && t != Tool.ROTATE && t != Tool.SCALE && t != Tool.SELECT
        ) {
            objMode.value = ObjMode.OBJECT
        }
        bump()
    }

    fun exitEditMode() {
        objMode.value = ObjMode.OBJECT
        tool.value = Tool.SELECT
        editSel.value = emptySet()
        editEdgesSel.value = emptySet()
        bump()
    }

    fun setEditComp(c: EditComp) {
        editComp.value = c
        editSel.value = emptySet()
        editEdgesSel.value = emptySet()
        bump()
    }

    // ---------- selecao (viewport) ----------

    fun tapSelect(ndcX: Float, ndcY: Float) {
        val s = lockedScene() ?: return
        val aspect: Float
        val threshold: Float
        synchronized(bridge.lock) {
            aspect = bridge.viewportW.toFloat() / max(1, bridge.viewportH).toFloat()
            threshold = camera.worldPerPixel(bridge.viewportH.toFloat()) * 22f
        }
        val ray = synchronized(bridge.lock) { camera.rayFor(ndcX, ndcY, aspect) }
        if (objMode.value == ObjMode.EDIT) {
            val id = selection.value.lastOrNull() ?: return
            synchronized(bridge.lock) {
                val obj = s.find(id)
                val mesh = obj?.mesh
                if (obj == null || mesh == null) return
                val wm = s.worldMatrix(obj)
                when (editComp.value) {
                    EditComp.VERTEX -> {
                        val v = Picking.pickVertex(mesh, wm, ray, threshold)
                        editSel.value = if (v >= 0) {
                            val cur = editSel.value.toMutableSet()
                            if (v in cur) cur.remove(v) else cur.add(v)
                            cur
                        } else emptySet()
                    }
                    EditComp.EDGE -> {
                        val e = Picking.pickEdge(mesh, wm, ray, threshold)
                        editEdgesSel.value = if (e != null) {
                            val cur = editEdgesSel.value.toMutableSet()
                            if (e in cur) cur.remove(e) else cur.add(e)
                            cur
                        } else emptySet()
                    }
                    EditComp.FACE -> {
                        val f = Picking.pickFace(mesh, wm, ray)
                        editSel.value = if (f >= 0) {
                            val cur = editSel.value.toMutableSet()
                            if (f in cur) cur.remove(f) else cur.add(f)
                            cur
                        } else emptySet()
                    }
                }
            }
            bump()
            return
        }
        val hit = synchronized(bridge.lock) { Picking.pickObject(s, ray) }
        if (hit != null) {
            selection.value = setOf(hit.objectId)
        } else {
            selection.value = emptySet()
        }
        bump()
    }

    fun selectObject(id: String) {
        selection.value = setOf(id)
        bump()
    }

    fun clearSelection() {
        selection.value = emptySet()
        bump()
    }

    // ---------- manipulacao por arrasto ----------

    fun beginStroke() = pushUndo()

    fun endStroke(recomputeNormals: Boolean = false) {
        if (recomputeNormals) {
            withScene { s ->
                selection.value.forEach { id ->
                    val o = s.find(id)
                    if (o != null) {
                        o.mesh?.computeNormals()
                        o.touchMesh()
                    }
                }
            }
        }
        markDirty()
        bump()
    }

    /** Arrastar na viewport com ferramenta ativa. dx/dy em pixels (dy positivo = para baixo). */
    fun onDrag(dxPx: Float, dyPx: Float) {
        if (selection.value.isEmpty()) return
        val wpp: Float = synchronized(bridge.lock) {
            camera.worldPerPixel(bridge.viewportH.toFloat())
        }
        val lock = axisLock.value
        withScene { s ->
            if (objMode.value == ObjMode.EDIT) {
                dragEditSelection(s, dxPx, dyPx, wpp, lock)
            } else {
                when (tool.value) {
                    Tool.MOVE -> {
                        val (right, up) = synchronized(bridge.lock) { camera.basis() }
                        val delta = right * (dxPx * wpp) + up * (-dyPx * wpp)
                        for (id in selection.value) {
                            val o = s.find(id) ?: continue
                            if (o.locked) continue
                            val p = o.transform.position
                            when (lock) {
                                AxisLock.ALL -> o.transform.position = p + delta
                                AxisLock.X -> p.x += delta.x
                                AxisLock.Y -> p.y += delta.y
                                AxisLock.Z -> p.z += delta.z
                            }
                        }
                    }
                    Tool.ROTATE -> {
                        for (id in selection.value) {
                            val o = s.find(id) ?: continue
                            if (o.locked) continue
                            val r = o.transform.rotation
                            when (lock) {
                                AxisLock.ALL -> {
                                    r.y += dxPx * 0.4f
                                    r.x += dyPx * 0.4f
                                }
                                AxisLock.X -> r.x += (dxPx + dyPx) * 0.3f
                                AxisLock.Y -> r.y += (dxPx + dyPx) * 0.3f
                                AxisLock.Z -> r.z += (dxPx - dyPx) * 0.3f
                            }
                        }
                    }
                    Tool.SCALE -> {
                        val f = 1f - dyPx * 0.006f + dxPx * 0.002f
                        val fc = f.coerceIn(0.9f, 1.1f)
                        for (id in selection.value) {
                            val o = s.find(id) ?: continue
                            if (o.locked) continue
                            val sc = o.transform.scale
                            fun cl(v: Float) = v.coerceIn(0.01f, 500f)
                            when (lock) {
                                AxisLock.ALL -> o.transform.scale = Vec3(cl(sc.x * fc), cl(sc.y * fc), cl(sc.z * fc))
                                AxisLock.X -> sc.x = cl(sc.x * fc)
                                AxisLock.Y -> sc.y = cl(sc.y * fc)
                                AxisLock.Z -> sc.z = cl(sc.z * fc)
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
        bump()
    }

    private fun dragEditSelection(s: Scene, dxPx: Float, dyPx: Float, wpp: Float, lock: AxisLock) {
        val id = selection.value.lastOrNull() ?: return
        val obj = s.find(id) ?: return
        val mesh = obj.mesh ?: return
        val (right, up) = synchronized(bridge.lock) { camera.basis() }
        val worldDelta = right * (dxPx * wpp) + up * (-dyPx * wpp)
        val wm = s.worldMatrix(obj)
        val inv = wm.inverted() ?: return
        val local = inv.transformDir(worldDelta)
        val verts = targetVerts(mesh)
        if (verts.isEmpty()) return
        for (vi in verts) {
            if (vi !in mesh.vertices.indices) continue
            val v = mesh.vertices[vi]
            when (lock) {
                AxisLock.ALL -> { v.x += local.x; v.y += local.y; v.z += local.z }
                AxisLock.X -> v.x += local.x
                AxisLock.Y -> v.y += local.y
                AxisLock.Z -> v.z += local.z
            }
        }
        obj.touchMesh()
    }

    private fun targetVerts(mesh: MeshData): Set<Int> {
        return when (editComp.value) {
            EditComp.VERTEX -> editSel.value
            EditComp.EDGE -> editEdgesSel.value.flatMap { listOf(it.first, it.second) }.toSet()
            EditComp.FACE -> editSel.value.flatMap { fi ->
                if (fi in mesh.faces.indices) mesh.faces[fi].indices else emptyList()
            }.toSet()
        }
    }

    // ---------- objetos ----------

    fun addPrimitive(kind: PrimitiveKind) {
        withScene { s ->
            pushUndo()
            val matId = s.defaultMaterial().id
            val obj = Primitives.createObject(kind, matId)
            obj.name = s.uniqueName(obj.name)
            if (obj.type == ObjectType.MESH || obj.type == ObjectType.TEXT) obj.materialId = matId
            // Posiciona a frente da camera
            val target = camera.target
            if (obj.type == ObjectType.MESH || obj.type == ObjectType.TEXT || obj.type == ObjectType.EMPTY) {
                obj.transform.position = Vec3(target.x, max(0f, target.y), target.z)
            }
            s.objects.add(obj)
            selection.value = setOf(obj.id)
            markDirty()
        }
        openSheet.value = null
        if (tool.value == Tool.ADD) tool.value = Tool.SELECT
        bump()
        say("Objeto criado")
    }

    fun deleteSelected() {
        val ids = selection.value
        if (ids.isEmpty()) return
        withScene { s ->
            pushUndo()
            // Remove filhos orfaos: solta parentesco
            for (o in s.objects) if (o.parentId in ids) o.parentId = null
            s.objects.removeAll { it.id in ids }
            s.animation.tracks.removeAll { it.objectId in ids }
            selection.value = emptySet()
            if (objMode.value == ObjMode.EDIT) objMode.value = ObjMode.OBJECT
            markDirty()
        }
        bump()
        say("Excluido")
    }

    fun duplicateSelected() {
        if (selection.value.isEmpty()) return
        withScene { s ->
            pushUndo()
            val copies = mutableListOf<String>()
            for (id in selection.value) {
                val o = s.find(id) ?: continue
                val c = o.deepCopy()
                c.id = newId("obj")
                c.name = s.uniqueName(o.name)
                c.transform.position = c.transform.position + Vec3(0.8f, 0f, 0.8f)
                if (c.camera?.isMain == true) c.camera?.isMain = false
                s.objects.add(c)
                copies.add(c.id)
            }
            if (copies.isNotEmpty()) selection.value = copies.toSet()
            markDirty()
        }
        bump()
        say("Duplicado")
    }

    fun renameActive(name: String) {
        val clean = name.trim().take(64)
        if (clean.isEmpty()) return
        withScene { s ->
            pushUndo()
            s.find(selection.value.lastOrNull())?.name = clean
            markDirty()
        }
        bump()
    }

    fun toggleVisibility(id: String) {
        withScene { s ->
            pushUndo()
            s.find(id)?.let { it.visible = !it.visible }
            markDirty()
        }
        bump()
    }

    fun toggleLock(id: String) {
        withScene { s ->
            s.find(id)?.let { it.locked = !it.locked }
            markDirty()
        }
        bump()
    }

    fun mirrorSelected(axis: Int, clone: Boolean) {
        val obj = activeObject() ?: return
        val mesh = withScene { s -> s.find(obj.id)?.mesh } ?: run {
            say("Selecione um objeto com malha"); return
        }
        pushUndo()
        val res = MeshOps.mirror(mesh, axis, clone)
        withScene { s -> s.find(obj.id)?.touchMesh() }
        markDirty()
        bump()
        say(res.message)
    }

    fun clearParentOfActive() {
        val id = selection.value.lastOrNull() ?: return
        withScene { s ->
            pushUndo()
            s.find(id)?.parentId = null
            markDirty()
        }
        bump()
        say("Parentesco removido")
    }

    fun setParent(childId: String, parentId: String?) {
        withScene { s ->
            if (parentId == childId) return@withScene
            // Evita ciclos
            var p = parentId?.let { s.find(it) }
            var guard = 0
            while (p != null && guard++ < 64) {
                if (p.id == childId) {
                    say("Parentesco invalido (ciclo)")
                    return@withScene
                }
                p = p.parentId?.let { s.find(it) }
            }
            pushUndo()
            s.find(childId)?.parentId = parentId
            markDirty()
        }
        bump()
    }

    // ---------- transform (painel) ----------

    fun setTransformComponent(kind: Int, axis: Int, value: Float) {
        // kind: 0 pos, 1 rot, 2 scale
        withScene { s ->
            val o = s.find(selection.value.lastOrNull()) ?: return@withScene
            val v = when (kind) {
                0 -> o.transform.position
                1 -> o.transform.rotation
                else -> o.transform.scale
            }
            val vv = if (kind == 2) value.coerceIn(0.01f, 500f) else value.coerceIn(-1000f, 1000f)
            when (axis) {
                0 -> v.x = vv; 1 -> v.y = vv; else -> v.z = vv
            }
        }
        bump()
    }

    fun commitTransformEdit() {
        markDirty()
        bump()
    }

    // ---------- edit mode ops ----------

    private fun editMesh(): Pair<SceneObject, MeshData>? {
        val id = selection.value.lastOrNull() ?: return null
        return withScene { s ->
            val o = s.find(id)
            val m = o?.mesh
            if (o != null && m != null) o to m else null
        }
    }

    private fun runEditOp(label: String, block: (MeshData) -> OpResult) {
        val (obj, mesh) = editMesh() ?: run { say("Nada selecionado"); return }
        pushUndo()
        val res = try {
            block(mesh)
        } catch (e: Exception) {
            OpResult(false, "$label falhou: ${e.message}")
        }
        withScene { s -> s.find(obj.id)?.touchMesh() }
        if (res.ok) {
            editSel.value = emptySet()
            editEdgesSel.value = emptySet()
            markDirty()
        }
        bump()
        say(res.message)
    }

    fun selectAllEdit() {
        val (_, mesh) = editMesh() ?: return
        when (editComp.value) {
            EditComp.VERTEX -> editSel.value = mesh.vertices.indices.toSet()
            EditComp.FACE -> editSel.value = mesh.faces.indices.toSet()
            EditComp.EDGE -> editEdgesSel.value = mesh.edges()
        }
        bump()
    }

    fun clearEditSelection() {
        editSel.value = emptySet()
        editEdgesSel.value = emptySet()
        bump()
    }

    fun extrudeSel(distance: Float) = runEditOp("Extrude") { MeshOps.extrudeFaces(it, editSel.value, distance) }
    fun insetSel(amount: Float) = runEditOp("Inset") { MeshOps.insetFaces(it, editSel.value, amount) }
    fun bevelSel(amount: Float) = runEditOp("Bevel") {
        val verts = targetVerts(it)
        MeshOps.bevelVertices(it, verts, amount)
    }
    fun subdivideSel() = runEditOp("Subdivide") { MeshOps.subdivideFaces(it, editSel.value.ifEmpty { null }, 1) }
    fun loopCutSel() = runEditOp("Loop Cut") { MeshOps.loopCutFaces(it, editSel.value) }
    fun mergeSel() = runEditOp("Merge") { MeshOps.mergeVertices(it, targetVerts(it)) }
    fun dissolveSel() = runEditOp("Dissolve") {
        when (editComp.value) {
            EditComp.VERTEX -> MeshOps.deleteVertices(it, editSel.value)
            EditComp.EDGE -> MeshOps.dissolveEdges(it, editEdgesSel.value)
            EditComp.FACE -> MeshOps.dissolveFaces(it, editSel.value)
        }
    }
    fun deleteElemSel() = dissolveSel()

    fun separateSel() {
        val (obj, mesh) = editMesh() ?: return
        if (editComp.value != EditComp.FACE || editSel.value.isEmpty()) {
            say("Selecione faces para separar"); return
        }
        pushUndo()
        val (newMesh, res) = MeshOps.separateFaces(mesh, editSel.value)
        if (newMesh != null) {
            withScene { s ->
                val src = s.find(obj.id)
                val novo = SceneObject(
                    name = s.uniqueName((src?.name ?: "Object") + "_part"),
                    type = ObjectType.MESH, mesh = newMesh,
                    materialId = src?.materialId,
                    transform = src?.transform?.deepCopy() ?: Transform()
                )
                s.objects.add(novo)
                selection.value = setOf(novo.id)
            }
            markDirty()
            say(res.message)
        } else say(res.message)
        editSel.value = emptySet()
        bump()
    }

    // ---------- materiais ----------

    fun createMaterial() {
        withScene { s ->
            pushUndo()
            val mat = MaterialData(name = s.uniqueMaterialName("Material"))
            // Cor inicial agradavel variada
            val palette = listOf(
                Rgba(0.48f, 0.36f, 1f, 1f), Rgba(0.22f, 0.85f, 1f, 1f),
                Rgba(1f, 0.45f, 0.55f, 1f), Rgba(0.3f, 0.9f, 0.6f, 1f),
                Rgba(1f, 0.75f, 0.3f, 1f)
            )
            mat.baseColor = palette[s.materials.size % palette.size].deepCopy()
            s.materials.add(mat)
            activeMatId = mat.id
            markDirty()
        }
        bump()
        say("Material criado")
    }

    fun deleteMaterial(id: String) {
        withScene { s ->
            if (s.materials.size <= 1) {
                say("O projeto precisa de ao menos 1 material"); return@withScene
            }
            pushUndo()
            val fallback = s.materials.first { it.id != id }.id
            for (o in s.objects) if (o.materialId == id) o.materialId = fallback
            val mat = s.materials.find { it.id == id }
            s.materials.removeAll { it.id == id }
            if (activeMatId == id) activeMatId = null
            mat?.textureFile?.let { store.deleteTextureIfUnused(projectId, it, s) }
            markDirty()
        }
        bump()
    }

    fun assignMaterialToSelected(matId: String) {
        if (selection.value.isEmpty()) {
            say("Selecione um objeto"); return
        }
        withScene { s ->
            pushUndo()
            for (id in selection.value) {
                val o = s.find(id)
                if (o != null && (o.type == ObjectType.MESH || o.type == ObjectType.TEXT)) {
                    o.materialId = matId
                }
            }
            markDirty()
        }
        bump()
        say("Material aplicado")
    }

    fun updateMaterial(id: String, block: (MaterialData) -> Unit) {
        withScene { s -> s.materials.find { it.id == id }?.let(block) }
        bump()
    }

    fun commitMaterialEdit() {
        markDirty()
        bump()
    }

    fun beginMaterialStroke() = pushUndo()

    fun importTexture(uri: Uri) {
        val mat = activeMaterial() ?: run { say("Nenhum material ativo"); return }
        isBusy.value = "Importando textura..."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalArgumentException("Nao foi possivel ler a imagem")
                if (bytes.size > 30 * 1024 * 1024) throw IllegalArgumentException("Imagem maior que 30 MB")
                val stored = store.saveTexture(projectId, "texture.png", bytes)
                    ?: throw IllegalArgumentException("Formato de imagem nao suportado")
                val bmp = store.loadTextureBitmap(projectId, stored)
                withContext(Dispatchers.Main) {
                    pushUndo()
                    withScene { s ->
                        val old = s.materials.find { it.id == mat.id }?.textureFile
                        s.materials.find { it.id == mat.id }?.textureFile = stored
                        if (bmp != null) bridge.textures.setBitmap("file:$stored", bmp)
                        if (old != null && old != stored) store.deleteTextureIfUnused(projectId, old, s)
                    }
                    markDirty()
                    bump()
                    say("Textura aplicada")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { say("Falha na textura: ${e.message}") }
            } finally {
                isBusy.value = null
            }
        }
    }

    fun removeTexture() {
        val mat = activeMaterial() ?: return
        pushUndo()
        withScene { s ->
            val old = s.materials.find { it.id == mat.id }?.textureFile
            s.materials.find { it.id == mat.id }?.textureFile = null
            if (old != null) {
                bridge.textures.remove("file:$old")
                store.deleteTextureIfUnused(projectId, old, s)
            }
            markDirty()
        }
        bump()
        say("Textura removida")
    }

    // ---------- luzes / cameras ----------

    fun updateLight(id: String, block: (LightData) -> Unit) {
        withScene { s -> s.find(id)?.light?.let(block) }
        bump()
    }

    fun commitLightEdit() {
        markDirty(); bump()
    }

    fun updateCameraObj(id: String, block: (SceneObject) -> Unit) {
        withScene { s -> s.find(id)?.let(block) }
        bump()
    }

    fun setMainCamera(id: String) {
        withScene { s ->
            pushUndo()
            for (o in s.objects) if (o.type == ObjectType.CAMERA) o.camera?.isMain = (o.id == id)
            markDirty()
        }
        bump()
        say("Camera principal definida")
    }

    fun viewThroughCamera() {
        val cam = withScene { s ->
            selection.value.lastOrNull()?.let { s.find(it) }?.takeIf { it.type == ObjectType.CAMERA }
                ?: s.mainCamera()
        } ?: run { say("Nenhuma camera no projeto"); return }
        withScene { s -> camera.matchCameraObject(cam, s) }
        say("View Camera")
    }

    // ---------- animacao ----------

    fun playPause() {
        if (anim.playing) {
            anim.playing = false
            animPlaying.value = false
            animJob?.cancel()
        } else {
            anim.playing = true
            animPlaying.value = true
            animJob?.cancel()
            animJob = viewModelScope.launch {
                var last = System.currentTimeMillis()
                while (anim.playing) {
                    delay(16)
                    val now = System.currentTimeMillis()
                    val dt = (now - last).coerceIn(0, 100)
                    last = now
                    withScene { s -> anim.tick(s, dt) }
                    animTime.value = anim.timeMs
                    sceneVersion.value++
                }
            }
        }
    }

    fun rewind() {
        anim.timeMs = 0L
        animTime.value = 0L
        withScene { s -> anim.applyEvaluated(s, anim.evaluate(s, 0L)) }
        bump()
    }

    fun seekTo(ms: Long) {
        anim.timeMs = ms.coerceIn(0L, animDuration.value)
        animTime.value = anim.timeMs
        withScene { s -> anim.applyEvaluated(s, anim.evaluate(s, anim.timeMs)) }
        bump()
    }

    fun addKey() {
        val id = selection.value.lastOrNull() ?: run { say("Selecione um objeto"); return }
        pushUndo()
        withScene { s ->
            val created = anim.addKey(s, id, anim.timeMs)
            animDuration.value = s.animation.durationMs
            markDirty()
            say(if (created) "Keyframe criado" else "Keyframe atualizado")
        }
        bump()
    }

    fun removeKey() {
        val id = selection.value.lastOrNull() ?: run { say("Selecione um objeto"); return }
        withScene { s ->
            pushUndo()
            val ok = anim.removeKeyAt(s, id, anim.timeMs)
            say(if (ok) "Keyframe removido" else "Nenhum keyframe aqui")
            if (ok) markDirty()
        }
        bump()
    }

    // ---------- undo / redo ----------

    fun undo() {
        withScene { s ->
            val prev = undoRedo.undo(s) ?: run { say("Nada para desfazer"); return@withScene }
            s.restoreFrom(prev)
            selection.value = selection.value.filter { id -> s.find(id) != null }.toSet()
            markDirty()
        }
        bump()
    }

    fun redo() {
        withScene { s ->
            val next = undoRedo.redo(s) ?: run { say("Nada para refazer"); return@withScene }
            s.restoreFrom(next)
            markDirty()
        }
        bump()
    }

    // ---------- camera / vista ----------

    fun cameraPreset(name: String) {
        synchronized(bridge.lock) { camera.preset(name) }
    }

    fun focusSelected() {
        val ids = selection.value
        if (ids.isEmpty()) {
            say("Nada selecionado"); return
        }
        withScene { s ->
            var min = Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
            var max = Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            var any = false
            for (id in ids) {
                val o = s.find(id) ?: continue
                val wm = s.worldMatrix(o)
                val mesh = o.mesh
                if (mesh != null && mesh.vertices.isNotEmpty()) {
                    for (v in mesh.vertices) {
                        val w = wm.transformPoint(v)
                        min.x = minOf(min.x, w.x); min.y = minOf(min.y, w.y); min.z = minOf(min.z, w.z)
                        max.x = maxOf(max.x, w.x); max.y = maxOf(max.y, w.y); max.z = maxOf(max.z, w.z)
                        any = true
                    }
                } else {
                    val c = wm.translationPart()
                    min.x = minOf(min.x, c.x - 0.5f); min.y = minOf(min.y, c.y - 0.5f); min.z = minOf(min.z, c.z - 0.5f)
                    max.x = maxOf(max.x, c.x + 0.5f); max.y = maxOf(max.y, c.y + 0.5f); max.z = maxOf(max.z, c.z + 0.5f)
                    any = true
                }
            }
            if (any) {
                val c = (min + max) * 0.5f
                val r = (max - min).length() * 0.5f
                camera.focus(c, r)
            }
        }
    }

    // ---------- salvar / importar / exportar ----------

    fun save(mostrarMsg: Boolean = true) {
        val s = lockedScene() ?: return
        isBusy.value = "Salvando..."
        val snapshot = synchronized(bridge.lock) { s.deepCopy() }
        viewModelScope.launch(Dispatchers.IO) {
            val res = store.saveProject(projectId, snapshot)
            // Thumbnail apos salvar
            val renderer = rendererRef
            if (renderer != null) {
                try {
                    withContext(Dispatchers.Main) {
                        renderer.thumbnailRequest = { bmp: Bitmap ->
                            viewModelScope.launch(Dispatchers.IO) {
                                store.saveThumbnail(projectId, bmp)
                                bmp.recycle()
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            withContext(Dispatchers.Main) {
                isBusy.value = null
                if (res.isSuccess) {
                    dirty.value = false
                    store.clearAutosave(projectId)
                    projectName.value = snapshot.name
                    if (mostrarMsg) say("Projeto salvo")
                } else {
                    say("Falha ao salvar: ${res.exceptionOrNull()?.message}")
                }
            }
        }
    }

    fun saveAs(newName: String) {
        val clean = newName.trim().take(64).ifEmpty { "Untitled" }
        isBusy.value = "Salvando copia..."
        val snapshot = withScene { it.deepCopy() } ?: run { isBusy.value = null; return }
        snapshot.name = clean
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newId = store.createProjectFromScene(clean, snapshot)
                // Copia texturas
                val oldTex = java.io.File(
                    getApplication<Application>().filesDir, "projects/$projectId/textures"
                )
                if (oldTex.exists()) {
                    oldTex.copyRecursively(
                        java.io.File(getApplication<Application>().filesDir, "projects/$newId/textures"),
                        overwrite = true
                    )
                }
                withContext(Dispatchers.Main) {
                    projectId = newId
                    projectName.value = clean
                    dirty.value = false
                    store.clearAutosave(projectId)
                    say("Salvo como $clean")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { say("Falha no 'Salvar como': ${e.message}") }
            } finally {
                isBusy.value = null
            }
        }
    }

    fun importModel(uri: Uri) {
        isBusy.value = "Importando modelo..."
        viewModelScope.launch(Dispatchers.IO) {
            val defaultMat = withScene { it.defaultMaterial().id }
            val outcome = ModelImporter.importFromUri(
                getApplication(), uri, defaultMat
            ) { suggested, bytes -> store.saveTexture(projectId, suggested, bytes) }
            withContext(Dispatchers.Main) {
                isBusy.value = null
                outcome.onSuccess { oc ->
                    if (oc.triCount > settings.polyLimit) {
                        pendingImport.value = oc
                    } else {
                        applyImport(oc, simplify = false)
                    }
                }.onFailure { e ->
                    say("Falha na importacao: ${e.message}")
                }
            }
        }
    }

    fun confirmPendingImport(simplify: Boolean) {
        val oc = pendingImport.value ?: return
        pendingImport.value = null
        applyImport(oc, simplify)
    }

    fun cancelPendingImport() {
        pendingImport.value = null
    }

    private fun applyImport(oc: ModelImporter.Outcome, simplify: Boolean) {
        // Pre-carrega bitmaps das texturas importadas
        viewModelScope.launch(Dispatchers.IO) {
            for (m in oc.materials) {
                m.textureFile?.let { tf ->
                    store.loadTextureBitmap(projectId, tf)?.let { bmp ->
                        bridge.textures.setBitmap("file:$tf", bmp)
                    }
                }
            }
            withContext(Dispatchers.Main) {
                withScene { s ->
                    pushUndo()
                    for (m in oc.materials) {
                        m.name = s.uniqueMaterialName(m.name)
                        s.materials.add(m)
                    }
                    val added = mutableListOf<String>()
                    for (o in oc.objects) {
                        o.name = s.uniqueName(o.name)
                        if (o.materialId == null || s.materials.none { it.id == o.materialId }) {
                            o.materialId = s.defaultMaterial().id
                        }
                        if (simplify && o.mesh != null) {
                            MeshOps.decimateTo(o.mesh!!, (settings.polyLimit / max(1, oc.objects.size)))
                        }
                        o.touchMesh()
                        s.objects.add(o)
                        added.add(o.id)
                    }
                    // Centraliza importados proximos a origem se muito distantes
                    selection.value = added.toSet()
                    markDirty()
                }
                bump()
                val warn = if (oc.warnings.isNotEmpty()) " (${oc.warnings.first()})" else ""
                say("Importado: ${oc.objects.size} objeto(s), ${oc.triCount} tris$warn")
                focusSelected()
            }
        }
    }

    fun exportModel(uri: Uri, ext: String, selectionOnly: Boolean) {
        isBusy.value = "Exportando..."
        val snapshot = withScene { it.deepCopy() }
        val ids = selection.value
        viewModelScope.launch(Dispatchers.IO) {
            val res = if (snapshot == null) {
                Result.failure<String>(IllegalArgumentException("Cena vazia"))
            } else {
                val list = if (selectionOnly) snapshot.objects.filter { it.id in ids } else snapshot.objects
                ModelExporter.exportToUri(getApplication(), uri, ext, snapshot, list) { tf ->
                    store.textureBytes(projectId, tf)
                }
            }
            withContext(Dispatchers.Main) {
                isBusy.value = null
                res.onSuccess { say(it) }.onFailure { say("Falha na exportacao: ${it.message}") }
            }
        }
    }

    // ---------- recuperacao / autosave ----------

    fun acceptRecovery() {
        viewModelScope.launch(Dispatchers.IO) {
            val rec = store.loadAutosaveIfNewer(projectId)
            withContext(Dispatchers.Main) {
                if (rec != null) {
                    synchronized(bridge.lock) { bridge.scene = rec }
                    projectName.value = rec.name
                    dirty.value = true
                    bump()
                    say("Trabalho recuperado do autosave")
                }
                recoveryAvailable.value = false
            }
        }
    }

    fun dismissRecovery() {
        recoveryAvailable.value = false
        viewModelScope.launch(Dispatchers.IO) { store.clearAutosave(projectId) }
    }

    fun renameProject(name: String) {
        val clean = name.trim().take(64).ifEmpty { "Untitled" }
        withScene { it.name = clean }
        projectName.value = clean
        markDirty()
        bump()
    }

    fun setActiveText(text: String) {
        val id = selection.value.lastOrNull() ?: return
        withScene { s ->
            val o = s.find(id) ?: return@withScene
            if (o.type != ObjectType.TEXT) return@withScene
            pushUndo()
            o.text = text.take(64)
            bridge.textures.remove("text:${o.id}")
            markDirty()
        }
        bump()
        say("Texto atualizado")
    }

    fun discardChanges() {
        viewModelScope.launch(Dispatchers.IO) { store.clearAutosave(projectId) }
        dirty.value = false
    }

    private fun startAutosaveLoop() {
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                val intervalMs = settings.autosaveMinutes * 60_000L
                if (dirty.value && lastEditMs - lastAutosaveMs > intervalMs && lastEditMs > 0) {
                    val snapshot = withScene { it.deepCopy() }
                    if (snapshot != null) {
                        withContext(Dispatchers.IO) { store.saveAutosave(projectId, snapshot) }
                        lastAutosaveMs = System.currentTimeMillis()
                        say("Autosave concluido")
                    }
                }
            }
        }
    }

    // ---------- configuracoes ----------

    fun updateSettings(block: (AppSettings) -> Unit) {
        block(settings)
        SettingsStore.save(getApplication(), settings)
        effectiveQuality.value = PerformanceManager.effectiveQuality(settings, getApplication())
        applySettingsToBridge()
        bump()
    }

    fun autoDegrade() {
        if (settings.quality != Quality.AUTO) {
            say("FPS baixo: considere reduzir a qualidade em Configuracoes")
            return
        }
        val cur = effectiveQuality.value
        val next = when (cur) {
            Quality.HIGH -> Quality.MEDIUM
            Quality.MEDIUM -> Quality.LOW
            else -> null
        }
        if (next != null) {
            effectiveQuality.value = next
            applySettingsToBridge()
            bump()
            say("Qualidade ajustada para ${next.name} (desempenho)")
        }
    }

    override fun onCleared() {
        animJob?.cancel()
        anim.playing = false
        // Autosave de seguranca ao sair
        val snapshot = withScene { it.deepCopy() }
        if (snapshot != null && dirty.value) {
            store.saveAutosave(projectId, snapshot)
        }
        super.onCleared()
    }
}
