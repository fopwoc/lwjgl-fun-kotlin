
package io.github.fopwoc.model

import io.github.fopwoc.graphics.vulkan.Texture
import org.joml.Vector4f

@JvmRecord
data class VulkanMaterial(
    val diffuseColor: Vector4f,
    val texture: Texture,
    val hasTexture: Boolean,
    val normalMap: Texture,
    val hasNormalMap: Boolean,
    val metalRoughMap: Texture,
    val hasMetalRoughMap: Boolean,
    val metallicFactor: Float,
    val roughnessFactor: Float,
    val vulkanMeshList: MutableList<VulkanMesh>
) {
    val isTransparent: Boolean
        get() = texture.hasTransparencies
}
