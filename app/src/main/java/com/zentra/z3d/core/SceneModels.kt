package com.zentra.z3d.core
import kotlin.math.*

import java.util.UUID

fun newId(prefix: String): String = prefix + "_" + UUID.randomUUID().toString().take(8)

enum class ObjectType { MESH, TEXT, EMPTY, LIGHT, CAMERA }
enum class LightKind { POINT, SPOT, DIRECTIONAL, AREA }

enum class PrimitiveKind {
    CUBE, SPHERE, CYLINDER, CONE, PLANE, TORUS, CAPSULE, TEXT,
    EMPTY, LIGHT_POINT, LIGHT_SPOT, LIGHT_DIR, LIGHT_AREA, CAMERA
}

data class Rgba(var r: Float = 1f, var g: Float = 1f, var b: Float = 1f, var a: Float = 1f) {
    fun deepCopy() = Rgba(r, g, b, a)
    fun toHex(): String {
        fun c(v: Float) = (clamp(v, 0f, 1f) * 255f).toInt().coerceIn(0, 255)
        return String.format("#%02X%02X%02X", c(r), c(g), c(b))
    }

    companion object {
        fun fromHex(hex: String): Rgba? {
            return try {
                val h = hex.trim().removePrefix("#")
                val full = when (h.length) {
                    6 -> h
                    3 -> h.map { "$it$it" }.joinToString("")
                    else -> return null
                }
                val v = full.toLong(16)
                Rgba(
                    ((v shr 16) and 0xFF) / 255f,
                    ((v shr 8) and 0xFF) / 255f,
                    (v and 0xFF) / 255f, 1f
                )
            } catch (_: Exception) { null }
        }
    }
}

data class Transform(
    var position: Vec3 = Vec3(),
    var rotation: Vec3 = Vec3(), // euler em graus
    var scale: Vec3 = Vec3(1f, 1f, 1f)
) {
    fun matrix(): Mat4 = Mat4.fromTRS(position, rotation, scale)
    fun deepCopy() = Transform(position.deepCopy(), rotation.deepCopy(), scale.deepCopy())
}

data class Face(var indices: MutableList<Int> = mutableListOf()) {
    fun deepCopy() = Face(indices.toMutableList())
    fun isValid(vCount: Int): Boolean =
        indices.size >= 3 && indices.all { it in 0 until vCount }
}

/** Malha poligonal editavel. Faces podem ser tris, quads ou ngons. */
data class MeshData(
    var vertices: MutableList<Vec3> = mutableListOf(),
    var normals: MutableList<Vec3> = mutableListOf(),
    var uvs: MutableList<Vec2> = mutableListOf(),
    var faces: MutableList<Face> = mutableListOf()
) {
    fun deepCopy(): MeshData = MeshData(
        vertices.map { it.deepCopy() }.toMutableList(),
        normals.map { it.deepCopy() }.toMutableList(),
        uvs.map { it.deepCopy() }.toMutableList(),
        faces.map { it.deepCopy() }.toMutableList()
    )

    fun triCount(): Int {
        var n = 0
        for (f in faces) if (f.indices.size >= 3) n += f.indices.size - 2
        return n
    }

    /** Triangulacao em leque (fan). */
    fun triangleIndices(): IntArray {
        val out = ArrayList<Int>(triCount() * 3)
        for (f in faces) {
            val idx = f.indices
            if (idx.size < 3) continue
            for (i in 1 until idx.size - 1) {
                out.add(idx[0]); out.add(idx[i]); out.add(idx[i + 1])
            }
        }
        return out.toIntArray()
    }

    fun edges(): Set<Pair<Int, Int>> {
        val set = LinkedHashSet<Pair<Int, Int>>()
        for (f in faces) {
            val idx = f.indices
            for (i in idx.indices) {
                val a = idx[i]; val b = idx[(i + 1) % idx.size]
                set.add(if (a < b) a to b else b to a)
            }
        }
        return set
    }

    /** Normais suaves ponderadas por area. */
    fun computeNormals() {
        normals = MutableList(vertices.size) { Vec3() }
        for (f in faces) {
            val idx = f.indices
            if (idx.size < 3) continue
            if (idx.any { it !in vertices.indices }) continue
            val v0 = vertices[idx[0]]
            for (i in 1 until idx.size - 1) {
                val v1 = vertices[idx[i]]
                val v2 = vertices[idx[i + 1]]
                val n = (v1 - v0).cross(v2 - v0)
                for (j in listOf(idx[0], idx[i], idx[i + 1])) {
                    val acc = normals[j]
                    acc.x += n.x; acc.y += n.y; acc.z += n.z
                }
            }
        }
        for (i in normals.indices) {
            val n = normals[i]
            if (n.lengthSq() < 1e-12f) normals[i] = Vec3(0f, 1f, 0f)
            else n.normalizeInPlace()
        }
    }

    fun ensureUvs() {
        if (uvs.size != vertices.size) {
            uvs = MutableList(vertices.size) { i ->
                if (i < uvs.size) uvs[i] else Vec2(0f, 0f)
            }
        }
    }

    fun boundingSphere(): Pair<Vec3, Float> {
        if (vertices.isEmpty()) return Vec3() to 0.5f
        var cx = 0f; var cy = 0f; var cz = 0f
        for (v in vertices) { cx += v.x; cy += v.y; cz += v.z }
        val n = vertices.size.toFloat()
        val c = Vec3(cx / n, cy / n, cz / n)
        var r = 0.001f
        for (v in vertices) r = maxOf(r, v.distanceTo(c))
        return c to r
    }

    /** Remove faces degeneradas e vertices orfaos. Retorna nº de faces removidas. */
    fun cleanup(): Int {
        var removed = 0
        val it = faces.iterator()
        while (it.hasNext()) {
            val f = it.next()
            val distinct = f.indices.distinct()
            if (distinct.size < 3 || f.indices.any { v -> v !in vertices.indices }) {
                it.remove(); removed++
            } else if (distinct.size != f.indices.size) {
                f.indices = distinct.toMutableList()
            }
        }
        val used = BooleanArray(vertices.size)
        for (f in faces) for (i in f.indices) if (i in used.indices) used[i] = true
        if (used.all { it }) { computeNormals(); return removed }
        val remap = IntArray(vertices.size) { -1 }
        val newVerts = mutableListOf<Vec3>()
        val newUvs = mutableListOf<Vec2>()
        ensureUvs()
        for (i in vertices.indices) {
            if (used[i]) {
                remap[i] = newVerts.size
                newVerts.add(vertices[i])
                newUvs.add(uvs[i])
            }
        }
        for (f in faces) {
            f.indices = f.indices.map { remap[it] }.toMutableList()
        }
        vertices = newVerts
        uvs = newUvs
        computeNormals()
        return removed
    }
}

data class MaterialData(
    var id: String = newId("mat"),
    var name: String = "Material",
    var baseColor: Rgba = Rgba(0.75f, 0.78f, 0.85f, 1f),
    var roughness: Float = 0.55f,
    var metallic: Float = 0.0f,
    var emission: Rgba = Rgba(0f, 0f, 0f, 1f),
    var emissionStrength: Float = 0f,
    var opacity: Float = 1f,
    var textureFile: String? = null, // arquivo local dentro da pasta do projeto
    var uvScale: Vec2 = Vec2(1f, 1f),
    var uvOffset: Vec2 = Vec2(0f, 0f)
) {
    fun deepCopy() = copy(
        baseColor = baseColor.deepCopy(),
        emission = emission.deepCopy(),
        uvScale = uvScale.deepCopy(),
        uvOffset = uvOffset.deepCopy()
    )
}

data class LightData(
    var kind: LightKind = LightKind.POINT,
    var color: Rgba = Rgba(1f, 1f, 1f, 1f),
    var intensity: Float = 20f,
    var range: Float = 25f,
    var spotAngleDeg: Float = 45f,
    var spotBlend: Float = 0.4f,
    var castShadow: Boolean = false
) {
    fun deepCopy() = copy(color = color.deepCopy())
}

data class CameraData(
    var fovDeg: Float = 50f,
    var near: Float = 0.1f,
    var far: Float = 200f,
    var isMain: Boolean = false
) {
    fun deepCopy() = copy()
}

data class SceneObject(
    var id: String = newId("obj"),
    var name: String = "Object",
    var type: ObjectType = ObjectType.MESH,
    var transform: Transform = Transform(),
    var mesh: MeshData? = null,
    var materialId: String? = null,
    var light: LightData? = null,
    var camera: CameraData? = null,
    var visible: Boolean = true,
    var parentId: String? = null,
    var locked: Boolean = false,
    var text: String? = null,
    var meshVersion: Long = 0L
) {
    fun deepCopy(): SceneObject = copy(
        transform = transform.deepCopy(),
        mesh = mesh?.deepCopy(),
        light = light?.deepCopy(),
        camera = camera?.deepCopy()
    )

    fun touchMesh() { meshVersion++ }
}

data class Keyframe(
    var timeMs: Long = 0L,
    var position: Vec3 = Vec3(),
    var rotation: Vec3 = Vec3(),
    var scale: Vec3 = Vec3(1f, 1f, 1f)
) {
    fun deepCopy() = Keyframe(timeMs, position.deepCopy(), rotation.deepCopy(), scale.deepCopy())
    fun asTransform() = Transform(position.deepCopy(), rotation.deepCopy(), scale.deepCopy())
}

data class AnimTrack(
    var objectId: String = "",
    var keys: MutableList<Keyframe> = mutableListOf()
) {
    fun deepCopy() = AnimTrack(objectId, keys.map { it.deepCopy() }.toMutableList())
    fun sortedKeys(): List<Keyframe> = keys.sortedBy { it.timeMs }
}

data class AnimationData(
    var tracks: MutableList<AnimTrack> = mutableListOf(),
    var durationMs: Long = 4000L,
    var fps: Int = 30
) {
    fun deepCopy() = AnimationData(tracks.map { it.deepCopy() }.toMutableList(), durationMs, fps)
    fun trackFor(objectId: String): AnimTrack? = tracks.find { it.objectId == objectId }
}
