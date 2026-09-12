package com.zentra.z3d.render

import com.zentra.z3d.core.*
import kotlin.math.*

/** Controle de camera orbital touch-friendly. */
class CameraController {
    var target: Vec3 = Vec3(0f, 0.8f, 0f)
    var yawDeg: Float = -35f
    var pitchDeg: Float = 24f
    var distance: Float = 9f
    var fovDeg: Float = 50f
    var near: Float = 0.1f
    var far: Float = 300f
    var minDistance: Float = 0.5f
    var maxDistance: Float = 120f

    fun position(): Vec3 {
        val yaw = yawDeg * DEG2RAD
        val pitch = clamp(pitchDeg, -89f, 89f) * DEG2RAD
        val cp = cos(pitch)
        val offset = Vec3(cp * sin(yaw), sin(pitch), cp * cos(yaw)) * distance
        return target + offset
    }

    fun forward(): Vec3 = (target - position()).normalized()

    fun basis(): Pair<Vec3, Vec3> {
        val f = forward()
        var right = f.cross(Vec3.UP)
        if (right.lengthSq() < 1e-6f) right = Vec3.RIGHT.deepCopy() else right.normalizeInPlace()
        val up = right.cross(f).normalized()
        return right to up
    }

    fun orbit(dxDeg: Float, dyDeg: Float) {
        yawDeg -= dxDeg
        pitchDeg = clamp(pitchDeg + dyDeg, -89f, 89f)
    }

    fun pan(worldDx: Float, worldDy: Float) {
        val (right, up) = basis()
        target = target - right * worldDx + up * worldDy
    }

    fun zoom(factor: Float) {
        distance = clamp(distance * factor, minDistance, maxDistance)
    }

    fun viewMatrix(): Mat4 = Mat4.lookAt(position(), target, Vec3.UP)
    fun projectionMatrix(aspect: Float): Mat4 = Mat4.perspective(fovDeg, aspect, near, far)

    fun worldPerPixel(viewportHeightPx: Float): Float {
        if (viewportHeightPx < 1f) return 0.01f
        return (2f * distance * tan(fovDeg * DEG2RAD / 2f)) / viewportHeightPx
    }

    fun rayFor(ndcX: Float, ndcY: Float, aspect: Float): Ray {
        val vp = projectionMatrix(aspect).multiplied(viewMatrix())
        val inv = vp.inverted() ?: return Ray(position(), forward())
        val p0 = inv.transformHomogeneous(Vec4(ndcX, ndcY, -1f, 1f))
        val p1 = inv.transformHomogeneous(Vec4(ndcX, ndcY, 1f, 1f))
        val dir = (Vec3(p1.x, p1.y, p1.z) - Vec3(p0.x, p0.y, p0.z)).normalized()
        return Ray(Vec3(p0.x, p0.y, p0.z), dir)
    }

    fun preset(name: String) {
        when (name.uppercase()) {
            "FRONT" -> { yawDeg = 0f; pitchDeg = 6f }
            "BACK" -> { yawDeg = 180f; pitchDeg = 6f }
            "RIGHT" -> { yawDeg = 90f; pitchDeg = 6f }
            "LEFT" -> { yawDeg = -90f; pitchDeg = 6f }
            "TOP" -> { pitchDeg = 89f }
            "BOTTOM" -> { pitchDeg = -89f }
        }
    }

    fun focus(center: Vec3, radius: Float) {
        target = center.deepCopy()
        distance = clamp(radius * 3.4f + 0.8f, minDistance, maxDistance)
    }

    /** Posiciona o controlador como a camera do objeto (View Camera). */
    fun matchCameraObject(camObj: SceneObject, scene: Scene) {
        val wm = scene.worldMatrix(camObj)
        val eye = wm.translationPart()
        val fwd = wm.transformDir(Vec3(0f, 0f, -1f)).normalized()
        val dist = clamp(distance, minDistance, maxDistance)
        target = eye + fwd * dist
        val off = (eye - target).normalized()
        pitchDeg = asin(clamp(off.y, -1f, 1f)) * RAD2DEG
        yawDeg = atan2(off.x, off.z) * RAD2DEG
        camObj.camera?.let { fovDeg = it.fovDeg }
    }

    fun deepCopy(): CameraController {
        val c = CameraController()
        c.target = target.deepCopy()
        c.yawDeg = yawDeg; c.pitchDeg = pitchDeg; c.distance = distance
        c.fovDeg = fovDeg; c.near = near; c.far = far
        return c
    }
}

private fun Mat4.transformHomogeneous(v: Vec4): Vec4 {
    val a = m
    val x = a[0] * v.x + a[4] * v.y + a[8] * v.z + a[12] * v.w
    val y = a[1] * v.x + a[5] * v.y + a[9] * v.z + a[13] * v.w
    val z = a[2] * v.x + a[6] * v.y + a[10] * v.z + a[14] * v.w
    val w = a[3] * v.x + a[7] * v.y + a[11] * v.z + a[15] * v.w
    return if (abs(w) > 1e-8f) Vec4(x / w, y / w, z / w, 1f) else Vec4(x, y, z, w)
}

data class PickHit(
    val objectId: String,
    val distance: Float,
    val point: Vec3,
    val faceIndex: Int = -1
)

/** Selecao por ray casting contra a cena. */
object Picking {

    fun pickObject(scene: Scene, ray: Ray): PickHit? {
        var best: PickHit? = null
        for (obj in scene.objects) {
            if (!obj.visible || obj.locked) continue
            val wm = scene.worldMatrix(obj)
            val mesh = obj.mesh
            if ((obj.type == ObjectType.MESH || obj.type == ObjectType.TEXT) && mesh != null) {
                val inv = wm.inverted() ?: continue
                val localRay = Ray(
                    inv.transformPoint(ray.origin),
                    inv.transformDir(ray.dir).normalized()
                )
                val (center, radius) = mesh.boundingSphere()
                if (raySphereHit(localRay, center, radius) == null) continue
                var bestT = Float.MAX_VALUE
                var bestFace = -1
                for ((fi, f) in mesh.faces.withIndex()) {
                    val idx = f.indices
                    if (idx.size < 3) continue
                    var ok = true
                    for (i in idx) if (i !in mesh.vertices.indices) { ok = false; break }
                    if (!ok) continue
                    for (i in 1 until idx.size - 1) {
                        val t = rayTriangleHit(
                            localRay,
                            mesh.vertices[idx[0]], mesh.vertices[idx[i]], mesh.vertices[idx[i + 1]]
                        )
                        if (t != null && t < bestT) { bestT = t; bestFace = fi }
                    }
                }
                if (bestFace >= 0) {
                    val localPoint = localRay.origin + localRay.dir * bestT
                    val worldPoint = wm.transformPoint(localPoint)
                    val dist = (worldPoint - ray.origin).dot(ray.dir)
                    if (dist > 0 && (best == null || dist < best.distance)) {
                        best = PickHit(obj.id, dist, worldPoint, bestFace)
                    }
                }
            } else {
                // Empty / Light / Camera: esfera de toque generosa
                val c = wm.translationPart()
                val t = raySphereHit(ray, c, 0.45f)
                if (t != null && (best == null || t < best.distance)) {
                    best = PickHit(obj.id, t, ray.origin + ray.dir * t, -1)
                }
            }
        }
        return best
    }

    private fun distToRay(p: Vec3, ray: Ray): Pair<Float, Float> {
        val rel = p - ray.origin
        val t = rel.dot(ray.dir)
        val perp = (rel - ray.dir * t).length()
        return perp to t
    }

    fun pickVertex(mesh: MeshData, world: Mat4, ray: Ray, threshold: Float): Int {
        var best = -1
        var bestT = Float.MAX_VALUE
        for (i in mesh.vertices.indices) {
            val w = world.transformPoint(mesh.vertices[i])
            val (perp, t) = distToRay(w, ray)
            if (t > 0 && perp < threshold && t < bestT) { bestT = t; best = i }
        }
        return best
    }

    fun pickEdge(mesh: MeshData, world: Mat4, ray: Ray, threshold: Float): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var bestT = Float.MAX_VALUE
        for (e in mesh.edges()) {
            if (e.first !in mesh.vertices.indices || e.second !in mesh.vertices.indices) continue
            val mid = world.transformPoint(
                (mesh.vertices[e.first] + mesh.vertices[e.second]) * 0.5f
            )
            val (perp, t) = distToRay(mid, ray)
            if (t > 0 && perp < threshold && t < bestT) { bestT = t; best = e }
        }
        return best
    }

    fun pickFace(mesh: MeshData, world: Mat4, ray: Ray): Int {
        val inv = world.inverted() ?: return -1
        val localRay = Ray(inv.transformPoint(ray.origin), inv.transformDir(ray.dir).normalized())
        var bestT = Float.MAX_VALUE
        var bestFace = -1
        for ((fi, f) in mesh.faces.withIndex()) {
            val idx = f.indices
            if (idx.size < 3 || idx.any { it !in mesh.vertices.indices }) continue
            for (i in 1 until idx.size - 1) {
                val t = rayTriangleHit(
                    localRay, mesh.vertices[idx[0]], mesh.vertices[idx[i]], mesh.vertices[idx[i + 1]]
                )
                if (t != null && t < bestT) { bestT = t; bestFace = fi }
            }
        }
        return bestFace
    }
}
