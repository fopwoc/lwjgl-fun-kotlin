package io.github.fopwoc.model

import io.github.fopwoc.graphics.vulkan.VulkanBuffer

@JvmRecord
data class TransferBuffers(
    val srcBuffer: VulkanBuffer,
    val dstBuffer: VulkanBuffer
)
