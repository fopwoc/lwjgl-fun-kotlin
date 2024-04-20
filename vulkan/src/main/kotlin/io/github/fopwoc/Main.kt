package io.github.fopwoc

import io.github.fopwoc.core.Engine
import io.github.fopwoc.core.MouseInput
import io.github.fopwoc.core.Window
import io.github.fopwoc.graphics.Render
import io.github.fopwoc.model.ModelData
import io.github.fopwoc.scene.Camera
import io.github.fopwoc.scene.Entity
import io.github.fopwoc.scene.InputConstants
import io.github.fopwoc.scene.Light
import io.github.fopwoc.scene.ModelLoader.loadModel
import io.github.fopwoc.scene.Scene
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.GLFW_CURSOR
import org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED
import org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT
import org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT
import org.lwjgl.glfw.GLFW.glfwSetInputMode
import org.lwjgl.system.Configuration
import org.pmw.tinylog.Logger
import kotlin.math.cos
import kotlin.math.sin

private val rotatingAngle = Vector3f(1f, 1f, 1f)
private var angle: Float = 0f
private var stopRotate: Boolean = false

private var angleInc: Float = 0.0f
private val directionalLight: Light = Light()
private var lightAngle: Float = 0.0f

fun main(args: Array<String>) {
    Logger.info("Starting application")
    // Configuration.DEBUG.set(true)
    // Configuration.DEBUG_LOADER.set(true)
    Configuration.LIBRARY_PATH.set("/usr/local/lib:${Configuration.LIBRARY_PATH.get()}")
    Configuration.VULKAN_LIBRARY_NAME.set("libvulkan.1.dylib")
    //Configuration.VULKAN_LIBRARY_NAME.set("MoltenVK")

    val engine = Engine(
        "Vulkan LWJGL Kotlin",
        init = { window: Window, scene: Scene, render: Render ->

            val modelDataList: MutableList<ModelData> = ArrayList()

            addToScene(
                id = "CubeEntity",
                pos = Vector3f(0f, 4f, 0f),
                modelId = "CubeModel",
                modelPath = "resources/models/cube/cube.obj",
                texturesDir = "resources/models/cube",
                modelDataList,
                scene
            )

            addToScene(
                id = "MaxwellEntity",
                pos = Vector3f(0f, 0.5f, 0f),
                modelId = "MaxwellModel",
                modelPath = "resources/models/maxwell/maxwell.obj",
                texturesDir = "resources/models/maxwell",
                modelDataList,
                scene
            )

            addToScene(
                id = "SponzaEntity",
                pos = Vector3f(0f, 0f, 0f),
                modelId = "sponza-model",
                modelPath = "resources/models/sponza/Sponza.gltf",
                texturesDir = "resources/models/sponza",
                modelDataList,
                scene
            )

            render.loadModels(modelDataList)

            val camera: Camera = scene.camera
            camera.setPosition(-5f, 2f, 0f)
            camera.setRotation(Math.toRadians(20.0).toFloat(), Math.toRadians(90.0).toFloat())

            scene.ambientLight.set(0.2f, 0.2f, 0.2f, 1.0f)
            val lights: MutableList<Light> = ArrayList()
            directionalLight.position.set(0.0f, 1.0f, 0.0f, 0.0f)
            directionalLight.color.set(1.0f, 1.0f, 1.0f, 1.0f)
            lights.add(directionalLight)
            updateDirectionalLight()

//            val light = Light()
//            light.position[0f, 1f, 0f] = 1.0f
//            light.color[0.0f, 1.0f, 0.0f] = 1.0f
//            lights.add(light)

            scene.setLights(lights.toTypedArray())
        },
        input = { window: Window, scene: Scene, diffTimeMillis: Long ->
            val move: Float = diffTimeMillis * InputConstants.MOVEMENT_SPEED
            val camera: Camera = scene.camera
            if (window.isKeyPressed(GLFW.GLFW_KEY_W)) {
                camera.moveForward(move)
            } else if (window.isKeyPressed(GLFW.GLFW_KEY_S)) {
                camera.moveBackwards(move)
            }
            if (window.isKeyPressed(GLFW.GLFW_KEY_A)) {
                camera.moveLeft(move)
            } else if (window.isKeyPressed(GLFW.GLFW_KEY_D)) {
                camera.moveRight(move)
            }
            if (window.isKeyPressed(GLFW.GLFW_KEY_SPACE)) {
                camera.moveUp(move)
            } else if (window.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT)) {
                camera.moveDown(move)
            }

            if (window.isKeyPressed(GLFW_KEY_LEFT)) {
                angleInc -= 0.05f
                scene.setLightChanged(true)
            } else if (window.isKeyPressed(GLFW_KEY_RIGHT)) {
                angleInc += 0.05f
                scene.setLightChanged(true)
            } else {
                angleInc = 0f
                scene.setLightChanged(false)
            }

            stopRotate = window.isKeyPressed(GLFW.GLFW_KEY_TAB)

            val mouseInput: MouseInput = window.mouseInput
            if (!window.isKeyPressed(GLFW.GLFW_KEY_LEFT_CONTROL)) {
                glfwSetInputMode(window.windowHandle, GLFW_CURSOR, GLFW_CURSOR_DISABLED)

                val displVec = mouseInput.displVec
                camera.addRotation(
                    Math.toRadians(displVec.x * InputConstants.MOUSE_SENSITIVITY).toFloat(),
                    Math.toRadians(displVec.y * InputConstants.MOUSE_SENSITIVITY).toFloat()
                )

                // glfwSetCursorPos(window.windowHandle,window.width.div(2.0), window.height.div(2.0))
            } else {
                glfwSetInputMode(window.windowHandle, GLFW_CURSOR, GLFW_CURSOR_NORMAL)
            }

            lightAngle += angleInc
            if (lightAngle < 0) {
                lightAngle = 0f
            } else if (lightAngle > 180) {
                lightAngle = 180f
            }
            updateDirectionalLight()
        },
        update = { window: Window, scene: Scene, diffTimeMillis: Long ->
            if (!stopRotate) {
                angle += 0.1f
                if (angle >= 360) {
                    angle -= 360
                }
            }

            val cat = scene.getEntitiesByModelId("MaxwellModel")
            cat?.forEach {
                if (!stopRotate) {
                    it.rotation.identity().rotateY(angle)
                }

                it.updateModelMatrix()
            }

            val cube = scene.getEntitiesByModelId("CubeModel")
            cube?.forEach {
                if (!stopRotate) {
                    it.rotation.identity().rotateAxis(Math.toRadians(angle * 10.toDouble()).toFloat(), rotatingAngle)
                }

                it.updateModelMatrix()
            }

            val light = scene.lights.first()

                //light.position.set(Vector4f(0.0f, 0.0f,0.0f,angle))
        },
        cleanUp = {
            // To be implemented
        },

    )
    engine.start()
}

fun addToScene(
    id: String,
    pos: Vector3f = Vector3f(0f, 0f, 0f),
    modelId: String,
    modelPath: String,
    texturesDir: String,
    modelDataList: MutableList<ModelData>,
    scene: Scene
) {
    val modelData = loadModel(
        modelId,
        modelPath = modelPath,
        texturesDir = texturesDir
    )
    modelDataList.add(modelData)

    scene.addEntity(
        Entity(
            id,
            modelId,
            pos
        )
    )
}

private fun updateDirectionalLight() {
    val zValue = cos(Math.toRadians(lightAngle.toDouble())).toFloat()
    val yValue = sin(Math.toRadians(lightAngle.toDouble())).toFloat()
    val lightDirection: Vector4f = directionalLight.position
    lightDirection.x = 0f
    lightDirection.y = yValue
    lightDirection.z = zValue
    lightDirection.normalize()
    lightDirection.w = 0.0f
}
