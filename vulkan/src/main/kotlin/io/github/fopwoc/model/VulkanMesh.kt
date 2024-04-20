package io.github.fopwoc.model

import io.github.fopwoc.graphics.vulkan.VulkanBuffer

@JvmRecord
data class VulkanMesh(
    val verticesBuffer: VulkanBuffer,
    val indicesBuffer: VulkanBuffer,
    val numIndices: Int
) {
    fun cleanup() {
        verticesBuffer.cleanup()
        indicesBuffer.cleanup()
    }
}
