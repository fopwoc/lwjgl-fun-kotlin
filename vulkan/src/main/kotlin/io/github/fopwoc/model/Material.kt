
package io.github.fopwoc.model

import org.joml.Vector4f

@JvmRecord
data class Material(
    val texturePath: String? = null,
    val normalMapPath: String? = null,
    val metalRoughMap: String? = null,
    val diffuseColor: Vector4f = DEFAULT_COLOR,
    val roughnessFactor: Float = 0.0f,
    val metallicFactor: Float = 0.0f
) {
    companion object {
        val DEFAULT_COLOR = Vector4f(1.0f, 1.0f, 1.0f, 1.0f)
    }
}
