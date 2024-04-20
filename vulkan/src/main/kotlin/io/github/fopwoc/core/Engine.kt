package io.github.fopwoc.core

import io.github.fopwoc.graphics.Render
import io.github.fopwoc.scene.Scene
import java.util.concurrent.atomic.AtomicBoolean

private const val MS_IN_SECOND = 1000.0f
class Engine(
    windowTitle: String,
    val init: (window: Window, scene: Scene, render: Render) -> Unit,
    val input: (window: Window, scene: Scene, diffTimeMillis: Long) -> Unit,
    val update: (window: Window, scene: Scene, diffTimeMillis: Long) -> Unit,
    val cleanUp: () -> Unit
) {
    private var running = AtomicBoolean(false)

    private val window: Window
    private val scene: Scene
    private val render: Render

    init {
        window = Window(windowTitle)
        scene = Scene(window)
        render = Render(window, scene)
        init(window, scene, render)
    }

    private fun cleanup() {
        cleanUp()
        render.cleanup()
        window.cleanup()
    }

    fun run() {
        val engineProperties: EngineProperties = EngineProperties.getInstance()
        var initialTime = System.currentTimeMillis()
        val timeU: Float = MS_IN_SECOND / engineProperties.ups
        var deltaUpdate = 0.0
        var updateTime = initialTime

        while (running.get() && !window.shouldClose()) {
            scene.camera.setHasMoved(false);
            window.pollEvents()
            val now = System.currentTimeMillis()
            deltaUpdate += ((now - initialTime) / timeU).toDouble()
            input(window, scene, now - initialTime)
            if (deltaUpdate >= 1) {
                val diffTimeMillis = now - updateTime
                update(window, scene, diffTimeMillis)
                updateTime = now
                deltaUpdate--
            }
            render.render(window, scene)
            initialTime = now
        }
        cleanup()
    }

    fun start() {
        running.set(true)
        run()
    }

    fun stop() {
        running.set(false)
    }
}
