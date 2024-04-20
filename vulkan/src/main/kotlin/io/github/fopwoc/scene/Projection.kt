package io.github.fopwoc.scene

import io.github.fopwoc.core.EngineProperties
import org.joml.Matrix4f

class Projection {

    var projectionMatrix: Matrix4f = Matrix4f()

    fun resize(width: Int, height: Int) {
        val engProps = EngineProperties.getInstance()
        projectionMatrix.identity()
        projectionMatrix.perspective(
            engProps.fov,
            width.toFloat() / height.toFloat(),
            engProps.zNear,
            engProps.zFar,
            true
        )
    }
}
