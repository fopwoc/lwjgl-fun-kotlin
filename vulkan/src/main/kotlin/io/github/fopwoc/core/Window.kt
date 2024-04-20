package io.github.fopwoc.core

import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW.GLFW_CLIENT_API
import org.lwjgl.glfw.GLFW.GLFW_FALSE
import org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
import org.lwjgl.glfw.GLFW.GLFW_MAXIMIZED
import org.lwjgl.glfw.GLFW.GLFW_NO_API
import org.lwjgl.glfw.GLFW.GLFW_PRESS
import org.lwjgl.glfw.GLFW.GLFW_RELEASE
import org.lwjgl.glfw.GLFW.glfwCreateWindow
import org.lwjgl.glfw.GLFW.glfwDefaultWindowHints
import org.lwjgl.glfw.GLFW.glfwDestroyWindow
import org.lwjgl.glfw.GLFW.glfwGetKey
import org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor
import org.lwjgl.glfw.GLFW.glfwGetVideoMode
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFW.glfwPollEvents
import org.lwjgl.glfw.GLFW.glfwSetFramebufferSizeCallback
import org.lwjgl.glfw.GLFW.glfwSetKeyCallback
import org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose
import org.lwjgl.glfw.GLFW.glfwTerminate
import org.lwjgl.glfw.GLFW.glfwWindowHint
import org.lwjgl.glfw.GLFW.glfwWindowShouldClose
import org.lwjgl.glfw.GLFWKeyCallbackI
import org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported
import org.lwjgl.system.MemoryUtil

class Window(
    title: String,
    keyCallback: GLFWKeyCallbackI? = null
) {
    val mouseInput: MouseInput
    val windowHandle: Long
    var width: Int
        private set
    var height: Int
        private set
    var resized = false
        private set

    init {
        if (!glfwInit()) {
            throw IllegalStateException("Unable to initialize GLFW")
        }

        if (!glfwVulkanSupported()) {
            throw IllegalStateException("Cannot find a compatible Vulkan installable client driver (ICD)")
        }

        val vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor())!!
        width = vidMode.width()
        height = vidMode.height()

        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        glfwWindowHint(GLFW_MAXIMIZED, GLFW_FALSE)

        // Create the window
        windowHandle = glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL)
        if (windowHandle == MemoryUtil.NULL) {
            throw RuntimeException("Failed to create the GLFW window")
        }

        glfwSetFramebufferSizeCallback(windowHandle) { window, w, h ->
            // resize(w, h)
        }

        glfwSetKeyCallback(windowHandle) { window, key, scancode, action, mods ->
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(window, true)
            }

            keyCallback?.invoke(window, key, scancode, action, mods)
        }
        mouseInput = MouseInput(windowHandle)
    }

    fun cleanup() {
        glfwFreeCallbacks(windowHandle)
        glfwDestroyWindow(windowHandle)
        glfwTerminate()
    }

    fun isKeyPressed(keyCode: Int): Boolean {
        return glfwGetKey(windowHandle, keyCode) == GLFW_PRESS
    }

    fun pollEvents() {
        glfwPollEvents()
        mouseInput.input()
    }

    fun resetResized() {
        resized = false
    }

    fun resize(width: Int, height: Int) {
        resized = true
        this.width = width
        this.height = height
    }

    fun setResized(resized: Boolean) {
        this.resized = resized
    }

    fun setShouldClose() {
        glfwSetWindowShouldClose(windowHandle, true)
    }

    fun shouldClose(): Boolean {
        return glfwWindowShouldClose(windowHandle)
    }
}
