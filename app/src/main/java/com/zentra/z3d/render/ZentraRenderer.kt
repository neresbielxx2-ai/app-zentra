package com.zentra.z3d.render
import kotlin.math.*

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.util.Log
import com.zentra.z3d.core.*
import com.zentra.z3d.settings.PerformanceManager
import com.zentra.z3d.settings.Quality
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.concurrent.ConcurrentHashMap
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Ponte thread-safe entre ViewModel (UI) e thread GL. Tudo sob [lock]. */
class RenderBridge {
    val lock = Any()
    var scene: Scene? = null
    var camera: CameraController? = null
    var selectedIds: Set<String> = emptySet()
    var activeId: String? = null
    var editMode: Boolean = false
    var editComponent: Int = 0 // 0 vertice, 1 edge, 2 face
    var editSelection: Set<Int> = emptySet()
    var editEdges: Set<Pair<Int, Int>> = emptySet()
    var toolGizmo: Int = 0 // 0 nenhum, 1 mover, 2 rotacionar, 3 escalar
    var axisLock: Int = 0 // 0 all, 1 X, 2 Y, 3 Z
    var showGrid: Boolean = true
    var showHelpers: Boolean = true
    var quality: Quality = Quality.MEDIUM
    var shadows: Boolean = true
    var viewportW: Int = 1
    var viewportH: Int = 1
    val textures = TextureManager()
}

data class RenderStats(var fps: Float = 0f, var tris: Int = 0, var calls: Int = 0)

/** Gerencia bitmaps (UI thread) e upload para GL (GL thread). */
class TextureManager {
    private val bitmaps = ConcurrentHashMap<String, Bitmap>()
    private val handles = HashMap<String, Int>()
    private val pendingDelete = mutableListOf<Int>()

    fun setBitmap(key: String, bmp: Bitmap) { bitmaps[key] = bmp }

    fun hasBitmap(key: String): Boolean = bitmaps.containsKey(key)

    fun remove(key: String) {
        bitmaps.remove(key)
        synchronized(this) {
            handles.remove(key)?.let { pendingDelete.add(it) }
        }
    }

    fun clearAll() {
        bitmaps.clear()
        synchronized(this) {
            pendingDelete.addAll(handles.values)
            handles.clear()
        }
    }

    /** Chamado apenas na GL thread. Retorna handle ou 0. */
    fun textureFor(key: String, provider: () -> Bitmap?): Int {
        synchronized(this) {
            for (h in pendingDelete) {
                try { GLES30.glDeleteTextures(1, intArrayOf(h), 0) } catch (_: Exception) {}
            }
            pendingDelete.clear()
            handles[key]?.let { return it }
        }
        val bmp = bitmaps[key] ?: provider()?.also { bitmaps[key] = it } ?: return 0
        if (bmp.isRecycled || bmp.width < 1 || bmp.height < 1) return 0
        val tex = IntArray(1)
        GLES30.glGenTextures(1, tex, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        synchronized(this) { handles[key] = tex[0] }
        return tex[0]
    }

    fun onContextLost() {
        synchronized(this) { handles.clear(); pendingDelete.clear() }
    }

    companion object {
        fun renderTextBitmap(text: String): Bitmap {
            val w = 1024; val h = 256
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = 0xFFFFFFFF.toInt()
            paint.textAlign = Paint.Align.CENTER
            var size = 150f
            paint.textSize = size
            val label = text.ifEmpty { "Text" }
            while (paint.measureText(label) > w - 60 && size > 12f) {
                size -= 8f
                paint.textSize = size
            }
            paint.setShadowLayer(8f, 0f, 4f, 0xAA000000.toInt())
            canvas.drawText(label, w / 2f, h / 2f - (paint.ascent() + paint.descent()) / 2f, paint)
            return bmp
        }

        fun blobShadowBitmap(): Bitmap {
            val s = 128
            val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = 0x99000000.toInt()
            for (i in 10 downTo 1) {
                paint.alpha = 14
                canvas.drawOval(
                    (s / 2f - s * i / 22f), (s / 2f - s * i / 22f),
                    (s / 2f + s * i / 22f), (s / 2f + s * i / 22f), paint
                )
            }
            return bmp
        }
    }
}

private fun floatBufferOf(data: FloatArray): FloatBuffer =
    ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(data); position(0)
    }

private fun intBufferOf(data: IntArray): IntBuffer =
    ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().apply {
        put(data); position(0)
    }

private class MeshGL {
    var vao = 0; var vbo = 0; var ibo = 0
    var indexCount = 0
    var version = -1L
    var vertCount = 0
}

private class SimpleGL {
    var vao = 0; var vbo = 0
    var count = 0
    var mode = GLES30.GL_LINES
}

/** Renderizador OpenGL ES 3.0 do viewport Zentra. */
class ZentraRenderer(val bridge: RenderBridge) : GLSurfaceView.Renderer {

    var thumbnailRequest: ((Bitmap) -> Unit)? = null
    var stats = RenderStats()
    var statsListener: ((RenderStats) -> Unit)? = null
    var autoQualityEnabled: Boolean = true
    var onSuggestLowerQuality: (() -> Unit)? = null

    private var meshProg = 0
    private var flatProg = 0
    private var pointProg = 0

    private val meshCache = HashMap<String, MeshGL>()
    private var gridFine: SimpleGL? = null
    private var gridCoarse: SimpleGL? = null
    private var axesHelper: SimpleGL? = null
    private var cameraHelper: SimpleGL? = null
    private var lightHelper: SimpleGL? = null
    private var dynLines: SimpleGL? = null
    private var dynTris: SimpleGL? = null
    private var dynPoints: SimpleGL? = null
    private var editPoints: SimpleGL? = null
    private var editPointsSel: SimpleGL? = null
    private var quadGL: SimpleGL? = null

    private var editCacheKey = ""
    private var lastSuggestMs = 0L
    private var frames = 0
    private var statTimeMs = 0L
    private var frameTris = 0
    private var frameCalls = 0

    // ---------- shaders ----------
    private val meshVert = """
        #version 300 es
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in vec3 aNormal;
        layout(location = 2) in vec2 aUV;
        uniform mat4 uMVP;
        uniform mat4 uModel;
        uniform mat3 uNormalMat;
        uniform vec2 uUvScale;
        uniform vec2 uUvOffset;
        out vec3 vWorldPos;
        out vec3 vNormal;
        out vec2 vUV;
        void main() {
            vec4 wp = uModel * vec4(aPos, 1.0);
            vWorldPos = wp.xyz;
            vNormal = normalize(uNormalMat * aNormal);
            vUV = aUV * uUvScale + uUvOffset;
            gl_Position = uMVP * vec4(aPos, 1.0);
        }
    """.trimIndent()

    private val meshFrag = """
        #version 300 es
        precision mediump float;
        in vec3 vWorldPos;
        in vec3 vNormal;
        in vec2 vUV;
        uniform vec4 uBaseColor;
        uniform float uRoughness;
        uniform float uMetallic;
        uniform vec3 uEmission;
        uniform float uOpacity;
        uniform int uHasTexture;
        uniform sampler2D uTexture;
        uniform int uSelected;
        uniform vec3 uCamPos;
        uniform int uNumLights;
        uniform int uSpecular;
        uniform vec4 uLightPos[8];
        uniform vec4 uLightColor[8];
        uniform vec4 uLightDir[8];
        uniform vec4 uLightParams[8];
        out vec4 fragColor;
        void main() {
            vec3 N = normalize(vNormal);
            if (!gl_FrontFacing) N = -N;
            vec3 V = normalize(uCamPos - vWorldPos);
            vec4 albedo = uBaseColor;
            if (uHasTexture == 1) {
                albedo *= texture(uTexture, vUV);
            }
            vec3 color = albedo.rgb * 0.22;
            float shininess = mix(90.0, 8.0, clamp(uRoughness, 0.0, 1.0));
            for (int i = 0; i < 8; i++) {
                if (i >= uNumLights) break;
                float ltype = uLightParams[i].x;
                float intensity = uLightParams[i].y;
                float range = max(uLightParams[i].z, 0.001);
                float spotCos = uLightParams[i].w;
                vec3 L; float atten = 1.0; float spot = 1.0;
                if (ltype < 1.5) {
                    vec3 toL = uLightPos[i].xyz - vWorldPos;
                    float dist = length(toL);
                    L = toL / max(dist, 0.0001);
                    float q = dist / range;
                    atten = intensity / (1.0 + 9.0 * q * q);
                    if (ltype > 0.5) {
                        vec3 sdir = normalize(uLightDir[i].xyz);
                        float c = dot(-L, sdir);
                        spot = smoothstep(spotCos, mix(1.0, spotCos, 0.55), c);
                    }
                } else if (ltype < 2.5) {
                    L = normalize(-uLightDir[i].xyz);
                    atten = intensity;
                } else {
                    vec3 toL = uLightPos[i].xyz - vWorldPos;
                    float dist = length(toL);
                    L = toL / max(dist, 0.0001);
                    float q = dist / range;
                    atten = intensity / (1.0 + 4.0 * q * q);
                }
                float diff = max(dot(N, L), 0.0);
                vec3 contrib = albedo.rgb * diff * uLightColor[i].rgb * atten * spot;
                if (uSpecular == 1 && diff > 0.0) {
                    vec3 H = normalize(L + V);
                    float spec = pow(max(dot(N, H), 0.0), shininess);
                    spec *= mix(0.25, 1.0, uMetallic) * (1.0 - uRoughness * 0.7);
                    contrib += uLightColor[i].rgb * spec * atten * spot;
                }
                color += contrib;
            }
            color += uEmission * albedo.a;
            if (uSelected == 1) {
                color = mix(color, vec3(0.55, 0.38, 1.0), 0.28) + vec3(0.10, 0.06, 0.20);
            }
            fragColor = vec4(color, albedo.a * uOpacity);
        }
    """.trimIndent()

    private val flatVert = """
        #version 300 es
        layout(location = 0) in vec3 aPos;
        uniform mat4 uMVP;
        void main() { gl_Position = uMVP * vec4(aPos, 1.0); }
    """.trimIndent()

    private val flatFrag = """
        #version 300 es
        precision mediump float;
        uniform vec4 uColor;
        out vec4 fragColor;
        void main() { fragColor = uColor; }
    """.trimIndent()

    private val pointVert = """
        #version 300 es
        layout(location = 0) in vec3 aPos;
        uniform mat4 uMVP;
        uniform float uPointSize;
        void main() {
            gl_Position = uMVP * vec4(aPos, 1.0);
            gl_PointSize = uPointSize;
        }
    """.trimIndent()

    private val pointFrag = """
        #version 300 es
        precision mediump float;
        uniform vec4 uColor;
        out vec4 fragColor;
        void main() {
            vec2 c = gl_PointCoord - vec2(0.5);
            if (dot(c, c) > 0.25) discard;
            fragColor = uColor;
        }
    """.trimIndent()

    private fun compileShader(type: Int, src: String): Int {
        val id = GLES30.glCreateShader(type)
        GLES30.glShaderSource(id, src)
        GLES30.glCompileShader(id)
        val status = IntArray(1)
        GLES30.glGetShaderiv(id, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e("ZentraGL", "Shader error: " + GLES30.glGetShaderInfoLog(id))
            GLES30.glDeleteShader(id)
            return 0
        }
        return id
    }

    private fun linkProgram(vs: Int, fs: Int): Int {
        val id = GLES30.glCreateProgram()
        GLES30.glAttachShader(id, vs)
        GLES30.glAttachShader(id, fs)
        GLES30.glLinkProgram(id)
        val status = IntArray(1)
        GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e("ZentraGL", "Link error: " + GLES30.glGetProgramInfoLog(id))
            GLES30.glDeleteProgram(id)
            return 0
        }
        return id
    }

    private fun buildProgram(vs: String, fs: String): Int {
        val v = compileShader(GLES30.GL_VERTEX_SHADER, vs)
        val f = compileShader(GLES30.GL_FRAGMENT_SHADER, fs)
        if (v == 0 || f == 0) return 0
        val p = linkProgram(v, f)
        GLES30.glDeleteShader(v)
        GLES30.glDeleteShader(f)
        return p
    }

    // ---------- buffers ----------
    private fun createSimple(data: FloatArray, mode: Int): SimpleGL {
        val g = SimpleGL()
        g.mode = mode
        g.count = data.size / 3
        val vao = IntArray(1); val vbo = IntArray(1)
        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glGenBuffers(1, vbo, 0)
        g.vao = vao[0]; g.vbo = vbo[0]
        GLES30.glBindVertexArray(g.vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, g.vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, floatBufferOf(data), GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        return g
    }

    private fun updateSimple(g: SimpleGL, data: FloatArray, mode: Int) {
        g.mode = mode
        g.count = data.size / 3
        if (g.vao == 0) {
            val vao = IntArray(1); val vbo = IntArray(1)
            GLES30.glGenVertexArrays(1, vao, 0)
            GLES30.glGenBuffers(1, vbo, 0)
            g.vao = vao[0]; g.vbo = vbo[0]
        }
        GLES30.glBindVertexArray(g.vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, g.vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, floatBufferOf(data), GLES30.GL_DYNAMIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun drawSimple(g: SimpleGL?, mvp: FloatArray, r: Float, gr: Float, b: Float, a: Float) {
        if (g == null || g.count <= 0) return
        GLES30.glUseProgram(flatProg)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(flatProg, "uMVP"), 1, false, mvp, 0)
        GLES30.glUniform4f(GLES30.glGetUniformLocation(flatProg, "uColor"), r, gr, b, a)
        GLES30.glBindVertexArray(g.vao)
        GLES30.glDrawArrays(g.mode, 0, g.count)
        GLES30.glBindVertexArray(0)
        frameCalls++
    }

    private fun drawPoints(g: SimpleGL?, mvp: FloatArray, size: Float, r: Float, gr: Float, b: Float, a: Float) {
        if (g == null || g.count <= 0) return
        GLES30.glUseProgram(pointProg)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(pointProg, "uMVP"), 1, false, mvp, 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(pointProg, "uPointSize"), size)
        GLES30.glUniform4f(GLES30.glGetUniformLocation(pointProg, "uColor"), r, gr, b, a)
        GLES30.glBindVertexArray(g.vao)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, g.count)
        GLES30.glBindVertexArray(0)
        frameCalls++
    }

    private fun buildGridLines(size: Float, step: Float): FloatArray {
        val list = ArrayList<Float>()
        var c = -size
        while (c <= size + 0.001f) {
            list.addAll(listOf(c, 0f, -size, c, 0f, size))
            list.addAll(listOf(-size, 0f, c, size, 0f, c))
            c += step
        }
        return list.toFloatArray()
    }

    private fun deleteMeshGL(g: MeshGL) {
        try {
            if (g.vao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(g.vao), 0)
            if (g.vbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(g.vbo), 0)
            if (g.ibo != 0) GLES30.glDeleteBuffers(1, intArrayOf(g.ibo), 0)
        } catch (_: Exception) {}
    }

    private fun meshGLFor(obj: SceneObject, mesh: MeshData): MeshGL? {
        var g = meshCache[obj.id]
        if (g != null && g.version == obj.meshVersion) return g
        if (mesh.vertices.isEmpty()) return null
        mesh.ensureUvs()
        val tris = mesh.triangleIndices()
        if (tris.isEmpty()) return null
        if (g == null) { g = MeshGL(); meshCache[obj.id] = g } else {
            // Rebuild: deleta buffers antigos
            try {
                if (g.vbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(g.vbo), 0)
                if (g.ibo != 0) GLES30.glDeleteBuffers(1, intArrayOf(g.ibo), 0)
            } catch (_: Exception) {}
        }
        val n = mesh.vertices.size
        val data = FloatArray(n * 8)
        for (i in 0 until n) {
            val v = mesh.vertices[i]
            val nr = if (i < mesh.normals.size) mesh.normals[i] else Vec3(0f, 1f, 0f)
            val uv = if (i < mesh.uvs.size) mesh.uvs[i] else Vec2(0f, 0f)
            data[i * 8] = v.x; data[i * 8 + 1] = v.y; data[i * 8 + 2] = v.z
            data[i * 8 + 3] = nr.x; data[i * 8 + 4] = nr.y; data[i * 8 + 5] = nr.z
            data[i * 8 + 6] = uv.x; data[i * 8 + 7] = uv.y
        }
        if (g.vao == 0) {
            val vao = IntArray(1)
            GLES30.glGenVertexArrays(1, vao, 0)
            g.vao = vao[0]
        }
        val vbo = IntArray(1); val ibo = IntArray(1)
        GLES30.glGenBuffers(1, vbo, 0)
        GLES30.glGenBuffers(1, ibo, 0)
        g.vbo = vbo[0]; g.ibo = ibo[0]
        GLES30.glBindVertexArray(g.vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, g.vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, floatBufferOf(data), GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, g.ibo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, tris.size * 4, intBufferOf(tris), GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 32, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 32, 12)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, 32, 24)
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
        g.indexCount = tris.size
        g.version = obj.meshVersion
        g.vertCount = n
        return g
    }

    // ---------- lifecycle ----------
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.043f, 0.055f, 0.09f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        meshProg = buildProgram(meshVert, meshFrag)
        flatProg = buildProgram(flatVert, flatFrag)
        pointProg = buildProgram(pointVert, pointFrag)
        meshCache.clear()
        bridge.textures.onContextLost()
        editCacheKey = ""
        gridFine = createSimple(buildGridLines(10f, 1f), GLES30.GL_LINES)
        gridCoarse = createSimple(buildGridLines(50f, 5f), GLES30.GL_LINES)
        axesHelper = createSimple(
            floatArrayOf(
                -0.6f, 0f, 0f, 0.6f, 0f, 0f,
                0f, -0.6f, 0f, 0f, 0.6f, 0f,
                0f, 0f, -0.6f, 0f, 0f, 0.6f
            ), GLES30.GL_LINES
        )
        cameraHelper = createSimple(
            floatArrayOf(
                -0.45f, -0.3f, 0f, 0.45f, -0.3f, 0f,
                0.45f, -0.3f, 0f, 0.45f, 0.3f, 0f,
                0.45f, 0.3f, 0f, -0.45f, 0.3f, 0f,
                -0.45f, 0.3f, 0f, -0.45f, -0.3f, 0f,
                -0.45f, -0.3f, 0f, 0f, 0f, -1.1f,
                0.45f, -0.3f, 0f, 0f, 0f, -1.1f,
                0.45f, 0.3f, 0f, 0f, 0f, -1.1f,
                -0.45f, 0.3f, 0f, 0f, 0f, -1.1f
            ), GLES30.GL_LINES
        )
        lightHelper = createSimple(
            floatArrayOf(
                0f, 0.5f, 0f, 0.5f, 0f, 0f,
                0.5f, 0f, 0f, 0f, -0.5f, 0f,
                0f, -0.5f, 0f, -0.5f, 0f, 0f,
                -0.5f, 0f, 0f, 0f, 0.5f, 0f,
                0f, 0.5f, 0f, 0f, 0f, 0.5f,
                0f, 0f, 0.5f, 0f, -0.5f, 0f,
                0f, -0.5f, 0f, 0f, 0f, -0.5f,
                0f, 0f, -0.5f, 0f, 0.5f, 0f,
                0.5f, 0f, 0f, 0f, 0f, 0.5f,
                0f, 0f, 0.5f, -0.5f, 0f, 0f,
                -0.5f, 0f, 0f, 0f, 0f, -0.5f,
                0f, 0f, -0.5f, 0.5f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f, -2.2f
            ), GLES30.GL_LINES
        )
        dynLines = SimpleGL(); dynTris = SimpleGL()
        dynPoints = SimpleGL(); editPoints = SimpleGL(); editPointsSel = SimpleGL()
        quadGL = createSimple(
            floatArrayOf(-0.5f, 0f, -0.5f, 0.5f, 0f, -0.5f, 0.5f, 0f, 0.5f, -0.5f, 0f, -0.5f, 0.5f, 0f, 0.5f, -0.5f, 0f, 0.5f),
            GLES30.GL_TRIANGLES
        )
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        val w = maxOf(1, width); val h = maxOf(1, height)
        GLES30.glViewport(0, 0, w, h)
        synchronized(bridge.lock) {
            bridge.viewportW = w
            bridge.viewportH = h
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        val t0 = System.nanoTime()
        try {
            drawFrame()
        } catch (e: Exception) {
            Log.e("ZentraGL", "Frame error: ${e.message}")
            GLES30.glClearColor(0.043f, 0.055f, 0.09f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        }
        // Estatisticas + auto quality
        frames++
        val nowMs = System.currentTimeMillis()
        if (statTimeMs == 0L) statTimeMs = nowMs
        val elapsed = nowMs - statTimeMs
        if (elapsed >= 1000L) {
            val fps = frames * 1000f / elapsed
            stats = RenderStats(fps, frameTris / maxOf(1, frames), frameCalls / maxOf(1, frames))
            statsListener?.invoke(stats)
            frames = 0; frameTris = 0; frameCalls = 0
            statTimeMs = nowMs
            if (autoQualityEnabled && fps < 22f && nowMs - lastSuggestMs > 8000L) {
                lastSuggestMs = nowMs
                onSuggestLowerQuality?.invoke()
            }
        }
        @Suppress("UNUSED_VARIABLE")
        val dtMs = (System.nanoTime() - t0) / 1_000_000L
    }

    private fun drawFrame() {
        synchronized(bridge.lock) {
            val scene = bridge.scene
            val cam = bridge.camera
            GLES30.glClearColor(0.043f, 0.055f, 0.09f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
            if (scene == null || cam == null) {
                finishFrame()
                return
            }
            val aspect = bridge.viewportW.toFloat() / maxOf(1, bridge.viewportH).toFloat()
            val view = cam.viewMatrix()
            val proj = cam.projectionMatrix(aspect)
            val vp = proj.multiplied(view)
            val camPos = cam.position()

            if (bridge.showGrid) drawGrid(vp)

            // Luzes
            val maxL = PerformanceManager.maxLights(if (bridge.quality == Quality.AUTO) Quality.MEDIUM else bridge.quality)
            val lights = scene.lights().take(maxL)
            val useDefaultLight = lights.isEmpty()

            // Objetos: opacos primeiro, transparentes depois
            val transparent = ArrayList<SceneObject>()
            for (obj in scene.objects) {
                if (!obj.visible) continue
                val mesh = obj.mesh
                if ((obj.type == ObjectType.MESH || obj.type == ObjectType.TEXT) && mesh != null) {
                    val mat = scene.materialOf(obj)
                    val isTransparent = obj.type == ObjectType.TEXT || (mat?.opacity ?: 1f) < 0.99f
                    if (isTransparent) transparent.add(obj)
                    else drawMesh(scene, obj, mesh, mat, vp, view, camPos, lights, useDefaultLight)
                }
            }
            if (transparent.isNotEmpty()) {
                GLES30.glEnable(GLES30.GL_BLEND)
                GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
                GLES30.glDepthMask(false)
                for (obj in transparent) {
                    val mesh = obj.mesh ?: continue
                    drawMesh(scene, obj, mesh, scene.materialOf(obj), vp, view, camPos, lights, useDefaultLight)
                }
                GLES30.glDepthMask(true)
                GLES30.glDisable(GLES30.GL_BLEND)
            }

            // Sombras suaves (blob) sob os objetos
            if (bridge.shadows && bridge.quality != Quality.LOW) {
                drawBlobShadows(scene, vp)
            }

            // Helpers (empty / light / camera)
            if (bridge.showHelpers) {
                for (obj in scene.objects) {
                    if (!obj.visible) continue
                    when (obj.type) {
                        ObjectType.EMPTY, ObjectType.LIGHT, ObjectType.CAMERA ->
                            drawHelper(scene, obj, vp)
                        else -> {}
                    }
                }
            }

            // Selecao: caixas + gizmo
            drawSelection(scene, vp)
            // Edit mode overlay
            if (bridge.editMode) drawEditOverlay(scene, vp)

            // Limpa meshes de objetos removidos
            val alive = scene.objects.map { it.id }.toSet()
            val dead = meshCache.keys.filter { it !in alive }
            for (id in dead) {
                meshCache.remove(id)?.let { deleteMeshGL(it) }
            }
            finishFrame()
        }
    }

    private fun drawGrid(vp: Mat4) {
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        drawSimple(gridCoarse, vp.m, 0.23f, 0.27f, 0.38f, 0.55f)
        drawSimple(gridFine, vp.m, 0.16f, 0.19f, 0.27f, 0.6f)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun drawMesh(
        scene: Scene, obj: SceneObject, mesh: MeshData, mat: MaterialData?,
        vp: Mat4, view: Mat4, camPos: Vec3,
        lights: List<SceneObject>, useDefaultLight: Boolean
    ) {
        val g = meshGLFor(obj, mesh) ?: return
        val model = scene.worldMatrix(obj)
        val mvp = vp.multiplied(model)
        // Normal matrix: inversa-transposta do model (3x3)
        val normalMat = FloatArray(9)
        val invT = model.inverted()?.transposed()
        if (invT != null) {
            normalMat[0] = invT.m[0]; normalMat[1] = invT.m[1]; normalMat[2] = invT.m[2]
            normalMat[3] = invT.m[4]; normalMat[4] = invT.m[5]; normalMat[5] = invT.m[6]
            normalMat[6] = invT.m[8]; normalMat[7] = invT.m[9]; normalMat[8] = invT.m[10]
        } else {
            normalMat[0] = 1f; normalMat[4] = 1f; normalMat[8] = 1f
        }
        GLES30.glUseProgram(meshProg)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(meshProg, "uMVP"), 1, false, mvp.m, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(meshProg, "uModel"), 1, false, model.m, 0)
        GLES30.glUniformMatrix3fv(GLES30.glGetUniformLocation(meshProg, "uNormalMat"), 1, false, normalMat, 0)
        val base = mat?.baseColor ?: Rgba(0.75f, 0.78f, 0.85f, 1f)
        GLES30.glUniform4f(GLES30.glGetUniformLocation(meshProg, "uBaseColor"), base.r, base.g, base.b, base.a)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(meshProg, "uRoughness"), mat?.roughness ?: 0.55f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(meshProg, "uMetallic"), mat?.metallic ?: 0f)
        val em = mat?.emission ?: Rgba(0f, 0f, 0f, 1f)
        val es = mat?.emissionStrength ?: 0f
        GLES30.glUniform3f(GLES30.glGetUniformLocation(meshProg, "uEmission"), em.r * es, em.g * es, em.b * es)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(meshProg, "uOpacity"), mat?.opacity ?: 1f)
        GLES30.glUniform2f(
            GLES30.glGetUniformLocation(meshProg, "uUvScale"),
            mat?.uvScale?.x ?: 1f, mat?.uvScale?.y ?: 1f
        )
        GLES30.glUniform2f(
            GLES30.glGetUniformLocation(meshProg, "uUvOffset"),
            mat?.uvOffset?.x ?: 0f, mat?.uvOffset?.y ?: 0f
        )
        GLES30.glUniform1i(GLES30.glGetUniformLocation(meshProg, "uSelected"), if (obj.id in bridge.selectedIds) 1 else 0)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(meshProg, "uCamPos"), camPos.x, camPos.y, camPos.z)
        GLES30.glUniform1i(
            GLES30.glGetUniformLocation(meshProg, "uSpecular"),
            if (bridge.quality == Quality.LOW) 0 else 1
        )
        // Textura
        var texHandle = 0
        if (obj.type == ObjectType.TEXT) {
            val key = "text:" + obj.id
            texHandle = bridge.textures.textureFor(key) {
                TextureManager.renderTextBitmap(obj.text ?: "Text")
            }
        } else if (mat?.textureFile != null) {
            val key = "file:" + mat.textureFile
            texHandle = bridge.textures.textureFor(key) { null }
        }
        if (texHandle != 0) {
            GLES30.glUniform1i(GLES30.glGetUniformLocation(meshProg, "uHasTexture"), 1)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texHandle)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(meshProg, "uTexture"), 0)
        } else {
            GLES30.glUniform1i(GLES30.glGetUniformLocation(meshProg, "uHasTexture"), 0)
        }
        // Luzes
        val lp = FloatArray(8 * 4); val lc = FloatArray(8 * 4)
        val ld = FloatArray(8 * 4); val pr = FloatArray(8 * 4)
        var count = 0
        if (useDefaultLight) {
            ld[0] = 0.4f; ld[1] = -1f; ld[2] = 0.35f; ld[3] = 0f
            lc[0] = 1f; lc[1] = 1f; lc[2] = 1f; lc[3] = 1f
            pr[0] = 2f; pr[1] = 1.6f; pr[2] = 1f; pr[3] = 0f
            count = 1
        } else {
            for ((i, lo) in lights.withIndex()) {
                val l = lo.light ?: continue
                val wm = scene.worldMatrix(lo)
                val p = wm.translationPart()
                val d = wm.transformDir(Vec3(0f, 0f, -1f)).normalized()
                val type = when (l.kind) {
                    LightKind.POINT -> 0f; LightKind.SPOT -> 1f
                    LightKind.DIRECTIONAL -> 2f; LightKind.AREA -> 3f
                }
                lp[i * 4] = p.x; lp[i * 4 + 1] = p.y; lp[i * 4 + 2] = p.z
                lc[i * 4] = l.color.r; lc[i * 4 + 1] = l.color.g; lc[i * 4 + 2] = l.color.b
                ld[i * 4] = d.x; ld[i * 4 + 1] = d.y; ld[i * 4 + 2] = d.z
                pr[i * 4] = type; pr[i * 4 + 1] = l.intensity / 10f
                pr[i * 4 + 2] = l.range
                pr[i * 4 + 3] = kotlin.math.cos(l.spotAngleDeg * DEG2RAD / 2f)
                count++
            }
        }
        GLES30.glUniform1i(GLES30.glGetUniformLocation(meshProg, "uNumLights"), count)
        GLES30.glUniform4fv(GLES30.glGetUniformLocation(meshProg, "uLightPos[0]"), 8, lp, 0)
        GLES30.glUniform4fv(GLES30.glGetUniformLocation(meshProg, "uLightColor[0]"), 8, lc, 0)
        GLES30.glUniform4fv(GLES30.glGetUniformLocation(meshProg, "uLightDir[0]"), 8, ld, 0)
        GLES30.glUniform4fv(GLES30.glGetUniformLocation(meshProg, "uLightParams[0]"), 8, pr, 0)

        GLES30.glBindVertexArray(g.vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, g.indexCount, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
        if (texHandle != 0) GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        frameCalls++
        frameTris += g.indexCount / 3
    }

    private fun drawBlobShadows(scene: Scene, vp: Mat4) {
        val tex = bridge.textures.textureFor("zz_blob") { TextureManager.blobShadowBitmap() }
        if (tex == 0) return
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        GLES30.glUseProgram(meshProg)
        // Usa o shader de mesh como quad texturizado simples
        for (obj in scene.objects) {
            if (!obj.visible || obj.type != ObjectType.MESH) continue
            val mesh = obj.mesh ?: continue
            val (c, r) = mesh.boundingSphere()
            val wm = scene.worldMatrix(obj)
            val wc = wm.transformPoint(c)
            val scale = maxOf(
                Vec3(wm.m[0], wm.m[1], wm.m[2]).length(),
                Vec3(wm.m[8], wm.m[9], wm.m[10]).length()
            )
            val rad = r * scale
            if (rad < 0.01f) continue
            val model = Mat4.translation(Vec3(wc.x, 0.02f, wc.z))
                .multiplyInPlace(Mat4.scaleMat(Vec3(rad * 2.4f, 1f, rad * 2.4f)))
            val mvp = vp.multiplied(model)
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(meshProg, "uMVP"), 1, false, mvp.m, 0)
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(meshProg, "uModel"), 1, false, model.m, 0)
            GLES30.glUniformMatrix3fv(
                GLES30.glGetUniformLocation(meshProg, "uNormalMat"), 1, false,
                floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f), 0
            )
            GLES30.glUniform4f(GLES30.glGetUniformLocation(meshProg, "uBaseColor"), 0f, 0f, 0f, 1f)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(meshProg, "uRoughness"), 1f)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(meshProg, "uMetallic"), 0f)
            GLES30.glUniform3f(GLES30.glGetUniformLocation(meshProg, "uEmission"), 0f, 0f, 0f)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(meshProg, "uOpacity"), 0.55f)
            GLES30.glUniform2f(GLES30.glGetUniformLocation(meshProg, "uUvScale"), 1f, 1f)
            GLES30.glUniform2f(GLES30.glGetUniformLocation(meshProg, "uUvOffset"), 0f, 0f)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(meshProg, "uSelected"), 0)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(meshProg, "uSpecular"), 0)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(meshProg, "uNumLights"), 0)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(meshProg, "uHasTexture"), 1)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(meshProg, "uTexture"), 0)
            // O quad não tem normal/uv no formato interleaved: desenha via flat com cor escura
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            drawSimple(quadGL, mvp.m, 0f, 0f, 0f, 0.35f)
        }
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun drawHelper(scene: Scene, obj: SceneObject, vp: Mat4) {
        val model = scene.worldMatrix(obj)
        val mvp = vp.multiplied(model)
        val sel = obj.id in bridge.selectedIds
        when (obj.type) {
            ObjectType.EMPTY -> {
                drawSimple(axesHelper, mvp.m, 0.6f, 0.65f, 0.8f, 1f)
                if (sel) drawPoints(dynPoints?.also { updateSimple(it, floatArrayOf(0f, 0f, 0f), GLES30.GL_POINTS) }, mvp.m, 14f, 0.55f, 0.38f, 1f, 1f)
            }
            ObjectType.LIGHT -> {
                val l = obj.light
                val cr = l?.color?.r ?: 1f; val cg = l?.color?.g ?: 0.9f; val cb = l?.color?.b ?: 0.6f
                drawSimple(lightHelper, mvp.m, cr, cg, cb, 1f)
                if (sel) drawSimple(axesHelper, mvp.m, 0.55f, 0.38f, 1f, 1f)
            }
            ObjectType.CAMERA -> {
                val isMain = obj.camera?.isMain == true
                if (isMain) drawSimple(cameraHelper, mvp.m, 0.22f, 0.88f, 1f, 1f)
                else drawSimple(cameraHelper, mvp.m, 0.55f, 0.6f, 0.7f, 1f)
                if (sel) drawSimple(axesHelper, mvp.m, 0.55f, 0.38f, 1f, 1f)
            }
            else -> {}
        }
        frameCalls++
    }

    private fun drawSelection(scene: Scene, vp: Mat4) {
        if (bridge.selectedIds.isEmpty()) return
        // Caixas delimitadoras
        val lineData = ArrayList<Float>()
        var gizmoCenter: Vec3? = null
        var gizmoCount = 0
        for (id in bridge.selectedIds) {
            val obj = scene.find(id) ?: continue
            val wm = scene.worldMatrix(obj)
            val mesh = obj.mesh
            var min = Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
            var max = Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            if (mesh != null && mesh.vertices.isNotEmpty()) {
                for (v in mesh.vertices) {
                    val w = wm.transformPoint(v)
                    min.x = minOf(min.x, w.x); min.y = minOf(min.y, w.y); min.z = minOf(min.z, w.z)
                    max.x = maxOf(max.x, w.x); max.y = maxOf(max.y, w.y); max.z = maxOf(max.z, w.z)
                }
            } else {
                val c = wm.translationPart()
                min = c + Vec3(-0.5f, -0.5f, -0.5f)
                max = c + Vec3(0.5f, 0.5f, 0.5f)
            }
            val c = (min + max) * 0.5f
            gizmoCenter = (gizmoCenter ?: Vec3()) + c
            gizmoCount++
            val x0 = min.x; val y0 = min.y; val z0 = min.z
            val x1 = max.x; val y1 = max.y; val z1 = max.z
            val corners = arrayOf(
                floatArrayOf(x0, y0, z0), floatArrayOf(x1, y0, z0),
                floatArrayOf(x1, y0, z0), floatArrayOf(x1, y0, z1),
                floatArrayOf(x1, y0, z1), floatArrayOf(x0, y0, z1),
                floatArrayOf(x0, y0, z1), floatArrayOf(x0, y0, z0),
                floatArrayOf(x0, y1, z0), floatArrayOf(x1, y1, z0),
                floatArrayOf(x1, y1, z0), floatArrayOf(x1, y1, z1),
                floatArrayOf(x1, y1, z1), floatArrayOf(x0, y1, z1),
                floatArrayOf(x0, y1, z1), floatArrayOf(x0, y1, z0),
                floatArrayOf(x0, y0, z0), floatArrayOf(x0, y1, z0),
                floatArrayOf(x1, y0, z0), floatArrayOf(x1, y1, z0),
                floatArrayOf(x1, y0, z1), floatArrayOf(x1, y1, z1),
                floatArrayOf(x0, y0, z1), floatArrayOf(x0, y1, z1)
            )
            for (p in corners) { lineData.add(p[0]); lineData.add(p[1]); lineData.add(p[2]) }
        }
        if (lineData.isNotEmpty()) {
            dynLines?.let { updateSimple(it, lineData.toFloatArray(), GLES30.GL_LINES) }
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            drawSimple(dynLines, vp.m, 0.55f, 0.38f, 1f, 1f)
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        }
        // Gizmo de eixos (mover/rotacionar/escalar)
        if (bridge.toolGizmo in 1..3 && gizmoCenter != null && gizmoCount > 0) {
            val center = gizmoCenter / gizmoCount.toFloat()
            val len = 1.1f
            val lock = bridge.axisLock
            fun axis(a: Vec3, r: Float, g: Float, b: Float, id: Int) {
                val e = center + a * len
                dynLines?.let {
                    updateSimple(it, floatArrayOf(center.x, center.y, center.z, e.x, e.y, e.z), GLES30.GL_LINES)
                }
                val dim = lock != 0 && lock != id
                GLES30.glDisable(GLES30.GL_DEPTH_TEST)
                drawSimple(dynLines, vp.m, r, g, b, if (dim) 0.25f else 1f)
                dynPoints?.let { updateSimple(it, floatArrayOf(e.x, e.y, e.z), GLES30.GL_POINTS) }
                drawPoints(dynPoints, vp.m, 12f, r, g, b, if (dim) 0.25f else 1f)
                GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            }
            axis(Vec3.RIGHT, 1f, 0.3f, 0.3f, 1)
            axis(Vec3.UP, 0.3f, 1f, 0.35f, 2)
            axis(Vec3(0f, 0f, 1f), 0.3f, 0.5f, 1f, 3)
        }
    }

    private fun drawEditOverlay(scene: Scene, vp: Mat4) {
        val id = bridge.activeId ?: return
        val obj = scene.find(id) ?: return
        val mesh = obj.mesh ?: return
        if (mesh.vertices.isEmpty()) return
        val model = scene.worldMatrix(obj)
        val mvp = vp.multiplied(model)
        val key = "$id:${obj.meshVersion}:${bridge.editComponent}:${bridge.editSelection.hashCode()}:${bridge.editEdges.hashCode()}"
        val comp = bridge.editComponent
        // Pontos de todos os vertices
        if (key != editCacheKey) {
            val all = FloatArray(mesh.vertices.size * 3)
            for (i in mesh.vertices.indices) {
                all[i * 3] = mesh.vertices[i].x
                all[i * 3 + 1] = mesh.vertices[i].y
                all[i * 3 + 2] = mesh.vertices[i].z
            }
            editPoints?.let { updateSimple(it, all, GLES30.GL_POINTS) }
            // Selecionados conforme componente
            val selPts = ArrayList<Float>()
            val selLines = ArrayList<Float>()
            val selTris = ArrayList<Float>()
            if (comp == 0) {
                for (i in bridge.editSelection) {
                    if (i !in mesh.vertices.indices) continue
                    val v = mesh.vertices[i]
                    selPts.addAll(listOf(v.x, v.y, v.z))
                }
            } else if (comp == 1) {
                for (e in bridge.editEdges) {
                    if (e.first !in mesh.vertices.indices || e.second !in mesh.vertices.indices) continue
                    val a = mesh.vertices[e.first]; val b = mesh.vertices[e.second]
                    selLines.addAll(listOf(a.x, a.y, a.z, b.x, b.y, b.z))
                    selPts.addAll(listOf(a.x, a.y, a.z, b.x, b.y, b.z))
                }
            } else {
                for (fi in bridge.editSelection) {
                    if (fi !in mesh.faces.indices) continue
                    val idx = mesh.faces[fi].indices
                    if (idx.any { it !in mesh.vertices.indices }) continue
                    for (i in idx.indices) {
                        val a = mesh.vertices[idx[i]]
                        val b = mesh.vertices[idx[(i + 1) % idx.size]]
                        selLines.addAll(listOf(a.x, a.y, a.z, b.x, b.y, b.z))
                    }
                    if (idx.size >= 3) {
                        for (i in 1 until idx.size - 1) {
                            for (k in listOf(idx[0], idx[i], idx[i + 1])) {
                                val v = mesh.vertices[k]
                                selTris.addAll(listOf(v.x, v.y, v.z))
                            }
                        }
                    }
                }
            }
            editPointsSel?.let { updateSimple(it, selPts.toFloatArray(), GLES30.GL_POINTS) }
            dynLines?.let { updateSimple(it, selLines.toFloatArray(), GLES30.GL_LINES) }
            dynTris?.let { updateSimple(it, selTris.toFloatArray(), GLES30.GL_TRIANGLES) }
            editCacheKey = key
        }
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        drawPoints(editPoints, mvp.m, 7f, 1f, 1f, 1f, 0.85f)
        drawPoints(editPointsSel, mvp.m, 11f, 0.55f, 0.38f, 1f, 1f)
        if (comp != 0) {
            drawSimple(dynLines, mvp.m, 1f, 0.75f, 0.25f, 1f)
        }
        if (comp == 2) {
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            drawSimple(dynTris, mvp.m, 1f, 0.6f, 0.15f, 0.35f)
            GLES30.glDisable(GLES30.GL_BLEND)
        }
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    private fun finishFrame() {
        val req = thumbnailRequest
        if (req != null) {
            thumbnailRequest = null
            try {
                val w = bridge.viewportW; val h = bridge.viewportH
                val pixels = IntArray(w * h)
                val byteBuf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
                GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, byteBuf)
                byteBuf.position(0)
                byteBuf.get(pixels)
                // Converte RGBA -> ARGB e inverte verticalmente
                val out = IntArray(w * h)
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val p = pixels[y * w + x]
                        val r = p and 0xFF
                        val g = (p shr 8) and 0xFF
                        val b = (p shr 16) and 0xFF
                        val a = (p ushr 24) and 0xFF
                        out[(h - 1 - y) * w + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.setPixels(out, 0, w, 0, 0, w, h)
                req(bmp)
            } catch (e: Exception) {
                Log.e("ZentraGL", "Thumbnail falhou: ${e.message}")
            }
        }
    }
}
