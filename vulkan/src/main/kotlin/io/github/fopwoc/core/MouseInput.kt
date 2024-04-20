package io.github.fopwoc.core

import org.joml.Vector2f
import org.lwjgl.glfw.GLFW

class MouseInput(windowHandle: Long) {
    val currentPos: Vector2f
    val displVec: Vector2f
    private val previousPos: Vector2f
    private var inWindow: Boolean
    var isLeftButtonPressed: Boolean
    var isRightButtonPressed: Boolean

    init {
        previousPos = Vector2f(-1f, -1f)
        currentPos = Vector2f()
        displVec = Vector2f()
        isLeftButtonPressed = false
        isRightButtonPressed = false
        inWindow = false
        GLFW.glfwSetCursorPosCallback(windowHandle) { handle: Long, xpos: Double, ypos: Double ->
            currentPos.x = xpos.toFloat()
            currentPos.y = ypos.toFloat()
        }
        GLFW.glfwSetCursorEnterCallback(
            windowHandle
        ) { handle: Long, entered: Boolean -> inWindow = entered }
        GLFW.glfwSetMouseButtonCallback(
            windowHandle
        ) { handle: Long, button: Int, action: Int, mode: Int ->
            isLeftButtonPressed = button == GLFW.GLFW_MOUSE_BUTTON_1 && action == GLFW.GLFW_PRESS
            isLeftButtonPressed = button == GLFW.GLFW_PRESS
            isRightButtonPressed = button == GLFW.GLFW_MOUSE_BUTTON_2 && action == GLFW.GLFW_PRESS
        }

        GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED)
    }

    fun input() {
        displVec.x = 0f
        displVec.y = 0f
        if (previousPos.x > 0 && previousPos.y > 0 && inWindow) {
            val deltax = (currentPos.x - previousPos.x).toDouble()
            val deltay = (currentPos.y - previousPos.y).toDouble()
            val rotateX = deltax != 0.0
            val rotateY = deltay != 0.0
            if (rotateX) {
                displVec.y = deltax.toFloat()
            }
            if (rotateY) {
                displVec.x = deltay.toFloat()
            }
        }
        previousPos.x = currentPos.x
        previousPos.y = currentPos.y
    }
}
