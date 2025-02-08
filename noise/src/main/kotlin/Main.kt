import de.articdive.jnoise.core.api.functions.Interpolation
import de.articdive.jnoise.generators.noise_parameters.fade_functions.FadeFunction
import de.articdive.jnoise.generators.noisegen.opensimplex.FastSimplexNoiseGenerator
import de.articdive.jnoise.generators.noisegen.worley.WorleyNoiseGenerator
import de.articdive.jnoise.pipeline.JNoise
import org.lwjgl.BufferUtils
import org.lwjgl.Version
import org.lwjgl.glfw.Callbacks
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.glfwGetWindowSize
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_POINT_SMOOTH
import org.lwjgl.opengl.GL11.GL_QUADS
import org.lwjgl.opengl.GL11.glBegin
import org.lwjgl.opengl.GL11.glClear
import org.lwjgl.opengl.GL11.glClearColor
import org.lwjgl.opengl.GL11.glColor3f
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glEnd
import org.lwjgl.opengl.GL11.glVertex2f
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import kotlin.time.Duration
import kotlin.time.measureTime

private var window: Long = 0

fun main(args: Array<String>) {
    println("Hello LWJGL " + Version.getVersion() + "!")

    init()
    loop()
    dispose()
}

private fun init() {
    // Setup an error callback. The default implementation
    // will print the error message in System.err.
    GLFWErrorCallback.createPrint(System.err).set()

    // Initialize GLFW. Most GLFW functions will not work before doing this.
    check(GLFW.glfwInit()) { "Unable to initialize GLFW" }

    // Configure GLFW
    GLFW.glfwDefaultWindowHints() // optional, the current window hints are already the default
    GLFW.glfwWindowHint(
        GLFW.GLFW_VISIBLE,
        GLFW.GLFW_FALSE
    ) // the window will stay hidden after creation
    GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE) // the window will be resizable

    // Create the window
    window = GLFW.glfwCreateWindow(800, 600, "Hello World!", MemoryUtil.NULL, MemoryUtil.NULL)
    if (window == MemoryUtil.NULL) throw RuntimeException("Failed to create the GLFW window")

    // Setup a key callback. It will be called every time a key is pressed, repeated or released.
    GLFW.glfwSetKeyCallback(
        window
    ) { window: Long, key: Int, scancode: Int, action: Int, mods: Int ->
        if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_RELEASE) {
            GLFW.glfwSetWindowShouldClose(
                window,
                true
            ) // We will detect this in the rendering loop
        }
    }

    MemoryStack.stackPush().use { stack ->
        val pWidth = stack.mallocInt(1) // int*
        val pHeight = stack.mallocInt(1) // int*

        // Get the window size passed to glfwCreateWindow
        GLFW.glfwGetWindowSize(window, pWidth, pHeight)

        // Get the resolution of the primary monitor
        val vidmode = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor())

        // Center the window
        GLFW.glfwSetWindowPos(
            window,
            (vidmode!!.width() - pWidth[0]) / 2,
            (vidmode.height() - pHeight[0]) / 2
        )
    }
    // Make the OpenGL context current
    GLFW.glfwMakeContextCurrent(window)
    // Enable v-sync
    GLFW.glfwSwapInterval(1)

    // Make the window visible
    GLFW.glfwShowWindow(window)
}

val seed: Long = 3301

val perlinNoisePipeline = JNoise.newBuilder().perlin(seed, Interpolation.COSINE, FadeFunction.QUADRATIC_RATIONAL).build()
val worleyNoisePipeline = JNoise.newBuilder().worley(WorleyNoiseGenerator.newBuilder().setSeed(seed).build()).build()
val simplexNoisePipeline = JNoise.newBuilder().fastSimplex(FastSimplexNoiseGenerator.newBuilder().setSeed(seed).build()).build()
val valueNoisePipeline = JNoise.newBuilder().value(seed, Interpolation.COSINE, FadeFunction.QUADRATIC_RATIONAL).build()
val whiteNoisePipeline = JNoise.newBuilder().white(seed).build()

enum class NoiseType {
    PERLIN,
    WORLEY,
    SIMPLEX,
    VALUE,
    WHITE
}

var currentType: NoiseType = NoiseType.WORLEY

val step: Float = 0.1f
val moveSteap: Float = 1f

var zCord: Float = 0.0f
var zoom: Float = 10.0f

var resolutionWidth: Int = 100
var resolutionHeight: Int = 1

var offsetX: Double = 0.0
var offsetY: Double = 0.0

private fun loop() {
    GL.createCapabilities()
    val frameCounter = FrameCounter()

    while (!GLFW.glfwWindowShouldClose(window)) {
        keys()
        frameCounter.count { render() }
    }
}

fun render() {
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
    glClear(GL11.GL_COLOR_BUFFER_BIT or GL11.GL_DEPTH_BUFFER_BIT)

    // lwjgl get the window size

    val w = BufferUtils.createIntBuffer(1)
    val h = BufferUtils.createIntBuffer(1)
    glfwGetWindowSize(window, w, h)
    val ratio: Float = h[0].toFloat() / w[0].toFloat()

    resolutionHeight = (resolutionWidth * ratio).toInt()

    // val noise = generateRandomNoise(width, height)

    glDisable(GL_POINT_SMOOTH)

    // draw noise
    glBegin(GL_QUADS)
    for (i in 0..<resolutionWidth) {
        for (j in 0..<resolutionHeight) {
            val value = when (currentType) {
                NoiseType.PERLIN -> perlinNoisePipeline.evaluateNoise(
                    (i.toDouble() + offsetX) / zoom,
                    (j.toDouble() + offsetY) / zoom,
                    zCord.toDouble()
                )
                NoiseType.WORLEY -> worleyNoisePipeline.evaluateNoise(
                    (i.toDouble() + offsetX) / zoom,
                    (j.toDouble() + offsetY) / zoom,
                    zCord.toDouble()
                )
                NoiseType.SIMPLEX -> simplexNoisePipeline.evaluateNoise(
                    (i.toDouble() + offsetX) / zoom,
                    (j.toDouble() + offsetY) / zoom,
                    zCord.toDouble()
                )

                NoiseType.VALUE -> {
                    valueNoisePipeline.evaluateNoise(
                        (i.toDouble() + offsetX) / zoom,
                        (j.toDouble() + offsetY) / zoom,
                        zCord.toDouble()
                    )
                }
                NoiseType.WHITE -> {
                    whiteNoisePipeline.evaluateNoise(
                        (i.toDouble() + offsetX) / zoom,
                        (j.toDouble() + offsetY) / zoom,
                        zCord.toDouble()
                    )
                }
            }

            val color = value.toFloat()
            val x = (i.toFloat() / resolutionWidth) * 2 - 1
            val y = (j.toFloat() / resolutionHeight) * 2 - 1
            val quadWidth = 2f / resolutionWidth
            val quadHeight = 2f / resolutionHeight

            drawQuad(x, y, quadWidth, quadHeight, color)
        }
    }
    glEnd()

    GLFW.glfwSwapBuffers(window)
    GLFW.glfwPollEvents()
}

fun drawQuad(x: Float, y: Float, width: Float, height: Float, color: Float) {
    when (currentType) {
        NoiseType.WORLEY -> glColor3f(0F, 0f, 0.8f * color)
        else -> glColor3f(color, color, color)
    }

    glVertex2f(x, y)
    glVertex2f(x + width, y)
    glVertex2f(x + width, y + height)
    glVertex2f(x, y + height)
}

private fun dispose() {
    // Free the window callbacks and destroy the window
    Callbacks.glfwFreeCallbacks(window)
    GLFW.glfwDestroyWindow(window)

    // Terminate GLFW and free the error callback
    GLFW.glfwTerminate()
    GLFW.glfwSetErrorCallback(null)!!.free()
}

class FrameCounter(
    startTime: Long = System.currentTimeMillis(),
) {
    var framesTotal: Long = 0
    var lastLogTime = startTime
    var frameTimes: MutableList<Duration> = mutableListOf()

    fun count(render: () -> Unit) {
        val timeElapsed: Duration = measureTime(render)
        frameTimes.add(timeElapsed)
        framesTotal += 1

        if (System.currentTimeMillis() - lastLogTime > 1000) {
            val averageFrameTime = frameTimes.map { it.inWholeMilliseconds }.average()
            val runtime = Runtime.getRuntime()
            println()
            println("FPS: ${frameTimes.size}; avrg frame time: ${averageFrameTime.toInt()} ms; Total frames: $framesTotal")
            println("Total Memory: ${runtime.totalMemory() / (1024 * 1024)} MB; Used Memory: " + (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024) + " MB")
            println("Resolution: ${resolutionWidth}x$resolutionHeight; Zoom: $zoom; Offset X: $offsetX; Offset Y: $offsetY; Z: $zCord")

            lastLogTime = System.currentTimeMillis()
            frameTimes.clear()
        }
    }
}

fun keys() {
    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) != GLFW.GLFW_PRESS && currentType == NoiseType.WORLEY) {
        zCord += 0.01f
    }

    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_1) == GLFW.GLFW_PRESS) {
        currentType = NoiseType.PERLIN
    }

    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_2) == GLFW.GLFW_PRESS) {
        currentType = NoiseType.WORLEY
    }

    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_3) == GLFW.GLFW_PRESS) {
        currentType = NoiseType.SIMPLEX
    }

    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_4) == GLFW.GLFW_PRESS) {
        currentType = NoiseType.VALUE
    }

    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_5) == GLFW.GLFW_PRESS) {
        currentType = NoiseType.WHITE
    }

    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_UP) == GLFW.GLFW_PRESS) {
        zCord += step
        println("Z: $zCord")
    }

    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_DOWN) == GLFW.GLFW_PRESS) {
        if (zCord - step > 0.0) {
            zCord -= step
            println("Z: $zCord")
        } else {
            println("Z can't be less than 0")
        }
    }

    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS) {
        zoom += step
        println("Zoom: $zoom")
    }

    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT) == GLFW.GLFW_PRESS) {
        if (zoom - step > 0.0) {
            zoom -= step
            println("Zoom: $zoom")
        } else {
            println("Zoom can't be less than 0")
        }
    }

    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) {
        offsetY += moveSteap
        println("Offset Y: $offsetY")
    }

    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS) {
        offsetX -= moveSteap
        println("Offset X: $offsetX")
    }

    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS) {
        offsetY -= moveSteap
        println("Offset Y: $offsetY")
    }

    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS) {
        offsetX += moveSteap
        println("Offset X: $offsetX")
    }

    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_PAGE_UP) == GLFW.GLFW_PRESS) {
        resolutionWidth += 1
        zoom += 0.1f

        println("Resolution: ${resolutionWidth}x$resolutionHeight")
    }

    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_PAGE_DOWN) == GLFW.GLFW_PRESS) {
        if (resolutionWidth - 1 > 1) {
            resolutionWidth -= 1
            zoom -= 0.1f

            println("Resolution: ${resolutionWidth}x$resolutionHeight")
        } else {
            println("Resolution can't be less than 1")
        }
    }

    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS) {
        resolutionWidth = 100
        offsetX = 0.0
        offsetY = 0.0
        zCord = 0.0f
        zoom = 10.0f
        println("Reset")
    }
}
