package io.github.fopwoc.graphics.vulkan

import io.github.fopwoc.model.VertexInputStateInfo
import org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_SFLOAT
import org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32_SFLOAT
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO
import org.lwjgl.vulkan.VK10.VK_VERTEX_INPUT_RATE_VERTEX
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription

class VertexBufferStructure : VertexInputStateInfo {

    private val viAttrs: VkVertexInputAttributeDescription.Buffer
    private val viBindings: VkVertexInputBindingDescription.Buffer
    override var vi: VkPipelineVertexInputStateCreateInfo

    init {
        viAttrs = VkVertexInputAttributeDescription.calloc(NUMBER_OF_ATTRIBUTES)
        viBindings = VkVertexInputBindingDescription.calloc(1)
        vi = VkPipelineVertexInputStateCreateInfo.calloc()

        var i = 0

        // Position
        viAttrs[i]
            .binding(0)
            .location(i)
            .format(VK_FORMAT_R32G32B32_SFLOAT)
            .offset(0)

        // Normal
        i++
        viAttrs[i]
            .binding(0)
            .location(i)
            .format(VK_FORMAT_R32G32B32_SFLOAT)
            .offset(POSITION_COMPONENTS * GraphConstants.FLOAT_LENGTH)

        // Tangent
        i++
        viAttrs[i]
            .binding(0)
            .location(i)
            .format(VK_FORMAT_R32G32B32_SFLOAT)
            .offset(NORMAL_COMPONENTS * GraphConstants.FLOAT_LENGTH + POSITION_COMPONENTS * GraphConstants.FLOAT_LENGTH)

        // BiTangent
        i++
        viAttrs[i]
            .binding(0)
            .location(i)
            .format(VK_FORMAT_R32G32B32_SFLOAT)
            .offset(
                NORMAL_COMPONENTS * GraphConstants.FLOAT_LENGTH * 2 + POSITION_COMPONENTS * GraphConstants.FLOAT_LENGTH
            )

        // Texture coordinates
        i++
        viAttrs[i]
            .binding(0)
            .location(i)
            .format(VK_FORMAT_R32G32_SFLOAT)
            .offset(
                NORMAL_COMPONENTS * GraphConstants.FLOAT_LENGTH * 3 + POSITION_COMPONENTS * GraphConstants.FLOAT_LENGTH
            )

        viBindings[0]
            .binding(0)
            .stride(
                POSITION_COMPONENTS * GraphConstants.FLOAT_LENGTH + NORMAL_COMPONENTS
                    * GraphConstants.FLOAT_LENGTH * 3 + TEXT_COORD_COMPONENTS * GraphConstants.FLOAT_LENGTH
            )
            .inputRate(VK_VERTEX_INPUT_RATE_VERTEX)

        vi
            .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
            .pVertexBindingDescriptions(viBindings)
            .pVertexAttributeDescriptions(viAttrs)
    }

    override fun cleanup() {
        super.cleanup()
        viBindings.free()
        viAttrs.free()
    }

    companion object {
        private const val POSITION_COMPONENTS = 3
        private const val TEXT_COORD_COMPONENTS: Int = 2
        private const val NORMAL_COMPONENTS = 3
        private const val NUMBER_OF_ATTRIBUTES = 5
    }
}
