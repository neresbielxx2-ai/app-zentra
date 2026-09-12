package com.zentra.z3d.animation

import com.zentra.z3d.core.*

/** Sistema simples de keyframes: Position / Rotation / Scale por objeto. */
class AnimationEngine {
    var playing: Boolean = false
    var timeMs: Long = 0L

    fun ensureTrack(scene: Scene, objectId: String): AnimTrack {
        var track = scene.animation.trackFor(objectId)
        if (track == null) {
            track = AnimTrack(objectId)
            scene.animation.tracks.add(track)
        }
        return track
    }

    /** Insere keyframe com o transform atual. Retorna true se criou, false se atualizou. */
    fun addKey(scene: Scene, objectId: String, atMs: Long): Boolean {
        val obj = scene.find(objectId) ?: return false
        val track = ensureTrack(scene, objectId)
        val existing = track.keys.find { kotlin.math.abs(it.timeMs - atMs) <= 25L }
        if (existing != null) {
            existing.position = obj.transform.position.deepCopy()
            existing.rotation = obj.transform.rotation.deepCopy()
            existing.scale = obj.transform.scale.deepCopy()
            return false
        }
        track.keys.add(
            Keyframe(
                atMs,
                obj.transform.position.deepCopy(),
                obj.transform.rotation.deepCopy(),
                obj.transform.scale.deepCopy()
            )
        )
        if (atMs > scene.animation.durationMs) scene.animation.durationMs = atMs + 500L
        return true
    }

    fun removeKeyAt(scene: Scene, objectId: String, atMs: Long, toleranceMs: Long = 80L): Boolean {
        val track = scene.animation.trackFor(objectId) ?: return false
        val k = track.keys.minByOrNull { kotlin.math.abs(it.timeMs - atMs) } ?: return false
        if (kotlin.math.abs(k.timeMs - atMs) > toleranceMs) return false
        track.keys.remove(k)
        if (track.keys.isEmpty()) scene.animation.tracks.remove(track)
        return true
    }

    fun hasKeys(scene: Scene, objectId: String): Boolean =
        scene.animation.trackFor(objectId)?.keys?.isNotEmpty() == true

    private fun lerpAngle(a: Float, b: Float, t: Float): Float {
        var d = (b - a) % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return a + d * t
    }

    /** Avalia todos os tracks no instante dado. */
    fun evaluate(scene: Scene, atMs: Long): Map<String, Transform> {
        val out = HashMap<String, Transform>()
        for (track in scene.animation.tracks) {
            val keys = track.sortedKeys()
            if (keys.isEmpty()) continue
            if (keys.size == 1 || atMs <= keys.first().timeMs) {
                out[track.objectId] = keys.first().asTransform()
                continue
            }
            if (atMs >= keys.last().timeMs) {
                out[track.objectId] = keys.last().asTransform()
                continue
            }
            var a = keys.first(); var b = keys.last()
            for (i in 0 until keys.size - 1) {
                if (atMs >= keys[i].timeMs && atMs <= keys[i + 1].timeMs) {
                    a = keys[i]; b = keys[i + 1]; break
                }
            }
            val span = (b.timeMs - a.timeMs).coerceAtLeast(1L)
            val t = ((atMs - a.timeMs).toFloat() / span).coerceIn(0f, 1f)
            out[track.objectId] = Transform(
                position = Vec3(
                    lerp(a.position.x, b.position.x, t),
                    lerp(a.position.y, b.position.y, t),
                    lerp(a.position.z, b.position.z, t)
                ),
                rotation = Vec3(
                    lerpAngle(a.rotation.x, b.rotation.x, t),
                    lerpAngle(a.rotation.y, b.rotation.y, t),
                    lerpAngle(a.rotation.z, b.rotation.z, t)
                ),
                scale = Vec3(
                    lerp(a.scale.x, b.scale.x, t),
                    lerp(a.scale.y, b.scale.y, t),
                    lerp(a.scale.z, b.scale.z, t)
                )
            )
        }
        return out
    }

    /** Aplica a avaliacao na cena (usado durante play/scrub). */
    fun applyEvaluated(scene: Scene, eval: Map<String, Transform>) {
        for ((id, tr) in eval) {
            scene.find(id)?.transform = tr.deepCopy()
        }
    }

    fun tick(scene: Scene, deltaMs: Long): Long {
        if (!playing) return timeMs
        val dur = scene.animation.durationMs.coerceAtLeast(500L)
        timeMs = (timeMs + deltaMs) % dur
        applyEvaluated(scene, evaluate(scene, timeMs))
        return timeMs
    }
}
