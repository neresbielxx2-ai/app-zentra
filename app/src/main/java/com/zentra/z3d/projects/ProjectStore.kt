package com.zentra.z3d.projects
import kotlin.math.*

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.zentra.z3d.core.*
import java.io.File

data class ProjectInfo(
    val id: String,
    var name: String,
    var updatedAt: Long,
    var hasThumbnail: Boolean = false
)

private data class ProjectFile(
    var version: Int = 1,
    var name: String = "Untitled",
    var updatedAt: Long = 0L,
    var objects: MutableList<SceneObject> = mutableListOf(),
    var materials: MutableList<MaterialData> = mutableListOf(),
    var animation: AnimationData = AnimationData()
)

/** Persistencia local de projetos (JSON + texturas + thumbnail). Chamar em Dispatchers.IO. */
class ProjectStore(private val context: Context) {

    private val gson: Gson = GsonBuilder().create()
    private val root: File get() = File(context.filesDir, "projects").apply { mkdirs() }

    fun projectDir(id: String): File = File(root, sanitize(id)).apply { mkdirs() }
    fun texturesDir(projectId: String): File = File(projectDir(projectId), "textures").apply { mkdirs() }
    private fun projectFile(projectId: String): File = File(projectDir(projectId), "project.json")
    private fun autosaveFile(projectId: String): File = File(projectDir(projectId), "autosave.json")
    private fun thumbnailFile(projectId: String): File = File(projectDir(projectId), "thumbnail.png")
    private fun indexFile(): File = File(root, "index.json")

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9_\\-]"), "_").take(64).ifEmpty { "proj" }

    // ---------- indice / lista ----------

    fun listProjects(): List<ProjectInfo> {
        val fromIndex = runCatching {
            val idx = indexFile()
            if (!idx.exists()) return@runCatching null
            gson.fromJson(idx.readText(), Array<ProjectInfo>::class.java)?.toList()
        }.getOrNull()
        val valid = fromIndex?.filter { projectFile(it.id).exists() }
        if (valid != null) return valid.sortedByDescending { it.updatedAt }
        // Reconstrucao por varredura
        val rebuilt = mutableListOf<ProjectInfo>()
        for (dir in root.listFiles()?.filter { it.isDirectory } ?: emptyList()) {
            val pf = File(dir, "project.json")
            if (!pf.exists()) continue
            try {
                val data = gson.fromJson(pf.readText(), ProjectFile::class.java)
                rebuilt.add(
                    ProjectInfo(
                        dir.name, data.name, data.updatedAt,
                        File(dir, "thumbnail.png").exists()
                    )
                )
            } catch (_: Exception) {}
        }
        saveIndex(rebuilt)
        return rebuilt.sortedByDescending { it.updatedAt }
    }

    private fun saveIndex(list: List<ProjectInfo>) {
        try {
            indexFile().writeText(gson.toJson(list))
        } catch (_: Exception) {}
    }

    private fun updateIndex(info: ProjectInfo) {
        val list = listProjects().toMutableList()
        list.removeAll { it.id == info.id }
        list.add(info)
        saveIndex(list)
    }

    // ---------- CRUD ----------

    fun createProject(name: String): String {
        val id = newId("proj")
        val scene = Scene.newDefault(name)
        saveProject(id, scene).getOrThrow()
        return id
    }

    fun createProjectFromScene(name: String, scene: Scene): String {
        val id = newId("proj")
        scene.name = name
        saveProject(id, scene).getOrThrow()
        return id
    }

    fun saveProject(projectId: String, scene: Scene): Result<Unit> = runCatching {
        val data = ProjectFile(
            version = 1,
            name = scene.name,
            updatedAt = System.currentTimeMillis(),
            objects = scene.objects,
            materials = scene.materials,
            animation = scene.animation
        )
        // Escrita atomica: temp + rename
        val target = projectFile(projectId)
        val tmp = File(target.parent, "project.json.tmp")
        tmp.writeText(gson.toJson(data))
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        updateIndex(
            ProjectInfo(projectId, scene.name, data.updatedAt, thumbnailFile(projectId).exists())
        )
    }

    fun loadProject(projectId: String): Result<Scene> = runCatching {
        val f = projectFile(projectId)
        if (!f.exists()) throw IllegalArgumentException("Projeto nao encontrado")
        val data = gson.fromJson(f.readText(), ProjectFile::class.java)
            ?: throw IllegalArgumentException("Arquivo de projeto corrompido")
        val scene = Scene(data.name)
        scene.objects = data.objects.ifEmpty { mutableListOf() }
        scene.materials = data.materials.ifEmpty { mutableListOf() }
        scene.animation = data.animation
        // Reparo defensivo
        for (o in scene.objects) {
            o.mesh?.let { m ->
                if (m.normals.size != m.vertices.size) {
                    runCatching { m.computeNormals() }
                }
            }
        }
        if (scene.materials.isEmpty()) scene.materials.add(MaterialData(name = "Material"))
        scene
    }

    fun renameProject(projectId: String, newName: String): Result<Unit> = runCatching {
        val scene = loadProject(projectId).getOrThrow()
        scene.name = newName.take(64).ifBlank { "Untitled" }
        saveProject(projectId, scene).getOrThrow()
    }

    fun duplicateProject(projectId: String): Result<String> = runCatching {
        val src = projectDir(projectId)
        if (!projectFile(projectId).exists()) throw IllegalArgumentException("Projeto nao encontrado")
        val newId = newId("proj")
        val dst = File(root, newId)
        src.copyRecursively(dst)
        val scene = loadProject(newId).getOrThrow()
        scene.name = (scene.name + " copy").take(64)
        saveProject(newId, scene).getOrThrow()
        newId
    }

    fun deleteProject(projectId: String): Result<Unit> = runCatching {
        projectDir(projectId).deleteRecursively()
        val list = listProjects().filter { it.id != projectId }
        saveIndex(list)
    }

    // ---------- autosave / recuperacao ----------

    fun saveAutosave(projectId: String, scene: Scene) {
        try {
            val data = ProjectFile(1, scene.name, System.currentTimeMillis(), scene.objects, scene.materials, scene.animation)
            autosaveFile(projectId).writeText(gson.toJson(data))
        } catch (_: Exception) {}
    }

    /** Retorna o autosave se for mais novo que o ultimo save, ou null. */
    fun loadAutosaveIfNewer(projectId: String): Scene? {
        return try {
            val auto = autosaveFile(projectId)
            val main = projectFile(projectId)
            if (!auto.exists() || !main.exists()) return null
            val a = gson.fromJson(auto.readText(), ProjectFile::class.java) ?: return null
            val m = gson.fromJson(main.readText(), ProjectFile::class.java) ?: return null
            if (a.updatedAt <= m.updatedAt) return null
            val scene = Scene(a.name)
            scene.objects = a.objects
            scene.materials = a.materials
            scene.animation = a.animation
            scene
        } catch (_: Exception) { null }
    }

    fun clearAutosave(projectId: String) {
        try { autosaveFile(projectId).delete() } catch (_: Exception) {}
    }

    // ---------- thumbnails ----------

    fun saveThumbnail(projectId: String, bmp: Bitmap) {
        try {
            val maxDim = 512
            val scale = minOf(1f, maxDim.toFloat() / maxOf(bmp.width, bmp.height).toFloat())
            val out = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bmp, (bmp.width * scale).toInt().coerceAtLeast(1),
                    (bmp.height * scale).toInt().coerceAtLeast(1), true
                )
            } else bmp
            thumbnailFile(projectId).outputStream().use { stream ->
                out.compress(Bitmap.CompressFormat.PNG, 90, stream)
            }
            if (out !== bmp) out.recycle()
        } catch (_: Exception) {}
    }

    fun loadThumbnail(projectId: String): Bitmap? {
        return try {
            val f = thumbnailFile(projectId)
            if (!f.exists()) null else BitmapFactory.decodeFile(f.absolutePath)
        } catch (_: Exception) { null }
    }

    // ---------- texturas ----------

    /** Salva bytes de imagem como PNG redimensionado (max 1024). Retorna o nome do arquivo. */
    fun saveTexture(projectId: String, suggestedName: String, bytes: ByteArray): String? {
        return try {
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val maxDim = 1024
            val scale = minOf(1f, maxDim.toFloat() / maxOf(bmp.width, bmp.height).toFloat())
            val out = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bmp, (bmp.width * scale).toInt().coerceAtLeast(1),
                    (bmp.height * scale).toInt().coerceAtLeast(1), true
                )
            } else bmp
            var base = sanitize(suggestedName.substringBeforeLast('.').ifEmpty { "tex" })
            if (!base.endsWith(".png", ignoreCase = true)) base += ".png"
            var file = File(texturesDir(projectId), base)
            var i = 2
            while (file.exists()) {
                file = File(texturesDir(projectId), "${base.removeSuffix(".png")}_$i.png")
                i++
            }
            file.outputStream().use { stream -> out.compress(Bitmap.CompressFormat.PNG, 90, stream) }
            if (out !== bmp) { out.recycle() }
            bmp.recycle()
            file.name
        } catch (_: Exception) { null }
    }

    fun loadTextureBitmap(projectId: String, fileName: String): Bitmap? {
        return try {
            BitmapFactory.decodeFile(File(texturesDir(projectId), fileName).absolutePath)
        } catch (_: Exception) { null }
    }

    fun textureBytes(projectId: String, fileName: String): ByteArray? {
        return try {
            File(texturesDir(projectId), fileName).readBytes()
        } catch (_: Exception) { null }
    }

    fun deleteTextureIfUnused(projectId: String, fileName: String, scene: Scene) {
        try {
            val used = scene.materials.any { it.textureFile == fileName }
            if (!used) File(texturesDir(projectId), fileName).delete()
        } catch (_: Exception) {}
    }
}
