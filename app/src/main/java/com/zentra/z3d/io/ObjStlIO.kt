package com.zentra.z3d.io
import kotlin.math.*

import com.zentra.z3d.core.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.roundToInt

data class ImportedModel(val objects: List<SceneObject>, val warnings: List<String>)

/** Importacao/exportacao Wavefront OBJ (texto). */
object ObjIO {

    private data class FaceRef(val v: IntArray, val t: IntArray, val n: IntArray)

    fun importObj(text: String, defaultMatId: String?, baseName: String): Result<ImportedModel> = runCatching {
        val warnings = mutableListOf<String>()
        val pos = ArrayList<Vec3>()
        val tex = ArrayList<Vec2>()
        val nor = ArrayList<Vec3>()
        val groups = LinkedHashMap<String, MutableList<FaceRef>>()
        var current = "mesh"
        groups[current] = mutableListOf()
        var matNoted = false

        fun resolve(raw: Int, size: Int): Int {
            return if (raw > 0) raw - 1 else size + raw
        }

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val parts = line.split(Regex("\\s+"))
            when (parts[0]) {
                "v" -> {
                    if (parts.size >= 4) {
                        pos.add(
                            Vec3(
                                parts[1].toFloatOrNull() ?: 0f,
                                parts[2].toFloatOrNull() ?: 0f,
                                parts[3].toFloatOrNull() ?: 0f
                            )
                        )
                    }
                }
                "vt" -> {
                    if (parts.size >= 3) {
                        tex.add(Vec2(parts[1].toFloatOrNull() ?: 0f, parts[2].toFloatOrNull() ?: 0f))
                    }
                }
                "vn" -> {
                    if (parts.size >= 4) {
                        nor.add(
                            Vec3(
                                parts[1].toFloatOrNull() ?: 0f,
                                parts[2].toFloatOrNull() ?: 0f,
                                parts[3].toFloatOrNull() ?: 0f
                            )
                        )
                    }
                }
                "f" -> {
                    if (parts.size < 4) continue
                    try {
                        val fv = IntArray(parts.size - 1)
                        val ft = IntArray(parts.size - 1) { -1 }
                        val fn = IntArray(parts.size - 1) { -1 }
                        for (i in 1 until parts.size) {
                            val tok = parts[i].split("/")
                            val vi = resolve(tok[0].toInt(), pos.size)
                            if (vi !in pos.indices) throw IllegalArgumentException("indice v invalido")
                            fv[i - 1] = vi
                            if (tok.size > 1 && tok[1].isNotEmpty()) {
                                val ti = resolve(tok[1].toInt(), tex.size)
                                if (ti in tex.indices) ft[i - 1] = ti
                            }
                            if (tok.size > 2 && tok[2].isNotEmpty()) {
                                val ni = resolve(tok[2].toInt(), nor.size)
                                if (ni in nor.indices) fn[i - 1] = ni
                            }
                        }
                        groups.getOrPut(current) { mutableListOf() }.add(FaceRef(fv, ft, fn))
                    } catch (_: Exception) { /* face invalida: ignora */ }
                }
                "o", "g" -> {
                    val nm = parts.drop(1).joinToString(" ").trim().ifEmpty { "mesh" }
                    current = nm.take(64)
                    groups.getOrPut(current) { mutableListOf() }
                }
                "mtllib", "usemtl" -> {
                    if (!matNoted) {
                        matNoted = true
                        warnings.add("Materiais .mtl nao importados (geometria preservada)")
                    }
                }
                else -> {}
            }
        }
        if (pos.isEmpty()) throw IllegalArgumentException("Nenhum vertice (v) encontrado no arquivo")

        val objects = mutableListOf<SceneObject>()
        var groupIdx = 0
        for ((name, faces) in groups) {
            if (faces.isEmpty()) continue
            groupIdx++
            val mesh = MeshData()
            val map = HashMap<Triple<Int, Int, Int>, Int>()
            var missingNormals = false
            for (fr in faces) {
                val idx = mutableListOf<Int>()
                for (k in fr.v.indices) {
                    val key = Triple(fr.v[k], fr.t[k], fr.n[k])
                    val mi = map.getOrPut(key) {
                        mesh.vertices.add(pos[fr.v[k]].deepCopy())
                        mesh.uvs.add(if (fr.t[k] >= 0) tex[fr.t[k]].deepCopy() else Vec2(0f, 0f))
                        if (fr.n[k] >= 0) mesh.normals.add(nor[fr.n[k]].deepCopy())
                        else { mesh.normals.add(Vec3(0f, 1f, 0f)); missingNormals = true }
                        mesh.vertices.size - 1
                    }
                    idx.add(mi)
                }
                if (idx.distinct().size >= 3) mesh.faces.add(Face(idx))
            }
            if (mesh.faces.isEmpty()) continue
            if (missingNormals || mesh.normals.size != mesh.vertices.size) mesh.computeNormals()
            val objName = if (groups.size == 1) baseName else "$baseName-$name"
            objects.add(
                SceneObject(
                    name = objName.take(64), type = ObjectType.MESH,
                    mesh = mesh, materialId = defaultMatId
                )
            )
        }
        if (objects.isEmpty()) throw IllegalArgumentException("Nenhuma face valida encontrada no arquivo")
        ImportedModel(objects, warnings)
    }

    fun export(objects: List<SceneObject>): String {
        val sb = StringBuilder()
        sb.append("# Exportado por Zentra 3D\n")
        var offset = 1
        for (obj in objects) {
            val mesh = obj.mesh ?: continue
            if (mesh.vertices.isEmpty() || mesh.faces.isEmpty()) continue
            val safeName = obj.name.replace(Regex("[\\r\\n]"), "_")
            sb.append("o ").append(safeName).append('\n')
            mesh.ensureUvs()
            if (mesh.normals.size != mesh.vertices.size) mesh.computeNormals()
            for (v in mesh.vertices) {
                sb.append(String.format(Locale.US, "v %.6f %.6f %.6f\n", v.x, v.y, v.z))
            }
            for (uv in mesh.uvs) {
                sb.append(String.format(Locale.US, "vt %.6f %.6f\n", uv.x, uv.y))
            }
            for (n in mesh.normals) {
                sb.append(String.format(Locale.US, "vn %.6f %.6f %.6f\n", n.x, n.y, n.z))
            }
            for (f in mesh.faces) {
                if (f.indices.size < 3) continue
                sb.append("f")
                for (i in f.indices) {
                    val vi = i + offset
                    sb.append(' ').append(vi).append('/').append(vi).append('/').append(vi)
                }
                sb.append('\n')
            }
            offset += mesh.vertices.size
        }
        return sb.toString()
    }
}

/** Importacao/exportacao STL (ASCII e binario). */
object StlIO {

    fun importBytes(bytes: ByteArray, defaultMatId: String?, baseName: String): Result<ImportedModel> = runCatching {
        if (bytes.size < 84) throw IllegalArgumentException("Arquivo STL muito pequeno ou corrompido")
        val head = String(bytes, 0, minOf(512, bytes.size), Charsets.US_ASCII)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val count = buf.getInt(80)
        val expectedBinary = 84L + count.toLong() * 50L
        val isBinary = expectedBinary == bytes.size.toLong()
        val looksAscii = head.trimStart().startsWith("solid", ignoreCase = true) && !isBinary
        if (looksAscii) importAscii(String(bytes, Charsets.UTF_8), defaultMatId, baseName).getOrThrow()
        else importBinary(bytes, count, defaultMatId, baseName).getOrThrow()
    }

    private fun importAscii(text: String, defaultMatId: String?, baseName: String): Result<ImportedModel> = runCatching {
        val mesh = MeshData()
        val weld = HashMap<Triple<Int, Int, Int>, Int>()
        fun weldIndex(p: Vec3, n: Vec3): Int {
            val key = Triple((p.x * 1e5f).roundToInt(), (p.y * 1e5f).roundToInt(), (p.z * 1e5f).roundToInt())
            return weld.getOrPut(key) {
                mesh.vertices.add(p)
                mesh.normals.add(n)
                mesh.uvs.add(Vec2(0f, 0f))
                mesh.vertices.size - 1
            }
        }
        var currentNormal = Vec3(0f, 0f, 1f)
        val triVerts = mutableListOf<Vec3>()
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            when {
                line.startsWith("facet normal", ignoreCase = true) -> {
                    val p = line.split(Regex("\\s+"))
                    if (p.size >= 5) {
                        currentNormal = Vec3(
                            p[2].toFloatOrNull() ?: 0f,
                            p[3].toFloatOrNull() ?: 0f,
                            p[4].toFloatOrNull() ?: 1f
                        )
                    }
                    triVerts.clear()
                }
                line.startsWith("vertex", ignoreCase = true) -> {
                    val p = line.split(Regex("\\s+"))
                    if (p.size >= 4) {
                        triVerts.add(
                            Vec3(
                                p[1].toFloatOrNull() ?: 0f,
                                p[2].toFloatOrNull() ?: 0f,
                                p[3].toFloatOrNull() ?: 0f
                            )
                        )
                        if (triVerts.size == 3) {
                            val idx = triVerts.map { weldIndex(it, currentNormal.deepCopy()) }
                            if (idx.distinct().size == 3) mesh.faces.add(Face(idx.toMutableList()))
                            triVerts.clear()
                        }
                    }
                }
            }
        }
        if (mesh.faces.isEmpty()) throw IllegalArgumentException("Nenhum triangulo valido no STL")
        if (mesh.normals.size != mesh.vertices.size) mesh.computeNormals()
        ImportedModel(
            listOf(SceneObject(name = baseName, type = ObjectType.MESH, mesh = mesh, materialId = defaultMatId)),
            emptyList()
        )
    }

    private fun importBinary(bytes: ByteArray, count: Int, defaultMatId: String?, baseName: String): Result<ImportedModel> = runCatching {
        if (count <= 0 || count > 10_000_000) throw IllegalArgumentException("Contagem de triangulos invalida: $count")
        if (bytes.size < 84 + count * 50) throw IllegalArgumentException("Arquivo STL binario truncado")
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val mesh = MeshData()
        val weld = HashMap<Triple<Int, Int, Int>, Int>()
        fun weldIndex(p: Vec3, n: Vec3): Int {
            val key = Triple((p.x * 1e5f).roundToInt(), (p.y * 1e5f).roundToInt(), (p.z * 1e5f).roundToInt())
            return weld.getOrPut(key) {
                mesh.vertices.add(p)
                mesh.normals.add(n)
                mesh.uvs.add(Vec2(0f, 0f))
                mesh.vertices.size - 1
            }
        }
        var offset = 84
        repeat(count) {
            val nx = buf.getFloat(offset); val ny = buf.getFloat(offset + 4); val nz = buf.getFloat(offset + 8)
            val n = Vec3(nx, ny, nz)
            if (n.lengthSq() < 1e-12f) n.set(0f, 0f, 1f)
            else n.normalizeInPlace()
            val idx = IntArray(3)
            for (k in 0..2) {
                val b = offset + 12 + k * 12
                val p = Vec3(buf.getFloat(b), buf.getFloat(b + 4), buf.getFloat(b + 8))
                idx[k] = weldIndex(p, n.deepCopy())
            }
            if (idx.distinct().size == 3) mesh.faces.add(Face(idx.toMutableList()))
            offset += 50
        }
        if (mesh.faces.isEmpty()) throw IllegalArgumentException("Nenhum triangulo valido no STL")
        ImportedModel(
            listOf(SceneObject(name = baseName, type = ObjectType.MESH, mesh = mesh, materialId = defaultMatId)),
            emptyList()
        )
    }

    private fun Vec3.set(x: Float, y: Float, z: Float) {
        this.x = x; this.y = y; this.z = z
    }

    /** Exporta STL binario (mais compacto e compativel). */
    fun exportBinary(objects: List<SceneObject>): ByteArray {
        val tris = ArrayList<Triple<Vec3, Vec3, Vec3>>()
        for (obj in objects) {
            val mesh = obj.mesh ?: continue
            for (f in mesh.faces) {
                val idx = f.indices
                if (idx.size < 3 || idx.any { it !in mesh.vertices.indices }) continue
                for (i in 1 until idx.size - 1) {
                    tris.add(Triple(mesh.vertices[idx[0]], mesh.vertices[idx[i]], mesh.vertices[idx[i + 1]]))
                }
            }
        }
        val buf = ByteBuffer.allocate(84 + tris.size * 50).order(ByteOrder.LITTLE_ENDIAN)
        val header = "Binary STL - Zentra 3D".toByteArray(Charsets.US_ASCII)
        buf.put(header)
        repeat(80 - header.size) { buf.put(0) }
        buf.putInt(tris.size)
        for ((a, b, c) in tris) {
            val n = (b - a).cross(c - a)
            if (n.lengthSq() > 1e-12f) n.normalizeInPlace() else n.set(0f, 0f, 1f)
            buf.putFloat(n.x); buf.putFloat(n.y); buf.putFloat(n.z)
            buf.putFloat(a.x); buf.putFloat(a.y); buf.putFloat(a.z)
            buf.putFloat(b.x); buf.putFloat(b.y); buf.putFloat(b.z)
            buf.putFloat(c.x); buf.putFloat(c.y); buf.putFloat(c.z)
            buf.putShort(0)
        }
        return buf.array()
    }
}
