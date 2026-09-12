package com.zentra.z3d.core

import kotlin.math.*

/** Geradores de malhas primitivas. */
object Primitives {

    fun createObject(kind: PrimitiveKind, defaultMatId: String?): SceneObject {
        return when (kind) {
            PrimitiveKind.CUBE -> SceneObject(
                name = "Cube", type = ObjectType.MESH,
                mesh = cube(2f), materialId = defaultMatId
            )
            PrimitiveKind.SPHERE -> SceneObject(
                name = "Sphere", type = ObjectType.MESH,
                mesh = sphere(1f, 28, 18), materialId = defaultMatId
            )
            PrimitiveKind.CYLINDER -> SceneObject(
                name = "Cylinder", type = ObjectType.MESH,
                mesh = cylinder(1f, 1f, 2f, 28), materialId = defaultMatId
            )
            PrimitiveKind.CONE -> SceneObject(
                name = "Cone", type = ObjectType.MESH,
                mesh = cone(1f, 2f, 28), materialId = defaultMatId
            )
            PrimitiveKind.PLANE -> SceneObject(
                name = "Plane", type = ObjectType.MESH,
                mesh = plane(4f, 1), materialId = defaultMatId
            )
            PrimitiveKind.TORUS -> SceneObject(
                name = "Torus", type = ObjectType.MESH,
                mesh = torus(1.2f, 0.42f, 36, 18), materialId = defaultMatId
            )
            PrimitiveKind.CAPSULE -> SceneObject(
                name = "Capsule", type = ObjectType.MESH,
                mesh = capsule(0.7f, 1.6f, 24), materialId = defaultMatId
            )
            PrimitiveKind.TEXT -> SceneObject(
                name = "Text", type = ObjectType.TEXT,
                mesh = plane(4f, 1), materialId = defaultMatId, text = "Zentra"
            )
            PrimitiveKind.EMPTY -> SceneObject(name = "Empty", type = ObjectType.EMPTY)
            PrimitiveKind.LIGHT_POINT -> SceneObject(
                name = "Point Light", type = ObjectType.LIGHT,
                light = LightData(kind = LightKind.POINT, intensity = 30f, range = 30f),
                transform = Transform(position = Vec3(3f, 4f, 2f))
            )
            PrimitiveKind.LIGHT_SPOT -> SceneObject(
                name = "Spot Light", type = ObjectType.LIGHT,
                light = LightData(kind = LightKind.SPOT, intensity = 60f, range = 40f, spotAngleDeg = 40f),
                transform = Transform(position = Vec3(3f, 5f, 2f), rotation = Vec3(-50f, 0f, 0f))
            )
            PrimitiveKind.LIGHT_DIR -> SceneObject(
                name = "Sun Light", type = ObjectType.LIGHT,
                light = LightData(kind = LightKind.DIRECTIONAL, intensity = 2.2f),
                transform = Transform(rotation = Vec3(-50f, 30f, 0f))
            )
            PrimitiveKind.LIGHT_AREA -> SceneObject(
                name = "Area Light", type = ObjectType.LIGHT,
                light = LightData(kind = LightKind.AREA, intensity = 25f, range = 30f),
                transform = Transform(position = Vec3(0f, 5f, 0f), rotation = Vec3(-90f, 0f, 0f))
            )
            PrimitiveKind.CAMERA -> SceneObject(
                name = "Camera", type = ObjectType.CAMERA,
                camera = CameraData(fovDeg = 50f),
                transform = Transform(position = Vec3(5.5f, 4f, 6.5f), rotation = Vec3(-22f, 38f, 0f))
            )
        }
    }

    fun cube(size: Float = 2f): MeshData {
        val h = size / 2f
        val m = MeshData()
        val v = listOf(
            Vec3(-h, -h, -h), Vec3(h, -h, -h), Vec3(h, h, -h), Vec3(-h, h, -h),
            Vec3(-h, -h, h), Vec3(h, -h, h), Vec3(h, h, h), Vec3(-h, h, h)
        )
        m.vertices.addAll(v)
        m.faces.addAll(
            listOf(
                Face(mutableListOf(0, 1, 2, 3)),
                Face(mutableListOf(5, 4, 7, 6)),
                Face(mutableListOf(4, 0, 3, 7)),
                Face(mutableListOf(1, 5, 6, 2)),
                Face(mutableListOf(3, 2, 6, 7)),
                Face(mutableListOf(4, 5, 1, 0))
            )
        )
        m.ensureUvs()
        m.computeNormals()
        return m
    }

    fun plane(size: Float = 4f, div: Int = 1): MeshData {
        val m = MeshData()
        val d = maxOf(1, div)
        val h = size / 2f
        for (iz in 0..d) {
            for (ix in 0..d) {
                m.vertices.add(Vec3(-h + size * ix / d, 0f, -h + size * iz / d))
                m.uvs.add(Vec2(ix / d.toFloat(), 1f - iz / d.toFloat()))
            }
        }
        for (iz in 0 until d) {
            for (ix in 0 until d) {
                val a = iz * (d + 1) + ix
                m.faces.add(Face(mutableListOf(a, a + 1, a + d + 2, a + d + 1)))
            }
        }
        m.computeNormals()
        return m
    }

    /** Revolucao de um perfil (raio, altura) ao redor do eixo Y. */
    private fun revolve(profile: List<Vec2>, segments: Int, capStart: Boolean, capEnd: Boolean): MeshData {
        val m = MeshData()
        val seg = maxOf(8, segments)
        for (i in profile.indices) {
            for (j in 0 until seg) {
                val a = 2f * PI.toFloat() * j / seg
                m.vertices.add(Vec3(profile[i].x * cos(a), profile[i].y, profile[i].x * sin(a)))
                m.uvs.add(Vec2(j / seg.toFloat(), i / (profile.size - 1).toFloat()))
            }
        }
        for (i in 0 until profile.size - 1) {
            for (j in 0 until seg) {
                val j2 = (j + 1) % seg
                val a = i * seg + j
                val b = i * seg + j2
                val c = (i + 1) * seg + j2
                val d = (i + 1) * seg + j
                if (profile[i].x < 1e-6f && profile[i + 1].x < 1e-6f) continue
                if (profile[i].x < 1e-6f) m.faces.add(Face(mutableListOf(a, c, d)))
                else if (profile[i + 1].x < 1e-6f) m.faces.add(Face(mutableListOf(a, b, c)))
                else m.faces.add(Face(mutableListOf(a, b, c, d)))
            }
        }
        if (capStart && profile.first().x > 1e-6f) {
            val centerIdx = m.vertices.size
            m.vertices.add(Vec3(0f, profile.first().y, 0f))
            m.uvs.add(Vec2(0.5f, 0f))
            for (j in 0 until seg) {
                val j2 = (j + 1) % seg
                m.faces.add(Face(mutableListOf(centerIdx, j2, j)))
            }
        }
        if (capEnd && profile.last().x > 1e-6f) {
            val centerIdx = m.vertices.size
            m.vertices.add(Vec3(0f, profile.last().y, 0f))
            m.uvs.add(Vec2(0.5f, 1f))
            val base = (profile.size - 1) * seg
            for (j in 0 until seg) {
                val j2 = (j + 1) % seg
                m.faces.add(Face(mutableListOf(centerIdx, base + j, base + j2)))
            }
        }
        m.computeNormals()
        return m
    }

    fun sphere(radius: Float = 1f, segments: Int = 28, rings: Int = 18): MeshData {
        val profile = mutableListOf<Vec2>()
        for (i in 0..rings) {
            val t = PI.toFloat() * i / rings
            profile.add(Vec2(radius * sin(t), radius * cos(t)))
        }
        return revolve(profile, segments, false, false)
    }

    fun cylinder(rTop: Float = 1f, rBottom: Float = 1f, height: Float = 2f, segments: Int = 28): MeshData {
        val h = height / 2f
        return revolve(listOf(Vec2(rBottom, -h), Vec2(rTop, h)), segments, true, true)
    }

    fun cone(radius: Float = 1f, height: Float = 2f, segments: Int = 28): MeshData {
        val h = height / 2f
        return revolve(listOf(Vec2(radius, -h), Vec2(0f, h)), segments, true, false)
    }

    fun capsule(radius: Float = 0.7f, height: Float = 1.6f, segments: Int = 24): MeshData {
        val profile = mutableListOf<Vec2>()
        val half = height / 2f
        val arc = 8
        for (i in 0..arc) {
            val t = -PI.toFloat() / 2f + PI.toFloat() / 2f * i / arc
            profile.add(Vec2(radius * cos(t), -half + radius * (1f + sin(t)) - radius))
        }
        for (i in 1..arc) {
            val t = PI.toFloat() / 2f * i / arc
            profile.add(Vec2(radius * cos(t), half - radius * (1f - sin(t)) + radius))
        }
        // Normaliza para topo/base exatos
        val fixed = profile.map { Vec2(maxOf(0f, it.x), it.y) }.toMutableList()
        fixed[0] = Vec2(0f, -half - radius)
        fixed[fixed.size - 1] = Vec2(0f, half + radius)
        return revolve(fixed, segments, false, false)
    }

    fun torus(ringR: Float = 1.2f, tubeR: Float = 0.42f, segRing: Int = 36, segTube: Int = 18): MeshData {
        val m = MeshData()
        for (i in 0 until segRing) {
            val u = 2f * PI.toFloat() * i / segRing
            val cu = cos(u); val su = sin(u)
            for (j in 0 until segTube) {
                val v = 2f * PI.toFloat() * j / segTube
                val cv = cos(v); val sv = sin(v)
                m.vertices.add(Vec3((ringR + tubeR * cv) * cu, tubeR * sv, (ringR + tubeR * cv) * su))
                m.uvs.add(Vec2(i / segRing.toFloat(), j / segTube.toFloat()))
            }
        }
        for (i in 0 until segRing) {
            val i2 = (i + 1) % segRing
            for (j in 0 until segTube) {
                val j2 = (j + 1) % segTube
                m.faces.add(
                    Face(mutableListOf(i * segTube + j, i2 * segTube + j, i2 * segTube + j2, i * segTube + j2))
                )
            }
        }
        m.computeNormals()
        return m
    }
}
