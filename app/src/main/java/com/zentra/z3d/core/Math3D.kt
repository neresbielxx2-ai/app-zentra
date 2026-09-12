package com.zentra.z3d.core

import kotlin.math.*

/** Matematica 3D propria do Zentra 3D (sem dependencias externas). */

const val DEG2RAD = (Math.PI / 180.0).toFloat()
const val RAD2DEG = (180.0 / Math.PI).toFloat()

fun clamp(v: Float, lo: Float, hi: Float): Float = v.coerceIn(lo, hi)
fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

data class Vec2(var x: Float = 0f, var y: Float = 0f) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Float) = Vec2(x * s, y * s)
    fun deepCopy() = Vec2(x, y)
}

data class Vec3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun div(s: Float) = Vec3(x / s, y / s, z / s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)
    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3): Vec3 = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x
    )
    fun length(): Float = sqrt(x * x + y * y + z * z)
    fun lengthSq(): Float = x * x + y * y + z * z
    fun normalized(): Vec3 {
        val l = length()
        return if (l > 1e-8f) this / l else Vec3(0f, 1f, 0f)
    }
    fun normalizeInPlace(): Vec3 {
        val l = length()
        if (l > 1e-8f) { x /= l; y /= l; z /= l }
        return this
    }
    fun distanceTo(o: Vec3): Float = (this - o).length()
    fun deepCopy() = Vec3(x, y, z)

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val ONE = Vec3(1f, 1f, 1f)
        val UP = Vec3(0f, 1f, 0f)
        val RIGHT = Vec3(1f, 0f, 0f)
        val FORWARD = Vec3(0f, 0f, -1f)
    }
}

data class Vec4(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f, var w: Float = 1f) {
    fun deepCopy() = Vec4(x, y, z, w)
}

data class Ray(var origin: Vec3 = Vec3(), var dir: Vec3 = Vec3(0f, 0f, -1f))

/** Matriz 4x4 column-major (padrao OpenGL). Nao serializada: sempre recalculada. */
class Mat4(var m: FloatArray = FloatArray(16)) {

    fun setIdentity(): Mat4 {
        m.fill(0f)
        m[0] = 1f; m[5] = 1f; m[10] = 1f; m[15] = 1f
        return this
    }

    fun copy(): Mat4 = Mat4(m.copyOf())

    /** Este = este * other (column-major). */
    fun multiplyInPlace(other: Mat4): Mat4 {
        val r = FloatArray(16)
        for (c in 0 until 4) {
            for (r2 in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) {
                    sum += m[k * 4 + r2] * other.m[c * 4 + k]
                }
                r[c * 4 + r2] = sum
            }
        }
        m = r
        return this
    }

    fun multiplied(other: Mat4): Mat4 = copy().multiplyInPlace(other)

    fun transformPoint(v: Vec3): Vec3 {
        val x = m[0] * v.x + m[4] * v.y + m[8] * v.z + m[12]
        val y = m[1] * v.x + m[5] * v.y + m[9] * v.z + m[13]
        val z = m[2] * v.x + m[6] * v.y + m[10] * v.z + m[14]
        val w = m[3] * v.x + m[7] * v.y + m[11] * v.z + m[15]
        return if (abs(w) > 1e-8f) Vec3(x / w, y / w, z / w) else Vec3(x, y, z)
    }

    fun transformDir(v: Vec3): Vec3 {
        return Vec3(
            m[0] * v.x + m[4] * v.y + m[8] * v.z,
            m[1] * v.x + m[5] * v.y + m[9] * v.z,
            m[2] * v.x + m[6] * v.y + m[10] * v.z
        )
    }

    fun translationPart(): Vec3 = Vec3(m[12], m[13], m[14])

    fun inverted(): Mat4? {
        val inv = FloatArray(16)
        val a = m
        inv[0] = a[5] * a[10] * a[15] - a[5] * a[11] * a[14] - a[9] * a[6] * a[15] +
                a[9] * a[7] * a[14] + a[13] * a[6] * a[11] - a[13] * a[7] * a[10]
        inv[4] = -a[4] * a[10] * a[15] + a[4] * a[11] * a[14] + a[8] * a[6] * a[15] -
                a[8] * a[7] * a[14] - a[12] * a[6] * a[11] + a[12] * a[7] * a[10]
        inv[8] = a[4] * a[9] * a[15] - a[4] * a[11] * a[13] - a[8] * a[5] * a[15] +
                a[8] * a[7] * a[13] + a[12] * a[5] * a[11] - a[12] * a[7] * a[9]
        inv[12] = -a[4] * a[9] * a[14] + a[4] * a[10] * a[13] + a[8] * a[5] * a[14] -
                a[8] * a[6] * a[13] - a[12] * a[5] * a[10] + a[12] * a[6] * a[9]
        inv[1] = -a[1] * a[10] * a[15] + a[1] * a[11] * a[14] + a[9] * a[2] * a[15] -
                a[9] * a[3] * a[14] - a[13] * a[2] * a[11] + a[13] * a[3] * a[10]
        inv[5] = a[0] * a[10] * a[15] - a[0] * a[11] * a[14] - a[8] * a[2] * a[15] +
                a[8] * a[3] * a[14] + a[12] * a[2] * a[11] - a[12] * a[3] * a[10]
        inv[9] = -a[0] * a[9] * a[15] + a[0] * a[11] * a[13] + a[8] * a[1] * a[15] -
                a[8] * a[3] * a[13] - a[12] * a[1] * a[11] + a[12] * a[3] * a[9]
        inv[13] = a[0] * a[9] * a[14] - a[0] * a[10] * a[13] - a[8] * a[1] * a[14] +
                a[8] * a[2] * a[13] + a[12] * a[1] * a[10] - a[12] * a[2] * a[9]
        inv[2] = a[1] * a[6] * a[15] - a[1] * a[7] * a[14] - a[5] * a[2] * a[15] +
                a[5] * a[3] * a[14] + a[13] * a[2] * a[7] - a[13] * a[3] * a[6]
        inv[6] = -a[0] * a[6] * a[15] + a[0] * a[7] * a[14] + a[4] * a[2] * a[15] -
                a[4] * a[3] * a[14] - a[12] * a[2] * a[7] + a[12] * a[3] * a[6]
        inv[10] = a[0] * a[5] * a[15] - a[0] * a[7] * a[13] - a[4] * a[1] * a[15] +
                a[4] * a[3] * a[13] + a[12] * a[1] * a[7] - a[12] * a[3] * a[5]
        inv[14] = -a[0] * a[5] * a[14] + a[0] * a[6] * a[13] + a[4] * a[1] * a[14] -
                a[4] * a[2] * a[13] - a[12] * a[1] * a[6] + a[12] * a[2] * a[5]
        inv[3] = -a[1] * a[6] * a[11] + a[1] * a[7] * a[10] + a[5] * a[2] * a[11] -
                a[5] * a[3] * a[10] - a[9] * a[2] * a[7] + a[9] * a[3] * a[6]
        inv[7] = a[0] * a[6] * a[11] - a[0] * a[7] * a[10] - a[4] * a[2] * a[11] +
                a[4] * a[3] * a[10] + a[8] * a[2] * a[7] - a[8] * a[3] * a[6]
        inv[11] = -a[0] * a[5] * a[11] + a[0] * a[7] * a[9] + a[4] * a[1] * a[11] -
                a[4] * a[3] * a[9] - a[8] * a[1] * a[7] + a[8] * a[3] * a[5]
        inv[15] = a[0] * a[5] * a[10] - a[0] * a[6] * a[9] - a[4] * a[1] * a[10] +
                a[4] * a[2] * a[9] + a[8] * a[1] * a[6] - a[8] * a[2] * a[5]
        var det = a[0] * inv[0] + a[1] * inv[4] + a[2] * inv[8] + a[3] * inv[12]
        if (abs(det) < 1e-12f) return null
        det = 1f / det
        for (i in 0 until 16) inv[i] *= det
        return Mat4(inv)
    }

    fun transposed(): Mat4 {
        val r = FloatArray(16)
        for (c in 0 until 4) for (rr in 0 until 4) r[c * 4 + rr] = m[rr * 4 + c]
        return Mat4(r)
    }

    companion object {
        fun identity(): Mat4 = Mat4().setIdentity()

        fun translation(t: Vec3): Mat4 {
            val r = identity()
            r.m[12] = t.x; r.m[13] = t.y; r.m[14] = t.z
            return r
        }

        fun scaleMat(s: Vec3): Mat4 {
            val r = identity()
            r.m[0] = s.x; r.m[5] = s.y; r.m[10] = s.z
            return r
        }

        private fun rotX(aDeg: Float): Mat4 {
            val a = aDeg * DEG2RAD
            val c = cos(a); val s = sin(a)
            val r = identity()
            r.m[5] = c; r.m[6] = s; r.m[9] = -s; r.m[10] = c
            return r
        }

        private fun rotY(aDeg: Float): Mat4 {
            val a = aDeg * DEG2RAD
            val c = cos(a); val s = sin(a)
            val r = identity()
            r.m[0] = c; r.m[2] = -s; r.m[8] = s; r.m[10] = c
            return r
        }

        private fun rotZ(aDeg: Float): Mat4 {
            val a = aDeg * DEG2RAD
            val c = cos(a); val s = sin(a)
            val r = identity()
            r.m[0] = c; r.m[1] = s; r.m[4] = -s; r.m[5] = c
            return r
        }

        /** T * Ry * Rx * Rz * S */
        fun fromTRS(pos: Vec3, eulerDeg: Vec3, scale: Vec3): Mat4 {
            return translation(pos)
                .multiplyInPlace(rotY(eulerDeg.y))
                .multiplyInPlace(rotX(eulerDeg.x))
                .multiplyInPlace(rotZ(eulerDeg.z))
                .multiplyInPlace(scaleMat(scale))
        }

        fun perspective(fovYDeg: Float, aspect: Float, near: Float, far: Float): Mat4 {
            val f = 1f / tan((fovYDeg * DEG2RAD) / 2f)
            val r = Mat4(FloatArray(16))
            r.m[0] = f / aspect
            r.m[5] = f
            r.m[10] = (far + near) / (near - far)
            r.m[11] = -1f
            r.m[14] = (2f * far * near) / (near - far)
            return r
        }

        fun lookAt(eye: Vec3, center: Vec3, up: Vec3): Mat4 {
            val f = (center - eye).normalized()
            var s = f.cross(up)
            if (s.lengthSq() < 1e-8f) s = Vec3(1f, 0f, 0f) else s.normalizeInPlace()
            val u = s.cross(f)
            val r = identity()
            r.m[0] = s.x; r.m[1] = u.x; r.m[2] = -f.x
            r.m[4] = s.y; r.m[5] = u.y; r.m[6] = -f.y
            r.m[8] = s.z; r.m[9] = u.z; r.m[10] = -f.z
            r.m[12] = -s.dot(eye); r.m[13] = -u.dot(eye); r.m[14] = f.dot(eye)
            return r
        }
    }
}

/** Moller-Trumbore. Retorna t do raio ou null se nao hitar. */
fun rayTriangleHit(ray: Ray, v0: Vec3, v1: Vec3, v2: Vec3): Float? {
    val eps = 1e-7f
    val e1 = v1 - v0
    val e2 = v2 - v0
    val p = ray.dir.cross(e2)
    val det = e1.dot(p)
    if (det > -eps && det < eps) return null
    val invDet = 1f / det
    val tvec = ray.origin - v0
    val u = tvec.dot(p) * invDet
    if (u < 0f || u > 1f) return null
    val q = tvec.cross(e1)
    val v = ray.dir.dot(q) * invDet
    if (v < 0f || u + v > 1f) return null
    val t = e2.dot(q) * invDet
    return if (t > eps) t else null
}

fun raySphereHit(ray: Ray, center: Vec3, radius: Float): Float? {
    val oc = ray.origin - center
    val b = oc.dot(ray.dir)
    val c = oc.dot(oc) - radius * radius
    val disc = b * b - c
    if (disc < 0f) return null
    val t = -b - sqrt(disc)
    return if (t > 1e-6f) t else null
}
