package com.zentra.z3d.ui.editor

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zentra.z3d.render.ZentraRenderer
import kotlin.math.abs

/** GLSurfaceView com gestos multitouch do Zentra (1 dedo manipula/orbita, 2 pinch+pan, 3 orbita). */
class ViewportView(context: Context, val vm: EditorViewModel) : GLSurfaceView(context) {

    val zRenderer: ZentraRenderer = ZentraRenderer(vm.bridge)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.coerceAtLeast(10)

    private var mode = Mode.NONE
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downTime = 0L
    private var pinchDist = 0f
    private var pinchMidX = 0f
    private var pinchMidY = 0f
    private var multiFingerSeen = false

    private enum class Mode { NONE, PENDING, ORBIT, MANIPULATE, PINCH, ORBIT3 }

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = true
        setRenderer(zRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                mode = Mode.PENDING
                multiFingerSeen = false
                downX = ev.x; downY = ev.y
                lastX = ev.x; lastY = ev.y
                downTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                when (ev.pointerCount) {
                    2 -> {
                        if (mode == Mode.MANIPULATE) {
                            vm.endStroke(vm.objMode.value == ObjMode.EDIT)
                        }
                        mode = Mode.PINCH
                        multiFingerSeen = true
                        pinchDist = fingerDistance(ev)
                        pinchMidX = (ev.getX(0) + ev.getX(1)) / 2f
                        pinchMidY = (ev.getY(0) + ev.getY(1)) / 2f
                    }
                    3 -> {
                        mode = Mode.ORBIT3
                        lastX = centroidX(ev, 3); lastY = centroidY(ev, 3)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when (mode) {
                    Mode.PENDING -> {
                        if (ev.pointerCount == 1) {
                            val dx = ev.x - downX
                            val dy = ev.y - downY
                            if (abs(dx) > slop || abs(dy) > slop) {
                                if (shouldManipulate()) {
                                    mode = Mode.MANIPULATE
                                    vm.beginStroke()
                                    lastX = ev.x; lastY = ev.y
                                } else {
                                    mode = Mode.ORBIT
                                    lastX = ev.x; lastY = ev.y
                                }
                            }
                        }
                    }
                    Mode.ORBIT -> {
                        if (ev.pointerCount == 1) {
                            val dx = ev.x - lastX
                            val dy = ev.y - lastY
                            lastX = ev.x; lastY = ev.y
                            val s = 0.35f * vm.settings.orbitSensitivity
                            synchronized(vm.bridge.lock) { vm.camera.orbit(dx * s, -dy * s) }
                        }
                    }
                    Mode.MANIPULATE -> {
                        if (ev.pointerCount == 1) {
                            val dx = ev.x - lastX
                            val dy = ev.y - lastY
                            lastX = ev.x; lastY = ev.y
                            vm.onDrag(dx, dy)
                        }
                    }
                    Mode.PINCH -> {
                        if (ev.pointerCount >= 2) {
                            val dist = fingerDistance(ev)
                            if (pinchDist > 1f && dist > 1f) {
                                synchronized(vm.bridge.lock) { vm.camera.zoom(pinchDist / dist) }
                            }
                            pinchDist = dist
                            val midX = (ev.getX(0) + ev.getX(1)) / 2f
                            val midY = (ev.getY(0) + ev.getY(1)) / 2f
                            synchronized(vm.bridge.lock) {
                                val wpp = vm.camera.worldPerPixel(vm.bridge.viewportH.toFloat())
                                vm.camera.pan((midX - pinchMidX) * wpp, (midY - pinchMidY) * wpp)
                            }
                            pinchMidX = midX; pinchMidY = midY
                        }
                    }
                    Mode.ORBIT3 -> {
                        if (ev.pointerCount >= 3) {
                            val cx = centroidX(ev, 3); val cy = centroidY(ev, 3)
                            val s = 0.35f * vm.settings.orbitSensitivity
                            synchronized(vm.bridge.lock) { vm.camera.orbit((cx - lastX) * s, -(cy - lastY) * s) }
                            lastX = cx; lastY = cy
                        }
                    }
                    Mode.NONE -> {}
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (mode == Mode.PINCH || mode == Mode.ORBIT3) {
                    // Volta para orbita com o dedo restante
                    mode = Mode.ORBIT
                    val remaining = ev.pointerCount - 1
                    if (remaining == 1) {
                        val idx = if (ev.actionIndex == 0) 1 else 0
                        lastX = ev.getX(idx); lastY = ev.getY(idx)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (action == MotionEvent.ACTION_UP && mode == Mode.PENDING && !multiFingerSeen) {
                    val dt = System.currentTimeMillis() - downTime
                    val moved = abs(ev.x - downX) + abs(ev.y - downY)
                    if (dt < 600 && moved < slop * 2) {
                        val w = width.coerceAtLeast(1).toFloat()
                        val h = height.coerceAtLeast(1).toFloat()
                        val ndcX = (ev.x / w) * 2f - 1f
                        val ndcY = 1f - (ev.y / h) * 2f
                        vm.tapSelect(ndcX, ndcY)
                    }
                }
                if (mode == Mode.MANIPULATE) {
                    vm.endStroke(vm.objMode.value == ObjMode.EDIT)
                }
                mode = Mode.NONE
                multiFingerSeen = false
            }
        }
        return true
    }

    private fun shouldManipulate(): Boolean {
        if (vm.selection.value.isEmpty()) return false
        return when (vm.tool.value) {
            Tool.MOVE, Tool.ROTATE, Tool.SCALE -> true
            else -> false
        }
    }

    private fun fingerDistance(ev: MotionEvent): Float {
        val dx = ev.getX(0) - ev.getX(1)
        val dy = ev.getY(0) - ev.getY(1)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun centroidX(ev: MotionEvent, n: Int): Float {
        var s = 0f
        for (i in 0 until minOf(n, ev.pointerCount)) s += ev.getX(i)
        return s / minOf(n, ev.pointerCount)
    }

    private fun centroidY(ev: MotionEvent, n: Int): Float {
        var s = 0f
        for (i in 0 until minOf(n, ev.pointerCount)) s += ev.getY(i)
        return s / minOf(n, ev.pointerCount)
    }
}

@Composable
fun ViewportPanel(vm: EditorViewModel, modifier: Modifier = Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = remember { arrayOfNulls<ViewportView>(1) }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> holder[0]?.onResume()
                Lifecycle.Event.ON_PAUSE -> holder[0]?.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ViewportView(ctx, vm).also { v ->
                holder[0] = v
                vm.rendererRef = v.zRenderer
                v.zRenderer.statsListener = { st -> vm.stats.value = st }
                v.zRenderer.onSuggestLowerQuality = { vm.autoDegrade() }
                v.zRenderer.autoQualityEnabled = true
            }
        },
        update = {},
        onRelease = { v ->
            try { v.onPause() } catch (_: Exception) {}
            if (vm.rendererRef === v.zRenderer) vm.rendererRef = null
        }
    )
}
