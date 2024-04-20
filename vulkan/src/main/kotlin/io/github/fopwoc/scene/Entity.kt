package io.github.fopwoc.scene

import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

class Entity(
    val id: String,
    val modelId: String,
    val position: Vector3f
) {

    val modelMatrix: Matrix4f
    val rotation: Quaternionf
    var scale: Float = 1f
        set(value) {
            field = value
            updateModelMatrix()
        }

    init {
        rotation = Quaternionf()
        modelMatrix = Matrix4f()
        updateModelMatrix()
    }

    fun resetRotation() {
        rotation.x = 0.0f
        rotation.y = 0.0f
        rotation.z = 0.0f
        rotation.w = 1.0f
    }

    fun setPosition(x: Float, y: Float, z: Float) {
        position.x = x
        position.y = y
        position.z = z
        updateModelMatrix()
    }

    fun updateModelMatrix() {
        modelMatrix.translationRotateScale(position, rotation, scale)
    }
}
