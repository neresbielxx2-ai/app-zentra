package com.zentra.z3d.core
import kotlin.math.*

data class OpResult(val ok: Boolean, val message: String, val changed: Int = 0)

/** Operacoes reais de modelagem sobre malhas poligonais. */
object MeshOps {

    private fun faceNormal(mesh: MeshData, f: Face): Vec3 {
        // Newell
        var nx = 0f; var ny = 0f; var nz = 0f
        val idx = f.indices
        for (i in idx.indices) {
            val a = mesh.vertices[idx[i]]
            val b = mesh.vertices[idx[(i + 1) % idx.size]]
            nx += (a.y - b.y) * (a.z + b.z)
            ny += (a.z - b.z) * (a.x + b.x)
            nz += (a.x - b.x) * (a.y + b.y)
        }
        val n = Vec3(nx, ny, nz)
        return if (n.lengthSq() > 1e-12f) n.normalizeInPlace() else Vec3(0f, 1f, 0f)
    }

    private fun faceCentroid(mesh: MeshData, f: Face): Vec3 {
        var c = Vec3()
        for (i in f.indices) c += mesh.vertices[i]
        return c / f.indices.size.toFloat()
    }

    private fun edgeKey(a: Int, b: Int): Pair<Int, Int> = if (a < b) a to b else b to a

    /** Extrude conectado: desloca a regiao selecionada ao longo das normais. */
    fun extrudeFaces(mesh: MeshData, faceSel: Set<Int>, distance: Float): OpResult {
        return try {
            val sel = faceSel.filter { it in mesh.faces.indices }
            if (sel.isEmpty()) return OpResult(false, "Nenhuma face selecionada")
            if (distance == 0f) return OpResult(false, "Distancia zero")
            mesh.ensureUvs()
            // Vertices usados pela selecao -> duplicados com deslocamento medio das normais
            val usedVerts = LinkedHashSet<Int>()
            val vertNormal = HashMap<Int, Vec3>()
            val vertCount = HashMap<Int, Int>()
            for (fi in sel) {
                val f = mesh.faces[fi]
                val n = faceNormal(mesh, f)
                for (vi in f.indices) {
                    usedVerts.add(vi)
                    val acc = vertNormal.getOrPut(vi) { Vec3() }
                    acc.x += n.x; acc.y += n.y; acc.z += n.z
                    vertCount[vi] = (vertCount[vi] ?: 0) + 1
                }
            }
            val dupMap = HashMap<Int, Int>()
            for (vi in usedVerts) {
                val acc = vertNormal[vi] ?: Vec3(0f, 1f, 0f)
                acc.normalizeInPlace()
                val p = mesh.vertices[vi] + acc * distance
                dupMap[vi] = mesh.vertices.size
                mesh.vertices.add(p)
                mesh.uvs.add(mesh.uvs[vi].deepCopy())
            }
            // Arestas de fronteira (usadas por exatamente 1 face selecionada)
            val edgeUse = HashMap<Pair<Int, Int>, Int>()
            for (fi in sel) {
                val idx = mesh.faces[fi].indices
                for (i in idx.indices) {
                    val k = edgeKey(idx[i], idx[(i + 1) % idx.size])
                    edgeUse[k] = (edgeUse[k] ?: 0) + 1
                }
            }
            // Paredes laterais na fronteira (orientacao herdada da face)
            for (fi in sel) {
                val idx = mesh.faces[fi].indices
                for (i in idx.indices) {
                    val a = idx[i]; val b = idx[(i + 1) % idx.size]
                    if (edgeUse[edgeKey(a, b)] == 1) {
                        mesh.faces.add(Face(mutableListOf(a, b, dupMap[b]!!, dupMap[a]!!)))
                    }
                }
            }
            // Move faces selecionadas para os duplicados
            for (fi in sel) {
                val f = mesh.faces[fi]
                f.indices = f.indices.map { dupMap[it]!! }.toMutableList()
            }
            mesh.computeNormals()
            OpResult(true, "Extrude aplicado em ${sel.size} face(s)", sel.size)
        } catch (e: Exception) {
            OpResult(false, "Falha no extrude: ${e.message}")
        }
    }

    /** Inset: cria loop interno encolhido dentro de cada face selecionada. */
    fun insetFaces(mesh: MeshData, faceSel: Set<Int>, amount: Float): OpResult {
        return try {
            val sel = faceSel.filter { it in mesh.faces.indices }
            if (sel.isEmpty()) return OpResult(false, "Nenhuma face selecionada")
            val t = clamp(amount, 0.02f, 0.95f)
            mesh.ensureUvs()
            val removeFaces = sel.sortedDescending()
            var count = 0
            for (fi in removeFaces) {
                val f = mesh.faces[fi]
                if (f.indices.size < 3) continue
                val c = faceCentroid(mesh, f)
                val newIdx = f.indices.map { vi ->
                    val p = mesh.vertices[vi]
                    val shrunk = p + (c - p) * t
                    mesh.vertices.add(shrunk)
                    mesh.uvs.add(mesh.uvs[vi].deepCopy())
                    mesh.vertices.size - 1
                }
                for (i in f.indices.indices) {
                    val a = f.indices[i]
                    val b = f.indices[(i + 1) % f.indices.size]
                    val na = newIdx[i]
                    val nb = newIdx[(i + 1) % newIdx.size]
                    mesh.faces.add(Face(mutableListOf(a, b, nb, na)))
                }
                mesh.faces.add(Face(newIdx.toMutableList()))
                mesh.faces.removeAt(fi)
                count++
            }
            mesh.computeNormals()
            OpResult(true, "Inset aplicado em $count face(s)", count)
        } catch (e: Exception) {
            OpResult(false, "Falha no inset: ${e.message}")
        }
    }

    /** Bevel (chanfro) real em vertices selecionados. */
    fun bevelVertices(mesh: MeshData, vertSel: Set<Int>, amount: Float): OpResult {
        return try {
            val sel = vertSel.filter { it in mesh.vertices.indices }
            if (sel.isEmpty()) return OpResult(false, "Nenhum vertice selecionado")
            var beveled = 0
            for (v in sel) {
                if (bevelOneVertex(mesh, v, amount)) beveled++
            }
            mesh.cleanup()
            if (beveled == 0) OpResult(false, "Topologia nao suportada para bevel nessa selecao")
            else OpResult(true, "Bevel aplicado em $beveled vertice(s)", beveled)
        } catch (e: Exception) {
            OpResult(false, "Falha no bevel: ${e.message}")
        }
    }

    private fun bevelOneVertex(mesh: MeshData, v: Int, amount: Float): Boolean {
        if (v !in mesh.vertices.indices) return false
        // Faces que contem v, com (prev, next)
        data class Adj(val faceIdx: Int, val pos: Int, val prev: Int, val next: Int)
        val adjs = mutableListOf<Adj>()
        for ((fi, f) in mesh.faces.withIndex()) {
            val p = f.indices.indexOf(v)
            if (p >= 0) {
                adjs.add(Adj(fi, p, f.indices[(p - 1 + f.indices.size) % f.indices.size], f.indices[(p + 1) % f.indices.size]))
            }
        }
        if (adjs.size < 2) return false
        // Ordena anel: caminha por pares (prev,next)
        val ring = mutableListOf<Int>()
        ring.add(adjs[0].prev); ring.add(adjs[0].next)
        val remaining = adjs.drop(1).toMutableList()
        var guard = 0
        while (remaining.isNotEmpty() && guard++ < 1024) {
            val last = ring.last()
            val k = remaining.indexOfFirst { it.prev == last || it.next == last }
            if (k < 0) break
            val a = remaining.removeAt(k)
            ring.add(if (a.prev == last) a.next else a.prev)
        }
        val closed = ring.first() == ring.last()
        val ringVerts = if (closed) ring.dropLast(1) else ring.toList()
        if (ringVerts.size < 2) return false
        // Distancia maxima segura
        var minLen = Float.MAX_VALUE
        for (r in ringVerts) minLen = minOf(minLen, mesh.vertices[v].distanceTo(mesh.vertices[r]))
        val dist = minOf(amount, minLen * 0.45f)
        if (dist <= 1e-6f) return false
        mesh.ensureUvs()
        // Cria pontos do chanfro
        val cutMap = HashMap<Int, Int>()
        for (r in ringVerts) {
            val dir = (mesh.vertices[r] - mesh.vertices[v]).normalized()
            val p = mesh.vertices[v] + dir * dist
            cutMap[r] = mesh.vertices.size
            mesh.vertices.add(p)
            mesh.uvs.add(mesh.uvs[v].deepCopy())
        }
        // Reescreve faces: v -> [cut(prev), cut(next)]
        for (a in adjs) {
            val f = mesh.faces[a.faceIdx]
            val cp = cutMap[a.prev] ?: return false
            val cn = cutMap[a.next] ?: return false
            val updated = mutableListOf<Int>()
            for ((i, idx) in f.indices.withIndex()) {
                if (i == a.pos) { updated.add(cp); updated.add(cn) } else updated.add(idx)
            }
            f.indices = updated
        }
        // Tampa
        if (closed) {
            mesh.faces.add(Face(ringVerts.map { cutMap[it]!! }.toMutableList()))
        }
        return true
    }

    /** Subdivide faces (ou todas) com pontos medios soldados. */
    fun subdivideFaces(mesh: MeshData, faceSel: Set<Int>?, levels: Int = 1): OpResult {
        return try {
            var total = 0
            repeat(maxOf(1, levels)) {
                val sel = if (faceSel == null) mesh.faces.indices.toSet()
                else faceSel.filter { it in mesh.faces.indices }.toSet()
                if (sel.isEmpty()) return@repeat
                mesh.ensureUvs()
                val midCache = HashMap<Pair<Int, Int>, Int>()
                fun midpoint(a: Int, b: Int): Int {
                    val k = edgeKey(a, b)
                    return midCache.getOrPut(k) {
                        val p = (mesh.vertices[a] + mesh.vertices[b]) * 0.5f
                        val uv = (mesh.uvs[a] + mesh.uvs[b]) * 0.5f
                        mesh.vertices.add(p); mesh.uvs.add(uv)
                        mesh.vertices.size - 1
                    }
                }
                val newFaces = mutableListOf<Face>()
                val oldSel = sel.sortedDescending()
                for (fi in oldSel) {
                    val f = mesh.faces[fi]
                    if (f.indices.size < 3) continue
                    val c = faceCentroid(mesh, f)
                    mesh.vertices.add(c)
                    var cu = Vec2(); for (i in f.indices) cu += mesh.uvs[i]
                    mesh.uvs.add(cu * (1f / f.indices.size))
                    val ci = mesh.vertices.size - 1
                    val n = f.indices.size
                    for (i in 0 until n) {
                        val vi = f.indices[i]
                        val vNext = f.indices[(i + 1) % n]
                        val vPrev = f.indices[(i - 1 + n) % n]
                        val mNext = midpoint(vi, vNext)
                        val mPrev = midpoint(vPrev, vi)
                        newFaces.add(Face(mutableListOf(vi, mNext, ci, mPrev)))
                    }
                    mesh.faces.removeAt(fi)
                    total++
                }
                mesh.faces.addAll(newFaces)
            }
            mesh.computeNormals()
            if (total == 0) OpResult(false, "Nada para subdividir")
            else OpResult(true, "Subdivide aplicado em $total face(s)", total)
        } catch (e: Exception) {
            OpResult(false, "Falha no subdivide: ${e.message}")
        }
    }

    /** Loop cut simples: divide quads/tris selecionados ao meio. */
    fun loopCutFaces(mesh: MeshData, faceSel: Set<Int>): OpResult {
        return try {
            val sel = faceSel.filter { it in mesh.faces.indices }.sortedDescending()
            if (sel.isEmpty()) return OpResult(false, "Nenhuma face selecionada")
            mesh.ensureUvs()
            val midCache = HashMap<Pair<Int, Int>, Int>()
            fun midpoint(a: Int, b: Int): Int {
                val k = edgeKey(a, b)
                return midCache.getOrPut(k) {
                    mesh.vertices.add((mesh.vertices[a] + mesh.vertices[b]) * 0.5f)
                    mesh.uvs.add((mesh.uvs[a] + mesh.uvs[b]) * 0.5f)
                    mesh.vertices.size - 1
                }
            }
            var count = 0
            for (fi in sel) {
                val f = mesh.faces[fi]
                when (f.indices.size) {
                    4 -> {
                        val (a, b, c, d) = f.indices
                        val m1 = midpoint(a, b); val m2 = midpoint(d, c)
                        mesh.faces.removeAt(fi)
                        mesh.faces.add(Face(mutableListOf(a, m1, m2, d)))
                        mesh.faces.add(Face(mutableListOf(m1, b, c, m2)))
                        count++
                    }
                    3 -> {
                        val idx = f.indices
                        var longest = 0; var best = -1f
                        for (i in 0..2) {
                            val l = mesh.vertices[idx[i]].distanceTo(mesh.vertices[idx[(i + 1) % 3]])
                            if (l > best) { best = l; longest = i }
                        }
                        val a = idx[longest]; val b = idx[(longest + 1) % 3]; val o = idx[(longest + 2) % 3]
                        val m = midpoint(a, b)
                        mesh.faces.removeAt(fi)
                        mesh.faces.add(Face(mutableListOf(a, m, o)))
                        mesh.faces.add(Face(mutableListOf(m, b, o)))
                        count++
                    }
                    else -> { /* ngons: ignorados */ }
                }
            }
            mesh.computeNormals()
            if (count == 0) OpResult(false, "Loop cut suporta apenas tris e quads")
            else OpResult(true, "Loop cut em $count face(s)", count)
        } catch (e: Exception) {
            OpResult(false, "Falha no loop cut: ${e.message}")
        }
    }

    fun mergeVertices(mesh: MeshData, vertSel: Set<Int>): OpResult {
        return try {
            val sel = vertSel.filter { it in mesh.vertices.indices }
            if (sel.size < 2) return OpResult(false, "Selecione 2+ vertices para merge")
            var c = Vec3()
            for (v in sel) c += mesh.vertices[v]
            c = c / sel.size.toFloat()
            val keep = sel.first()
            mesh.vertices[keep] = c
            for (f in mesh.faces) {
                f.indices = f.indices.map { if (it in sel) keep else it }.toMutableList()
            }
            mesh.cleanup()
            OpResult(true, "Merge: ${sel.size} vertices unidos", sel.size)
        } catch (e: Exception) {
            OpResult(false, "Falha no merge: ${e.message}")
        }
    }

    fun dissolveFaces(mesh: MeshData, faceSel: Set<Int>): OpResult {
        return try {
            val sel = faceSel.filter { it in mesh.faces.indices }.sortedDescending()
            if (sel.isEmpty()) return OpResult(false, "Nenhuma face selecionada")
            for (fi in sel) mesh.faces.removeAt(fi)
            mesh.cleanup()
            OpResult(true, "${sel.size} face(s) dissolvida(s)", sel.size)
        } catch (e: Exception) {
            OpResult(false, "Falha no dissolve: ${e.message}")
        }
    }

    fun deleteVertices(mesh: MeshData, vertSel: Set<Int>): OpResult {
        return try {
            val sel = vertSel.filter { it in mesh.vertices.indices }.toSet()
            if (sel.isEmpty()) return OpResult(false, "Nenhum vertice selecionado")
            mesh.faces.removeAll { f -> f.indices.any { it in sel } }
            mesh.cleanup()
            OpResult(true, "${sel.size} vertice(s) removido(s)", sel.size)
        } catch (e: Exception) {
            OpResult(false, "Falha ao deletar: ${e.message}")
        }
    }

    fun dissolveEdges(mesh: MeshData, edgeSel: Set<Pair<Int, Int>>): OpResult {
        return try {
            if (edgeSel.isEmpty()) return OpResult(false, "Nenhuma edge selecionada")
            val norm = edgeSel.map { edgeKey(it.first, it.second) }.toSet()
            mesh.faces.removeAll { f ->
                val idx = f.indices
                (idx.indices).any { i ->
                    edgeKey(idx[i], idx[(i + 1) % idx.size]) in norm
                }
            }
            mesh.cleanup()
            OpResult(true, "Edges dissolvidas", norm.size)
        } catch (e: Exception) {
            OpResult(false, "Falha no dissolve: ${e.message}")
        }
    }

    /** Separa faces selecionadas em uma nova malha. */
    fun separateFaces(mesh: MeshData, faceSel: Set<Int>): Pair<MeshData?, OpResult> {
        return try {
            val sel = faceSel.filter { it in mesh.faces.indices }
            if (sel.isEmpty()) return null to OpResult(false, "Nenhuma face selecionada")
            mesh.ensureUvs()
            val used = LinkedHashSet<Int>()
            for (fi in sel) used.addAll(mesh.faces[fi].indices)
            val remap = HashMap<Int, Int>()
            val out = MeshData()
            for (v in used) {
                remap[v] = out.vertices.size
                out.vertices.add(mesh.vertices[v].deepCopy())
                out.uvs.add(mesh.uvs[v].deepCopy())
            }
            for (fi in sel) {
                out.faces.add(Face(mesh.faces[fi].indices.map { remap[it]!! }.toMutableList()))
            }
            out.computeNormals()
            for (fi in sel.sortedDescending()) mesh.faces.removeAt(fi)
            mesh.cleanup()
            out to OpResult(true, "Separado em novo objeto", sel.size)
        } catch (e: Exception) {
            null to OpResult(false, "Falha no separate: ${e.message}")
        }
    }

    fun mirror(mesh: MeshData, axis: Int, clone: Boolean): OpResult {
        return try {
            if (clone) {
                val base = mesh.vertices.size
                mesh.ensureUvs()
                for (v in mesh.vertices.toList()) {
                    val p = v.deepCopy()
                    when (axis) {
                        0 -> p.x = -p.x; 1 -> p.y = -p.y; else -> p.z = -p.z
                    }
                    mesh.vertices.add(p)
                }
                for (u in mesh.uvs.toList().take(base)) mesh.uvs.add(u.deepCopy())
                val mirrored = mesh.faces.map { f ->
                    Face(f.indices.reversed().map { it + base }.toMutableList())
                }
                mesh.faces.addAll(mirrored)
            } else {
                for (v in mesh.vertices) {
                    when (axis) {
                        0 -> v.x = -v.x; 1 -> v.y = -v.y; else -> v.z = -v.z
                    }
                }
                for (f in mesh.faces) f.indices = f.indices.reversed().toMutableList()
            }
            mesh.computeNormals()
            OpResult(true, "Mirror aplicado", 1)
        } catch (e: Exception) {
            OpResult(false, "Falha no mirror: ${e.message}")
        }
    }

    /** Dizimacao ingenua por agrupamento espacial (para limite de poligonos). */
    fun decimateTo(mesh: MeshData, targetTris: Int): OpResult {
        return try {
            if (mesh.triCount() <= targetTris) return OpResult(true, "Malha ja dentro do limite", 0)
            var min = Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
            var max = Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            for (v in mesh.vertices) {
                min.x = minOf(min.x, v.x); min.y = minOf(min.y, v.y); min.z = minOf(min.z, v.z)
                max.x = maxOf(max.x, v.x); max.y = maxOf(max.y, v.y); max.z = maxOf(max.z, v.z)
            }
            val diag = (max - min).length().coerceAtLeast(1e-4f)
            var cell = diag / 64f
            var iter = 0
            while (mesh.triCount() > targetTris && iter++ < 10) {
                val buckets = HashMap<Triple<Int, Int, Int>, MutableList<Int>>()
                for (i in mesh.vertices.indices) {
                    val v = mesh.vertices[i]
                    val key = Triple(
                        ((v.x - min.x) / cell).toInt(),
                        ((v.y - min.y) / cell).toInt(),
                        ((v.z - min.z) / cell).toInt()
                    )
                    buckets.getOrPut(key) { mutableListOf() }.add(i)
                }
                val remap = IntArray(mesh.vertices.size) { it }
                for (list in buckets.values) {
                    if (list.size < 2) continue
                    var c = Vec3()
                    for (i in list) c += mesh.vertices[i]
                    c = c / list.size.toFloat()
                    val keep = list.first()
                    mesh.vertices[keep] = c
                    for (i in list) remap[i] = keep
                }
                for (f in mesh.faces) f.indices = f.indices.map { remap[it] }.toMutableList()
                mesh.cleanup()
                cell *= 1.6f
            }
            OpResult(true, "Malha simplificada para ${mesh.triCount()} tris", mesh.triCount())
        } catch (e: Exception) {
            OpResult(false, "Falha ao simplificar: ${e.message}")
        }
    }
}
