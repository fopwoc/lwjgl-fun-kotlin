package io.github.fopwoc.model

import org.lwjgl.vulkan.VkSpecializationInfo





@JvmRecord
data class ShaderModuleData(
    val shaderStage: Int,
    val shaderSpvFile: String,
    val specInfo: VkSpecializationInfo?,
) {
    constructor(shaderStage: Int, shaderSpvFile: String) : this(shaderStage, shaderSpvFile, null)
}
