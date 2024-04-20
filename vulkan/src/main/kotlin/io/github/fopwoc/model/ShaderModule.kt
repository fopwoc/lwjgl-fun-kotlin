package io.github.fopwoc.model

import org.lwjgl.vulkan.VkSpecializationInfo

@JvmRecord
data class ShaderModule(
    val shaderStage: Int,
    val handle: Long,
    val specInfo: VkSpecializationInfo?,
)
