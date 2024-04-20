package io.github.fopwoc.model

import io.github.fopwoc.graphics.vulkan.DescriptorSetLayout
import io.github.fopwoc.graphics.vulkan.ShaderProgram

@JvmRecord
data class PipeLineCreationInfo(
    val vkRenderPass: Long,
    val shaderProgram: ShaderProgram,
    val numColorAttachments: Int,
    val hasDepthAttachment: Boolean,
    val useBlend: Boolean,
    val pushConstantsSize: Int,
    val viInputStateInfo: VertexInputStateInfo,
    val descriptorSetLayouts: Array<DescriptorSetLayout>
) {
    fun cleanup() {
        viInputStateInfo.cleanup()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PipeLineCreationInfo

        if (vkRenderPass != other.vkRenderPass) return false
        if (shaderProgram != other.shaderProgram) return false
        if (numColorAttachments != other.numColorAttachments) return false
        if (hasDepthAttachment != other.hasDepthAttachment) return false
        if (pushConstantsSize != other.pushConstantsSize) return false
        if (viInputStateInfo != other.viInputStateInfo) return false
        return descriptorSetLayouts.contentEquals(other.descriptorSetLayouts)
    }

    override fun hashCode(): Int {
        var result = vkRenderPass.hashCode()
        result = 31 * result + shaderProgram.hashCode()
        result = 31 * result + numColorAttachments
        result = 31 * result + hasDepthAttachment.hashCode()
        result = 31 * result + pushConstantsSize
        result = 31 * result + viInputStateInfo.hashCode()
        result = 31 * result + descriptorSetLayouts.contentHashCode()
        return result
    }
}
