package io.github.fopwoc.model

import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo


interface VertexInputStateInfo {
    var vi: VkPipelineVertexInputStateCreateInfo
    fun cleanup() {
        vi.free()
    }
}

