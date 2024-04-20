package io.github.fopwoc.graphics.lightning

import io.github.fopwoc.model.VertexInputStateInfo
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo


class EmptyVertexBufferStructure : VertexInputStateInfo {

    override var vi: VkPipelineVertexInputStateCreateInfo = VkPipelineVertexInputStateCreateInfo.calloc()

    init {
        vi.sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
            .pVertexBindingDescriptions(null)
            .pVertexAttributeDescriptions(null)
    }
}
