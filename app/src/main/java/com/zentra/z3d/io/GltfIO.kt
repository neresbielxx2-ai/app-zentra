package com.zentra.z3d.io

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.zentra.z3d.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

data class RichImport(
    val objects: List<SceneObject>,
    val materials: List<MaterialData>,
    val warnings: List<String>
)

/** Container binario GLB. */
object GlbIO {
    private const val MAGIC = 0x46546C67
    private const val JSON_CHUNK = 0x4E4F534A
    private const val BIN_CHUNK = 0x004E4942

    fun parse(bytes: ByteArray): Result<Pair<String, ByteArray>> = runCatching {
        if (bytes.size < 20) throw IllegalArgumentException("Arquivo GLB muito pequeno")
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buf.getInt(0)
        val version = buf.getInt(4)
        if (magic != MAGIC) throw IllegalArgumentException("Assinatura GLB invalida")
        if (version != 2) throw IllegalArgumentException("Versao GLB nao suportada: $version")
        var offset = 12
        var json: String? = null
        var bin = ByteArray(0)
        while (offset + 8 <= bytes.size) {
            val len = buf.getInt(offset)
            val type = buf.getInt(offset + 4)
            if (len < 0 || offset + 8 + len > bytes.size) throw IllegalArgumentException("GLB truncado")
            val chunk = bytes.copyOfRange(offset + 8, offset + 8 + len)
            if (type == JSON_CHUNK) json = String(chunk, Charsets.UTF_8).trimEnd(' ', '\u0000', '\r', '\n')
            else if (type == BIN_CHUNK) bin = chunk
            offset += 8 + len
        }
        val j = json ?: throw IllegalArgumentException("Chunk JSON nao encontrado no GLB")
        j to bin
    }

    fun build(json: String, bin: ByteArray): ByteArray {
        val jsonBytes = json.toByteArray(Charsets.UTF_8)
        val jsonPad = (4 - jsonBytes.size % 4) % 4
        val binPad = (4 - bin.size % 4) % 4
        val total = 12 + 8 + jsonBytes.size + jsonPad + 8 + bin.size + binPad
        val buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(MAGIC); buf.putInt(2); buf.putInt(total)
        buf.putInt(jsonBytes.size + jsonPad); buf.putInt(JSON_CHUNK)
        buf.put(jsonBytes)
        repeat(jsonPad) { buf.put(0x20) }
        buf.putInt(bin.size + binPad); buf.putInt(BIN_CHUNK)
        buf.put(bin)
        repeat(binPad) { buf.put(0) }
        return buf.array()
    }
}

/** Exportador glTF 2.0 minimo e valido. */
object GltfExport {

    fun build(scene: Scene, objects: List<SceneObject>, textureBytesOf: (String) -> ByteArray?): Pair<String, ByteArray> {
        val meshes = objects.filter { it.mesh != null && it.mesh!!.vertices.isNotEmpty() }
        require(meshes.isNotEmpty()) { "Nenhum objeto com malha para exportar" }

        val binOut = ByteArrayOutputStream()
        fun pad4() { while (binOut.size() % 4 != 0) binOut.write(0) }
        fun writeFloats(data: FloatArray): Int {
            pad4()
            val off = binOut.size()
            val bb = ByteBuffer.allocate(data.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            bb.asFloatBuffer().put(data)
            binOut.write(bb.array())
            return off
        }
        fun writeInts(data: IntArray): Int {
            pad4()
            val off = binOut.size()
            val bb = ByteBuffer.allocate(data.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            bb.asIntBuffer().put(data)
            binOut.write(bb.array())
            return off
        }
        fun writeRaw(data: ByteArray): Int {
            pad4()
            val off = binOut.size()
            binOut.write(data)
            return off
        }

        val bufferViews = JSONArray()
        val accessors = JSONArray()
        fun addView(byteOffset: Int, byteLength: Int): Int {
            val o = JSONObject()
            o.put("buffer", 0)
            o.put("byteOffset", byteOffset)
            o.put("byteLength", byteLength)
            bufferViews.put(o)
            return bufferViews.length() - 1
        }
        fun addAccessor(view: Int, count: Int, type: String, comp: Int, min: JSONArray?, max: JSONArray?): Int {
            val o = JSONObject()
            o.put("bufferView", view)
            o.put("count", count)
            o.put("type", type)
            o.put("componentType", comp)
            if (min != null) o.put("min", min)
            if (max != null) o.put("max", max)
            accessors.put(o)
            return accessors.length() - 1
        }

        // Materiais
        val matIndex = HashMap<String, Int>()
        val gltfMats = JSONArray()
        val images = JSONArray()
        val textures = JSONArray()
        fun materialIdxOf(obj: SceneObject): Int? {
            val mat = scene.materialOf(obj) ?: return null
            matIndex[mat.id]?.let { return it }
            val pbr = JSONObject()
            pbr.put("baseColorFactor", JSONArray(listOf(mat.baseColor.r, mat.baseColor.g, mat.baseColor.b, mat.opacity)))
            pbr.put("metallicFactor", mat.metallic)
            pbr.put("roughnessFactor", mat.roughness)
            val tf = mat.textureFile
            if (tf != null) {
                val bytes = runCatching { textureBytesOf(tf) }.getOrNull()
                if (bytes != null && bytes.isNotEmpty()) {
                    val off = writeRaw(bytes)
                    val view = addView(off, bytes.size)
                    val img = JSONObject()
                    img.put("bufferView", view)
                    img.put("mimeType", "image/png")
                    images.put(img)
                    val tx = JSONObject()
                    tx.put("source", images.length() - 1)
                    textures.put(tx)
                    val bct = JSONObject()
                    bct.put("index", textures.length() - 1)
                    pbr.put("baseColorTexture", bct)
                }
            }
            val m = JSONObject()
            m.put("name", mat.name)
            m.put("pbrMetallicRoughness", pbr)
            m.put("emissiveFactor", JSONArray(listOf(mat.emission.r * mat.emissionStrength, mat.emission.g * mat.emissionStrength, mat.emission.b * mat.emissionStrength)))
            m.put("alphaMode", if (mat.opacity < 0.99f) "BLEND" else "OPAQUE")
            m.put("doubleSided", true)
            gltfMats.put(m)
            val idx = gltfMats.length() - 1
            matIndex[mat.id] = idx
            return idx
        }

        // Meshes
        val gltfMeshes = JSONArray()
        val meshIdxOf = HashMap<String, Int>()
        for (obj in meshes) {
            val mesh = obj.mesh!!
            mesh.ensureUvs()
            if (mesh.normals.size != mesh.vertices.size) mesh.computeNormals()
            val n = mesh.vertices.size
            val posArr = FloatArray(n * 3)
            val norArr = FloatArray(n * 3)
            val uvArr = FloatArray(n * 2)
            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
            for (i in 0 until n) {
                val v = mesh.vertices[i]
                posArr[i * 3] = v.x; posArr[i * 3 + 1] = v.y; posArr[i * 3 + 2] = v.z
                minX = minOf(minX, v.x); minY = minOf(minY, v.y); minZ = minOf(minZ, v.z)
                maxX = maxOf(maxX, v.x); maxY = maxOf(maxY, v.y); maxZ = maxOf(maxZ, v.z)
                val nr = mesh.normals[i]
                norArr[i * 3] = nr.x; norArr[i * 3 + 1] = nr.y; norArr[i * 3 + 2] = nr.z
                val uv = mesh.uvs[i]
                uvArr[i * 2] = uv.x; uvArr[i * 2 + 1] = uv.y
            }
            val tris = mesh.triangleIndices()
            val posAcc = addAccessor(addView(writeFloats(posArr), posArr.size * 4), n, "VEC3", 5126,
                JSONArray(listOf(minX, minY, minZ)), JSONArray(listOf(maxX, maxY, maxZ)))
            val norAcc = addAccessor(addView(writeFloats(norArr), norArr.size * 4), n, "VEC3", 5126, null, null)
            val uvAcc = addAccessor(addView(writeFloats(uvArr), uvArr.size * 4), n, "VEC2", 5126, null, null)
            val idxAcc = addAccessor(addView(writeInts(tris), tris.size * 4), tris.size, "SCALAR", 5125, null, null)
            val attrs = JSONObject()
            attrs.put("POSITION", posAcc)
            attrs.put("NORMAL", norAcc)
            attrs.put("TEXCOORD_0", uvAcc)
            val prim = JSONObject()
            prim.put("attributes", attrs)
            prim.put("indices", idxAcc)
            prim.put("mode", 4)
            materialIdxOf(obj)?.let { prim.put("material", it) }
            val m = JSONObject()
            m.put("name", obj.name)
            m.put("primitives", JSONArray().put(prim))
            gltfMeshes.put(m)
            meshIdxOf[obj.id] = gltfMeshes.length() - 1
        }

        // Nodes + hierarquia
        val exportIds = meshes.map { it.id }.toSet()
        val nodeIdxOf = HashMap<String, Int>()
        val gltfNodes = JSONArray()
        for (obj in meshes) {
            val o = JSONObject()
            o.put("name", obj.name)
            o.put("matrix", JSONArray(obj.transform.matrix().m.toList()))
            meshIdxOf[obj.id]?.let { o.put("mesh", it) }
            gltfNodes.put(o)
            nodeIdxOf[obj.id] = gltfNodes.length() - 1
        }
        val rootNodes = mutableListOf<Int>()
        for (obj in meshes) {
            val myIdx = nodeIdxOf[obj.id]!!
            val parentIdx = obj.parentId?.let { nodeIdxOf[it] }
            if (parentIdx != null) {
                val parent = gltfNodes.getJSONObject(parentIdx)
                val kids = parent.optJSONArray("children") ?: JSONArray().also { parent.put("children", it) }
                kids.put(myIdx)
            } else {
                rootNodes.add(myIdx)
            }
        }

        val bin = binOut.toByteArray()
        val root = JSONObject()
        val asset = JSONObject()
        asset.put("version", "2.0")
        asset.put("generator", "Zentra 3D")
        root.put("asset", asset)
        root.put("scene", 0)
        val sceneArr = JSONArray()
        val sc = JSONObject()
        sc.put("name", scene.name)
        sc.put("nodes", JSONArray(rootNodes))
        sceneArr.put(sc)
        root.put("scenes", sceneArr)
        root.put("nodes", gltfNodes)
        root.put("meshes", gltfMeshes)
        if (gltfMats.length() > 0) root.put("materials", gltfMats)
        if (images.length() > 0) root.put("images", images)
        if (textures.length() > 0) root.put("textures", textures)
        root.put("accessors", accessors)
        root.put("bufferViews", bufferViews)
        val buffers = JSONArray()
        val b0 = JSONObject()
        b0.put("byteLength", bin.size)
        buffers.put(b0)
        root.put("buffers", buffers)
        return root.toString() to bin
    }

    /** Gera .gltf de arquivo unico com buffer embutido em base64. */
    fun buildEmbeddedGltf(scene: Scene, objects: List<SceneObject>, textureBytesOf: (String) -> ByteArray?): String {
        val (json, bin) = build(scene, objects, textureBytesOf)
        val root = JSONObject(json)
        val b64 = Base64.encodeToString(bin, Base64.NO_WRAP)
        root.getJSONArray("buffers").getJSONObject(0)
            .put("uri", "data:application/octet-stream;base64,$b64")
        return root.toString()
    }
}

/** Importador glTF 2.0 (subset solido: malhas, materiais PBR, texturas, cameras, luzes punctuais). */
object GltfImport {

    fun importGltf(
        json: String,
        glbBin: ByteArray?,
        baseName: String,
        defaultMatId: String?,
        saveTexture: (suggestedName: String, bytes: ByteArray) -> String?
    ): Result<RichImport> = runCatching {
        val warnings = mutableListOf<String>()
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("JSON glTF invalido: ${e.message}")
        }
        if (root.has("extensionsRequired")) {
            val req = root.getJSONArray("extensionsRequired")
            for (i in 0 until req.length()) {
                val ext = req.optString(i)
                if (ext == "KHR_draco_mesh_compression") {
                    throw IllegalArgumentException("Malha com compressao Draco nao suportada")
                }
                warnings.add("Extensao requerida ignorada: $ext")
            }
        }
        // Buffers
        val buffers = ArrayList<ByteArray>()
        val jBuffers = root.optJSONArray("buffers") ?: JSONArray()
        for (i in 0 until jBuffers.length()) {
            val b = jBuffers.getJSONObject(i)
            val uri = b.optString("uri", null)
            when {
                uri == null && glbBin != null && i == 0 -> buffers.add(glbBin)
                uri == null -> throw IllegalArgumentException("Buffer $i sem dados (use GLB ou glTF embutido)")
                uri.startsWith("data:") -> {
                    val comma = uri.indexOf(',')
                    if (comma < 0) throw IllegalArgumentException("Data URI invalida no buffer $i")
                    buffers.add(Base64.decode(uri.substring(comma + 1), Base64.DEFAULT))
                }
                else -> throw IllegalArgumentException(
                    "glTF com .bin externo nao suportado via seletor de arquivos. Use GLB ou glTF com buffer embutido."
                )
            }
        }
        if (buffers.isEmpty()) throw IllegalArgumentException("Nenhum buffer encontrado")

        val jViews = root.optJSONArray("bufferViews") ?: JSONArray()
        val jAcc = root.optJSONArray("accessors") ?: JSONArray()

        fun readAccessor(accIdx: Int): AccessorData {
            val acc = jAcc.getJSONObject(accIdx)
            if (acc.has("sparse")) throw IllegalArgumentException("Accessors esparsos nao suportados")
            val viewIdx = acc.getInt("bufferView")
            val view = jViews.getJSONObject(viewIdx)
            val bufIdx = view.optInt("buffer", 0)
            val buf = buffers.getOrNull(bufIdx) ?: throw IllegalArgumentException("Buffer $bufIdx ausente")
            val viewOff = view.optInt("byteOffset", 0)
            val accOff = acc.optInt("byteOffset", 0)
            val count = acc.getInt("count")
            val type = acc.getString("type")
            val comp = acc.getInt("componentType")
            val numComp = when (type) {
                "SCALAR" -> 1; "VEC2" -> 2; "VEC3" -> 3; "VEC4" -> 4
                else -> throw IllegalArgumentException("Tipo de accessor nao suportado: $type")
            }
            val compSize = when (comp) {
                5120, 5121 -> 1
                5122, 5123 -> 2
                5125, 5126 -> 4
                else -> throw IllegalArgumentException("ComponentType nao suportado: $comp")
            }
            val stride = view.optInt("byteStride", numComp * compSize)
            val normalized = acc.optBoolean("normalized", false)
            val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
            val floats = FloatArray(count * numComp)
            val ints = IntArray(count * numComp)
            var isFloat = comp == 5126
            for (c in 0 until count) {
                for (k in 0 until numComp) {
                    val pos = viewOff + accOff + c * stride + k * compSize
                    if (pos + compSize > buf.size) throw IllegalArgumentException("Accessor fora dos limites do buffer")
                    when (comp) {
                        5126 -> floats[c * numComp + k] = bb.getFloat(pos)
                        5121 -> {
                            val v = buf[pos].toInt() and 0xFF
                            if (normalized) floats[c * numComp + k] = v / 255f else ints[c * numComp + k] = v
                            if (normalized) isFloat = true
                        }
                        5123 -> {
                            val v = bb.getShort(pos).toInt() and 0xFFFF
                            if (normalized) floats[c * numComp + k] = v / 65535f else ints[c * numComp + k] = v
                            if (normalized) isFloat = true
                        }
                        5125 -> {
                            val v = bb.getInt(pos)
                            if (v < 0) throw IllegalArgumentException("Indice uint32 fora do limite suportado")
                            ints[c * numComp + k] = v
                        }
                        5120 -> {
                            val v = buf[pos].toInt()
                            floats[c * numComp + k] = (v / 127f).coerceIn(-1f, 1f); isFloat = true
                        }
                        5122 -> {
                            val v = bb.getShort(pos).toInt()
                            floats[c * numComp + k] = (v / 32767f).coerceIn(-1f, 1f); isFloat = true
                        }
                    }
                }
            }
            return AccessorData(numComp, count, floats, ints, isFloat)
        }

        // Materiais
        val materials = mutableListOf<MaterialData>()
        val matIdOf = HashMap<Int, String>()
        val jMats = root.optJSONArray("materials") ?: JSONArray()
        val jImages = root.optJSONArray("images") ?: JSONArray()
        val jTextures = root.optJSONArray("textures") ?: JSONArray()

        fun imageBytes(imgIdx: Int): ByteArray? {
            if (imgIdx !in 0 until jImages.length()) return null
            val img = jImages.getJSONObject(imgIdx)
            if (img.has("bufferView")) {
                val view = jViews.getJSONObject(img.getInt("bufferView"))
                val buf = buffers.getOrNull(view.optInt("buffer", 0)) ?: return null
                val off = view.optInt("byteOffset", 0)
                val len = view.getInt("byteLength")
                if (off + len > buf.size) return null
                return buf.copyOfRange(off, off + len)
            }
            val uri = img.optString("uri", null)
            if (uri != null && uri.startsWith("data:")) {
                val comma = uri.indexOf(',')
                if (comma > 0) return Base64.decode(uri.substring(comma + 1), Base64.DEFAULT)
            }
            return null
        }

        for (mi in 0 until jMats.length()) {
            val jm = jMats.getJSONObject(mi)
            val mat = MaterialData(name = jm.optString("name", "Mat$mi").ifEmpty { "Mat$mi" })
            val pbr = jm.optJSONObject("pbrMetallicRoughness")
            if (pbr != null) {
                val bcf = pbr.optJSONArray("baseColorFactor")
                if (bcf != null && bcf.length() >= 3) {
                    mat.baseColor = Rgba(
                        bcf.optDouble(0, 1.0).toFloat(),
                        bcf.optDouble(1, 1.0).toFloat(),
                        bcf.optDouble(2, 1.0).toFloat(), 1f
                    )
                    if (bcf.length() >= 4) mat.opacity = bcf.optDouble(3, 1.0).toFloat()
                }
                mat.metallic = pbr.optDouble("metallicFactor", 0.0).toFloat()
                mat.roughness = pbr.optDouble("roughnessFactor", 0.55).toFloat()
                val bct = pbr.optJSONObject("baseColorTexture")
                if (bct != null) {
                    val txIdx = bct.optInt("index", -1)
                    if (txIdx in 0 until jTextures.length()) {
                        val srcIdx = jTextures.getJSONObject(txIdx).optInt("source", -1)
                        val bytes = runCatching { imageBytes(srcIdx) }.getOrNull()
                        if (bytes != null) {
                            val stored = runCatching {
                                saveTexture("gltf_tex$txIdx.png", bytes)
                            }.getOrNull()
                            if (stored != null) mat.textureFile = stored
                            else warnings.add("Textura $txIdx nao pode ser salva")
                        } else warnings.add("Textura $txIdx com fonte externa (ignorada)")
                    }
                }
            }
            val em = jm.optJSONArray("emissiveFactor")
            if (em != null && em.length() >= 3) {
                mat.emission = Rgba(
                    em.optDouble(0, 0.0).toFloat(),
                    em.optDouble(1, 0.0).toFloat(),
                    em.optDouble(2, 0.0).toFloat(), 1f
                )
                if (mat.emission.r + mat.emission.g + mat.emission.b > 0.01f) mat.emissionStrength = 1f
            }
            materials.add(mat)
            matIdOf[mi] = mat.id
        }

        // Meshes -> dados geometricos por primitiva
        data class PrimMesh(val mesh: MeshData, val matIdx: Int?)
        val jMeshes = root.optJSONArray("meshes") ?: JSONArray()
        val primMeshes = HashMap<Int, MutableList<PrimMesh>>()
        for (mi in 0 until jMeshes.length()) {
            val jm = jMeshes.getJSONObject(mi)
            val mName = jm.optString("name", "mesh$mi")
            val prims = jm.optJSONArray("primitives") ?: JSONArray()
            val list = mutableListOf<PrimMesh>()
            for (pi in 0 until prims.length()) {
                val prim = prims.getJSONObject(pi)
                val mode = prim.optInt("mode", 4)
                if (mode != 4) {
                    warnings.add("$mName: modo de primitiva $mode ignorado (so triangulos)")
                    continue
                }
                val attrs = prim.getJSONObject("attributes")
                if (!attrs.has("POSITION")) {
                    warnings.add("$mName: primitiva sem POSITION (ignorada)")
                    continue
                }
                val pos = readAccessor(attrs.getInt("POSITION"))
                val mesh = MeshData()
                for (c in 0 until pos.count) {
                    mesh.vertices.add(Vec3(pos.floats[c * 3], pos.floats[c * 3 + 1], pos.floats[c * 3 + 2]))
                }
                if (attrs.has("NORMAL")) {
                    val nrm = readAccessor(attrs.getInt("NORMAL"))
                    for (c in 0 until minOf(nrm.count, pos.count)) {
                        mesh.normals.add(Vec3(nrm.floats[c * 3], nrm.floats[c * 3 + 1], nrm.floats[c * 3 + 2]))
                    }
                }
                if (attrs.has("TEXCOORD_0")) {
                    val tx = readAccessor(attrs.getInt("TEXCOORD_0"))
                    for (c in 0 until minOf(tx.count, pos.count)) {
                        mesh.uvs.add(Vec2(tx.floats[c * 2], tx.floats[c * 2 + 1]))
                    }
                }
                mesh.ensureUvs()
                if (mesh.normals.size != mesh.vertices.size) mesh.computeNormals()
                if (prim.has("indices")) {
                    val idx = readAccessor(prim.getInt("indices"))
                    val arr = if (idx.isFloat) IntArray(idx.count) { idx.floats[it].toInt() } else idx.ints
                    var i = 0
                    while (i + 2 < arr.size) {
                        val a = arr[i]; val b = arr[i + 1]; val c = arr[i + 2]
                        if (a in mesh.vertices.indices && b in mesh.vertices.indices && c in mesh.vertices.indices) {
                            if (a != b && b != c && a != c) mesh.faces.add(Face(mutableListOf(a, b, c)))
                        }
                        i += 3
                    }
                } else {
                    var i = 0
                    while (i + 2 < mesh.vertices.size) {
                        mesh.faces.add(Face(mutableListOf(i, i + 1, i + 2)))
                        i += 3
                    }
                }
                if (mesh.faces.isEmpty()) {
                    warnings.add("$mName: primitiva sem faces validas")
                    continue
                }
                list.add(PrimMesh(mesh, prim.optInt("material", -1).takeIf { it >= 0 }))
            }
            primMeshes[mi] = list
        }

        // Cameras e luzes
        val jCams = root.optJSONArray("cameras") ?: JSONArray()
        data class CamDef(val fovDeg: Float, val near: Float, val far: Float)
        val camDefs = HashMap<Int, CamDef>()
        for (ci in 0 until jCams.length()) {
            val jc = jCams.getJSONObject(ci)
            if (jc.optString("type") == "perspective") {
                val p = jc.getJSONObject("perspective")
                camDefs[ci] = CamDef(
                    p.optDouble("yfov", 0.8).toFloat() * RAD2DEG,
                    p.optDouble("znear", 0.1).toFloat(),
                    p.optDouble("zfar", 300.0).toFloat()
                )
            } else {
                camDefs[ci] = CamDef(40f, 0.1f, 300f)
            }
        }
        data class LightDef(val kind: LightKind, val color: Rgba, val intensity: Float, val range: Float, val angle: Float)
        val lightDefs = HashMap<Int, LightDef>()
        val ext = root.optJSONObject("extensions")
        val klp = ext?.optJSONObject("KHR_lights_punctual")
        val jLights = klp?.optJSONArray("lights")
        if (jLights != null) {
            for (li in 0 until jLights.length()) {
                val jl = jLights.getJSONObject(li)
                val kind = when (jl.optString("type")) {
                    "point" -> LightKind.POINT
                    "spot" -> LightKind.SPOT
                    "directional" -> LightKind.DIRECTIONAL
                    else -> LightKind.POINT
                }
                val col = jl.optJSONArray("color")
                val spot = jl.optJSONObject("spot")
                lightDefs[li] = LightDef(
                    kind,
                    if (col != null && col.length() >= 3) Rgba(col.optDouble(0, 1.0).toFloat(), col.optDouble(1, 1.0).toFloat(), col.optDouble(2, 1.0).toFloat(), 1f)
                    else Rgba(1f, 1f, 1f, 1f),
                    jl.optDouble("intensity", 10.0).toFloat(),
                    jl.optDouble("range", 30.0).toFloat(),
                    (spot?.optDouble("outerConeAngle", 0.7)?.toFloat() ?: 0.7f) * RAD2DEG * 2f
                )
            }
        }

        // Nodes
        val jNodes = root.optJSONArray("nodes") ?: JSONArray()
        val objects = mutableListOf<SceneObject>()
        val nodeObjIds = HashMap<Int, String>()
        for (ni in 0 until jNodes.length()) {
            val jn = jNodes.getJSONObject(ni)
            val tr = nodeTransform(jn)
            val nName = jn.optString("name", "$baseName$ni").ifEmpty { "$baseName$ni" }
            if (jn.has("mesh")) {
                val meshIdx = jn.getInt("mesh")
                val prims = primMeshes[meshIdx]
                if (prims.isNullOrEmpty()) {
                    warnings.add("Node $nName referencia malha vazia")
                    continue
                }
                for ((pi, pm) in prims.withIndex()) {
                    val matId = pm.matIdx?.let { matIdOf[it] } ?: defaultMatId
                    val obj = SceneObject(
                        name = if (prims.size == 1) nName.take(64) else "$nName-p$pi",
                        type = ObjectType.MESH,
                        transform = if (pi == 0) tr else tr.deepCopy(),
                        mesh = pm.mesh,
                        materialId = matId
                    )
                    objects.add(obj)
                    if (pi == 0) nodeObjIds[ni] = obj.id
                }
            } else if (jn.has("camera")) {
                val def = camDefs[jn.getInt("camera")] ?: CamDef(50f, 0.1f, 300f)
                val obj = SceneObject(
                    name = nName.take(64), type = ObjectType.CAMERA,
                    transform = tr, camera = CameraData(def.fovDeg, def.near, def.far, false)
                )
                objects.add(obj)
                nodeObjIds[ni] = obj.id
            } else {
                val lightIdx = jn.optJSONObject("extensions")
                    ?.optJSONObject("KHR_lights_punctual")?.optInt("light", -1) ?: -1
                if (lightIdx >= 0 && lightDefs.containsKey(lightIdx)) {
                    val def = lightDefs[lightIdx]!!
                    val obj = SceneObject(
                        name = nName.take(64), type = ObjectType.LIGHT,
                        transform = tr,
                        light = LightData(def.kind, def.color, def.intensity, def.range, def.angle, 0.4f, false)
                    )
                    objects.add(obj)
                    nodeObjIds[ni] = obj.id
                }
            }
        }
        // Hierarquia de pais
        for (ni in 0 until jNodes.length()) {
            val kids = jNodes.getJSONObject(ni).optJSONArray("children") ?: continue
            val parentId = nodeObjIds[ni] ?: continue
            for (k in 0 until kids.length()) {
                val childId = nodeObjIds[kids.optInt(k, -1)] ?: continue
                objects.find { it.id == childId }?.parentId = parentId
            }
        }
        if (objects.isEmpty()) throw IllegalArgumentException("Nenhum objeto importavel no glTF")
        if (root.has("animations")) warnings.add("Animacoes do arquivo ignoradas (use a timeline do Zentra)")
        if (root.has("skins")) warnings.add("Skins ignoradas")
        RichImport(objects, materials, warnings)
    }

    private data class AccessorData(
        val numComp: Int,
        val count: Int,
        val floats: FloatArray,
        val ints: IntArray,
        val isFloat: Boolean
    )

    private fun nodeTransform(jn: JSONObject): Transform {
        if (jn.has("matrix")) {
            val arr = jn.getJSONArray("matrix")
            val m = FloatArray(16) { i -> arr.optDouble(i, if (i % 5 == 0) 1.0 else 0.0).toFloat() }
            return decomposeToTransform(Mat4(m))
        }
        val t = jn.optJSONArray("translation")
        val r = jn.optJSONArray("rotation")
        val s = jn.optJSONArray("scale")
        val pos = if (t != null && t.length() >= 3) Vec3(t.optDouble(0, 0.0).toFloat(), t.optDouble(1, 0.0).toFloat(), t.optDouble(2, 0.0).toFloat()) else Vec3()
        val scl = if (s != null && s.length() >= 3) Vec3(s.optDouble(0, 1.0).toFloat(), s.optDouble(1, 1.0).toFloat(), s.optDouble(2, 1.0).toFloat()) else Vec3(1f, 1f, 1f)
        var euler = Vec3()
        if (r != null && r.length() >= 4) {
            val qx = r.optDouble(0, 0.0).toFloat()
            val qy = r.optDouble(1, 0.0).toFloat()
            val qz = r.optDouble(2, 0.0).toFloat()
            val qw = r.optDouble(3, 1.0).toFloat()
            euler = quatToEulerYXZ(qx, qy, qz, qw)
        }
        return Transform(pos, euler, scl)
    }

    /** Decompoe matriz column-major em T/R(euler YXZ graus)/S. */
    fun decomposeToTransform(m: Mat4): Transform {
        val a = m.m
        val pos = Vec3(a[12], a[13], a[14])
        val sx = Vec3(a[0], a[1], a[2]).length()
        val sy = Vec3(a[4], a[5], a[6]).length()
        val sz = Vec3(a[8], a[9], a[10]).length()
        val scl = Vec3(sx.coerceAtLeast(1e-6f), sy.coerceAtLeast(1e-6f), sz.coerceAtLeast(1e-6f))
        // Rotacao normalizada
        val r00 = a[0] / scl.x; val r01 = a[4] / scl.y; val r02 = a[8] / scl.z
        val r10 = a[1] / scl.x; val r11 = a[5] / scl.y; val r12 = a[9] / scl.z
        val r20 = a[2] / scl.x; val r21 = a[6] / scl.y; val r22 = a[10] / scl.z
        // R = Ry * Rx * Rz  =>  x = asin(-r12)
        val x: Float; val y: Float; val z: Float
        val sy2 = -r12
        if (abs(sy2) < 0.99999f) {
            x = asin(sy2.coerceIn(-1f, 1f))
            y = atan2(r02, r22)
            z = atan2(r10, r11)
        } else {
            x = (if (sy2 > 0) PI.toFloat() / 2f else -PI.toFloat() / 2f)
            y = atan2(-r20, r00)
            z = 0f
        }
        return Transform(pos, Vec3(x * RAD2DEG, y * RAD2DEG, z * RAD2DEG), scl)
    }

    private fun quatToEulerYXZ(qx: Float, qy: Float, qz: Float, qw: Float): Vec3 {
        val n = sqrt(qx * qx + qy * qy + qz * qz + qw * qw).coerceAtLeast(1e-8f)
        val x = qx / n; val y = qy / n; val z = qz / n; val w = qw / n
        // Matriz 3x3 (row-major)
        val r00 = 1f - 2f * (y * y + z * z)
        val r01 = 2f * (x * y - z * w)
        val r02 = 2f * (x * z + y * w)
        val r10 = 2f * (x * y + z * w)
        val r11 = 1f - 2f * (x * x + z * z)
        val r12 = 2f * (y * z - x * w)
        val r20 = 2f * (x * z - y * w)
        val r22 = 1f - 2f * (x * x + y * y)
        val sy2 = -r12
        return if (abs(sy2) < 0.99999f) {
            Vec3(
                asin(sy2.coerceIn(-1f, 1f)) * RAD2DEG,
                atan2(r02, r22) * RAD2DEG,
                atan2(r10, r11) * RAD2DEG
            )
        } else {
            Vec3(
                (if (sy2 > 0) 90f else -90f),
                atan2(-r20, r00) * RAD2DEG,
                0f
            )
        }
    }
}

/** Fachada de importacao via Storage Access Framework. */
object ModelImporter {
    const val MAX_BYTES = 150 * 1024 * 1024

    data class Outcome(
        val objects: List<SceneObject>,
        val materials: List<MaterialData>,
        val warnings: List<String>,
        val triCount: Int,
        val suggestion: String
    )

    fun displayName(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) ?: "modelo"
                else "modelo"
            } ?: "modelo"
        } catch (_: Exception) { "modelo" }
    }

    fun importFromUri(
        context: Context,
        uri: Uri,
        defaultMatId: String?,
        saveTexture: (suggestedName: String, bytes: ByteArray) -> String?
    ): Result<Outcome> = runCatching {
        val name = displayName(context, uri)
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext !in setOf("obj", "stl", "gltf", "glb")) {
            throw IllegalArgumentException("Formato '.$ext' nao suportado. Use OBJ, STL, GLTF ou GLB.")
        }
        val bytes = context.contentResolver.openInputStream(uri)?.use { ins ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val r = ins.read(buf)
                if (r <= 0) break
                total += r
                if (total > MAX_BYTES) throw IllegalArgumentException("Arquivo maior que 150 MB")
                out.write(buf, 0, r)
            }
            out.toByteArray()
        } ?: throw IllegalArgumentException("Nao foi possivel abrir o arquivo")
        if (bytes.isEmpty()) throw IllegalArgumentException("Arquivo vazio")

        val base = name.substringBeforeLast('.').ifEmpty { "Modelo" }.take(48)
        val rich: RichImport = when (ext) {
            "obj" -> {
                val imp = ObjIO.importObj(String(bytes, Charsets.UTF_8), defaultMatId, base).getOrThrow()
                RichImport(imp.objects, emptyList(), imp.warnings)
            }
            "stl" -> {
                val imp = StlIO.importBytes(bytes, defaultMatId, base).getOrThrow()
                RichImport(imp.objects, emptyList(), imp.warnings)
            }
            "glb" -> {
                val (json, bin) = GlbIO.parse(bytes).getOrThrow()
                GltfImport.importGltf(json, bin, base, defaultMatId, saveTexture).getOrThrow()
            }
            else -> {
                GltfImport.importGltf(String(bytes, Charsets.UTF_8), null, base, defaultMatId, saveTexture).getOrThrow()
            }
        }
        var tris = 0
        for (o in rich.objects) tris += o.mesh?.triCount() ?: 0
        Outcome(rich.objects, rich.materials, rich.warnings, tris, name)
    }
}

/** Fachada de exportacao via Storage Access Framework. */
object ModelExporter {

    fun exportToUri(
        context: Context,
        uri: Uri,
        ext: String,
        scene: Scene,
        objects: List<SceneObject>,
        textureBytesOf: (String) -> ByteArray?
    ): Result<String> = runCatching {
        val meshes = objects.filter { it.mesh != null && (it.type == ObjectType.MESH || it.type == ObjectType.TEXT) }
        if (meshes.isEmpty()) throw IllegalArgumentException("Nenhum objeto com malha para exportar")
        var tris = 0
        for (o in meshes) tris += o.mesh?.triCount() ?: 0
        val payload: ByteArray = when (ext.lowercase()) {
            "obj" -> ObjIO.export(meshes).toByteArray(Charsets.UTF_8)
            "stl" -> StlIO.exportBinary(meshes)
            "glb" -> {
                val (json, bin) = GltfExport.build(scene, meshes, textureBytesOf)
                GlbIO.build(json, bin)
            }
            "gltf" -> GltfExport.buildEmbeddedGltf(scene, meshes, textureBytesOf).toByteArray(Charsets.UTF_8)
            else -> throw IllegalArgumentException("Formato '.$ext' nao suportado")
        }
        context.contentResolver.openOutputStream(uri)?.use { it.write(payload) }
            ?: throw IllegalArgumentException("Nao foi possivel gravar o arquivo")
        "Exportado: ${meshes.size} objeto(s), $tris triangulos"
    }
}
