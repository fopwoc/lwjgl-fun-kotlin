import org.intellij.lang.annotations.Language
import org.joml.Matrix4f
import org.lwjgl.BufferUtils.createFloatBuffer
import org.lwjgl.Version
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR
import org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR
import org.lwjgl.glfw.GLFW.GLFW_FALSE
import org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
import org.lwjgl.glfw.GLFW.GLFW_KEY_Q
import org.lwjgl.glfw.GLFW.GLFW_KEY_W
import org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE
import org.lwjgl.glfw.GLFW.GLFW_OPENGL_FORWARD_COMPAT
import org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE
import org.lwjgl.glfw.GLFW.GLFW_RELEASE
import org.lwjgl.glfw.GLFW.GLFW_RESIZABLE
import org.lwjgl.glfw.GLFW.GLFW_TRUE
import org.lwjgl.glfw.GLFW.GLFW_VISIBLE
import org.lwjgl.glfw.GLFW.glfwCreateWindow
import org.lwjgl.glfw.GLFW.glfwDefaultWindowHints
import org.lwjgl.glfw.GLFW.glfwDestroyWindow
import org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor
import org.lwjgl.glfw.GLFW.glfwGetVideoMode
import org.lwjgl.glfw.GLFW.glfwGetWindowSize
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFW.glfwMakeContextCurrent
import org.lwjgl.glfw.GLFW.glfwPollEvents
import org.lwjgl.glfw.GLFW.glfwSetErrorCallback
import org.lwjgl.glfw.GLFW.glfwSetKeyCallback
import org.lwjgl.glfw.GLFW.glfwSetWindowPos
import org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose
import org.lwjgl.glfw.GLFW.glfwShowWindow
import org.lwjgl.glfw.GLFW.glfwSwapInterval
import org.lwjgl.glfw.GLFW.glfwTerminate
import org.lwjgl.glfw.GLFW.glfwWindowHint
import org.lwjgl.glfw.GLFW.glfwWindowShouldClose
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWVidMode
import org.lwjgl.opengl.ARBVertexArrayObject.glBindVertexArray
import org.lwjgl.opengl.ARBVertexArrayObject.glDeleteVertexArrays
import org.lwjgl.opengl.ARBVertexArrayObject.glGenVertexArrays
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT
import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL11.GL_TRIANGLES
import org.lwjgl.opengl.GL11.GL_TRUE
import org.lwjgl.opengl.GL11.glClear
import org.lwjgl.opengl.GL11.glClearColor
import org.lwjgl.opengl.GL11.glDrawArrays
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.GL_STATIC_DRAW
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL15.glBufferData
import org.lwjgl.opengl.GL15.glDeleteBuffers
import org.lwjgl.opengl.GL15.glGenBuffers
import org.lwjgl.opengl.GL20.GL_COMPILE_STATUS
import org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER
import org.lwjgl.opengl.GL20.GL_LINK_STATUS
import org.lwjgl.opengl.GL20.GL_VERTEX_SHADER
import org.lwjgl.opengl.GL20.glAttachShader
import org.lwjgl.opengl.GL20.glCompileShader
import org.lwjgl.opengl.GL20.glCreateProgram
import org.lwjgl.opengl.GL20.glCreateShader
import org.lwjgl.opengl.GL20.glDeleteProgram
import org.lwjgl.opengl.GL20.glDeleteShader
import org.lwjgl.opengl.GL20.glEnableVertexAttribArray
import org.lwjgl.opengl.GL20.glGetAttribLocation
import org.lwjgl.opengl.GL20.glGetProgramInfoLog
import org.lwjgl.opengl.GL20.glGetProgrami
import org.lwjgl.opengl.GL20.glGetShaderInfoLog
import org.lwjgl.opengl.GL20.glGetShaderi
import org.lwjgl.opengl.GL20.glGetUniformLocation
import org.lwjgl.opengl.GL20.glLinkProgram
import org.lwjgl.opengl.GL20.glShaderSource
import org.lwjgl.opengl.GL20.glUniformMatrix4fv
import org.lwjgl.opengl.GL20.glUseProgram
import org.lwjgl.opengl.GL20.glVertexAttribPointer
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL30.glBindFragDataLocation
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil.NULL
import java.nio.IntBuffer
import kotlin.properties.Delegates
import kotlin.system.exitProcess

@Language("GLSL")
const val vertexShaderSrc = """
#version 150 core

in vec3 position;
in vec3 color;

out vec3 vertexColor;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;

void main() {
    vertexColor = color;
    mat4 mvp = projection * view * model;
    gl_Position = mvp * vec4(position, 1);
}
"""

@Language("GLSL")
const val fragmentShaderSrc = """
#version 150 core

in vec3 vertexColor;

out vec4 fragColor;

void main() {
    fragColor = vec4(vertexColor, 1.0);
}
"""

const val floatSize = 4

private var window: Long = 0
private var timer = Timer()
var lastLoopTime = 0.0

const val TARGET_FPS = 75
var TARGET_UPS = 30

private const val uniModel = 0
private var angle = 0f
private const val anglePerSecond = 1f

private var vao by Delegates.notNull<Int>()
private var vbo by Delegates.notNull<Int>()
private var vertexShader by Delegates.notNull<Int>()
private var fragmentShader by Delegates.notNull<Int>()
private var shaderProgram by Delegates.notNull<Int>()

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
    check(glfwInit()) { "Unable to initialize GLFW" }

    // Configure GLFW
    glfwDefaultWindowHints() // optional, the current window hints are already the default
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2)
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE) // the window will stay hidden after creation
    glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE) // the window will be resizable

    // Create the window
    window = glfwCreateWindow(800, 600, "Hello LWJGL World!", NULL, NULL)
    if (window == NULL) throw RuntimeException("Failed to create the GLFW window")

    // Setup a key callback. It will be called every time a key is pressed, repeated or released.
    glfwSetKeyCallback(window) { window, key, scancode, action, mods ->
        if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
            glfwSetWindowShouldClose(window, true) // We will detect this in the rendering loop
            dispose()
            exitProcess(0)
        }

        if (key == GLFW_KEY_Q && action == GLFW_RELEASE) {
            TARGET_UPS -= 1
        }
        if (key == GLFW_KEY_W && action == GLFW_RELEASE) {
            TARGET_UPS += 1
        }
    }

    stackPush().use { stack ->
        val pWidth: IntBuffer = stack.mallocInt(1) // int*
        val pHeight: IntBuffer = stack.mallocInt(1) // int*

        // Get the window size passed to glfwCreateWindow
        glfwGetWindowSize(window, pWidth, pHeight)

        // Get the resolution of the primary monitor
        val vidmode: GLFWVidMode = glfwGetVideoMode(glfwGetPrimaryMonitor())!!

        // Center the window
        glfwSetWindowPos(
            window,
            (vidmode.width() - pWidth[0]) / 2,
            (vidmode.height() - pHeight[0]) / 2
        )
    }

    // Make the OpenGL context current
    glfwMakeContextCurrent(window)

    // This line is critical for LWJGL's interoperation with GLFW's
    // OpenGL context, or any context that is managed externally.
    // LWJGL detects the context that is current in the current thread,
    // creates the GLCapabilities instance and makes the OpenGL
    // bindings available for use.
    GL.createCapabilities()

    // Enable v-sync
    glfwSwapInterval(1)

    // Make the window visible
    glfwShowWindow(window)

    glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

    vao = glGenVertexArrays()
    glBindVertexArray(vao)

    stackPush().use { stack ->
        val vertices = stack.mallocFloat(3 * 6)
        vertices.put(-0.6f).put(-0.4f).put(0f).put(1f).put(0f).put(0f)
        vertices.put(0.6f).put(-0.4f).put(0f).put(0f).put(1f).put(0f)
        vertices.put(0f).put(0.6f).put(0f).put(0f).put(0f).put(1f)
        vertices.flip()
        vbo = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW)
    }

    vertexShader = glCreateShader(GL_VERTEX_SHADER)
    glShaderSource(vertexShader, vertexShaderSrc)
    glCompileShader(vertexShader)

    Unit.also {
        val status: Int = glGetShaderi(vertexShader, GL_COMPILE_STATUS)
        if (status != GL_TRUE) {
            throw java.lang.RuntimeException(glGetShaderInfoLog(vertexShader))
        }
    }

    fragmentShader = glCreateShader(GL_FRAGMENT_SHADER)
    glShaderSource(fragmentShader, fragmentShaderSrc)
    glCompileShader(fragmentShader)

    Unit.also {
        val status: Int = glGetShaderi(vertexShader, GL_COMPILE_STATUS)
        if (status != GL_TRUE) {
            throw java.lang.RuntimeException(glGetShaderInfoLog(vertexShader))
        }
    }

    shaderProgram = glCreateProgram()
    glAttachShader(shaderProgram, vertexShader)
    glAttachShader(shaderProgram, fragmentShader)
    glBindFragDataLocation(shaderProgram, 0, "fragColor")
    glLinkProgram(shaderProgram)

    val status: Int = glGetProgrami(shaderProgram, GL_LINK_STATUS)
    if (status != GL_TRUE) {
        throw java.lang.RuntimeException(glGetProgramInfoLog(shaderProgram))
    }

    glUseProgram(shaderProgram)

    val posAttrib: Int = glGetAttribLocation(shaderProgram, "position")
    glEnableVertexAttribArray(posAttrib)
    glVertexAttribPointer(posAttrib, 3, GL_FLOAT, false, 6 * floatSize, 0)

    val colAttrib: Int = glGetAttribLocation(shaderProgram, "color")
    glEnableVertexAttribArray(colAttrib)
    glVertexAttribPointer(colAttrib, 3, GL_FLOAT, false, 6 * floatSize, (3 * floatSize).toLong())

    val uniModel = glGetUniformLocation(shaderProgram, "model")
    val model = Matrix4f()
    glUniformMatrix4fv(uniModel, false, model.get(createFloatBuffer(16)))

    val uniView = glGetUniformLocation(shaderProgram, "view")
    val view = Matrix4f()
    glUniformMatrix4fv(uniView, false, view.get(createFloatBuffer(16)))

    val uniProjection = glGetUniformLocation(shaderProgram, "projection")
    val ratio = 640f / 480f
    val projection: Matrix4f = Matrix4f().ortho(-ratio, ratio, -1f, 1f, -1f, 1f)
    glUniformMatrix4fv(uniProjection, false, projection.get(createFloatBuffer(16)))
}

private fun loop() {
    var delta: Float
    var accumulator = 0f

    var alpha: Float

    lastLoopTime = getTime()

    while (!glfwWindowShouldClose(window)) {
        val interval: Float = 1f / TARGET_UPS

        /* Get delta time and update the accumulator */
        delta = timer.delta
        accumulator += delta

        input()

        /* Update game and timer UPS if enough time has passed */
        while (accumulator >= interval) {
            update(delta)
            timer.updateUPS()
            accumulator -= interval
        }

        /* Calculate alpha value for interpolation */
        alpha = accumulator / interval

        /* Render game and update timer FPS */
        render(alpha)
        timer.updateFPS()

        /* Update timer */
        timer.update()

        println("FPS: ${timer.fPS}; UPS ${timer.uPS}")
    }
}

private fun input() {}

private fun update(delta: Float) {
    angle += delta * anglePerSecond
}

private fun render(alpha: Float) {
    glClear(GL_COLOR_BUFFER_BIT or GL30.GL_DEPTH_BUFFER_BIT)

    val model: Matrix4f = Matrix4f().rotate(angle, 0f, 0f, 1f)
    glUniformMatrix4fv(uniModel, false, model.get(createFloatBuffer(16)))

    glDrawArrays(GL_TRIANGLES, 0, 3)

    GLFW.glfwSwapBuffers(window)
    glfwPollEvents()
}

private fun dispose() {
    glDeleteVertexArrays(vao)
    glDeleteBuffers(vbo)
    glDeleteShader(vertexShader)
    glDeleteShader(fragmentShader)
    glDeleteProgram(shaderProgram)

    // Free the window callbacks and destroy the window
    glfwFreeCallbacks(window)
    glfwDestroyWindow(window)

    // Terminate GLFW and free the error callback
    glfwTerminate()
    glfwSetErrorCallback(null)!!.free()
}

fun getTime(): Double {
    return System.nanoTime() / 1000000000.0
}
