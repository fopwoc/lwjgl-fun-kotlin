package io.github.fopwoc.scene

import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f


class Camera {
    private val direction: Vector3f = Vector3f()
    val position: Vector3f = Vector3f()
    private val right: Vector3f = Vector3f()
    private val rotation: Vector2f = Vector2f()
    private val up: Vector3f = Vector3f()
    val viewMatrix: Matrix4f = Matrix4f()
    var hasMoved: Boolean = false
        private set

    fun addRotation(x: Float, y: Float) {
        rotation.add(x, y)
        recalculate()
    }

    fun setHasMoved(hasMoved: Boolean) {
        this.hasMoved = hasMoved
    }

    fun moveBackwards(inc: Float) {
        viewMatrix.positiveZ(direction).negate().mul(inc)
        position.sub(direction)
        recalculate()
    }

    fun moveDown(inc: Float) {
        viewMatrix.positiveY(up).mul(inc)
        position.sub(up)
        recalculate()
    }

    fun moveForward(inc: Float) {
        viewMatrix.positiveZ(direction).negate().mul(inc)
        position.add(direction)
        recalculate()
    }

    fun moveLeft(inc: Float) {
        viewMatrix.positiveX(right).mul(inc)
        position.sub(right)
        recalculate()
    }

    fun moveRight(inc: Float) {
        viewMatrix.positiveX(right).mul(inc)
        position.add(right)
        recalculate()
    }

    fun moveUp(inc: Float) {
        viewMatrix.positiveY(up).mul(inc)
        position.add(up)
        recalculate()
    }

    private fun recalculate() {
        hasMoved = true

        viewMatrix.identity()
            .rotateX(rotation.x)
            .rotateY(rotation.y)
            .translate(-position.x, -position.y, -position.z)
    }

    fun setPosition(x: Float, y: Float, z: Float) {
        position[x, y] = z
        recalculate()
    }

    fun setRotation(x: Float, y: Float) {
        rotation[x] = y
        recalculate()
    }
}
