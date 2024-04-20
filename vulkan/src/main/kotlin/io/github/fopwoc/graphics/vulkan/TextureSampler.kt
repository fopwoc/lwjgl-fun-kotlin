package io.github.fopwoc.graphics.vulkan

import io.github.fopwoc.graphics.vulkan.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.VK_BORDER_COLOR_INT_OPAQUE_BLACK
import org.lwjgl.vulkan.VK10.VK_COMPARE_OP_ALWAYS
import org.lwjgl.vulkan.VK10.VK_FILTER_LINEAR
import org.lwjgl.vulkan.VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT
import org.lwjgl.vulkan.VK10.VK_SAMPLER_MIPMAP_MODE_LINEAR
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO
import org.lwjgl.vulkan.VK10.vkCreateSampler
import org.lwjgl.vulkan.VK10.vkDestroySampler
import org.lwjgl.vulkan.VkSamplerCreateInfo


class TextureSampler(
    val device: Device,
    mipLevels: Int,
    anisotropyEnable: Boolean
) {
    val vkSampler: Long

    init {
        MemoryStack.stackPush().use { stack ->
            val samplerInfo = VkSamplerCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                .magFilter(VK_FILTER_LINEAR)
                .minFilter(VK_FILTER_LINEAR)
                .addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                .addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                .addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                .unnormalizedCoordinates(false)
                .compareEnable(false)
                .compareOp(VK_COMPARE_OP_ALWAYS)
                .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
                .minLod(0.0f)
                .maxLod(mipLevels.toFloat())
                .mipLodBias(0.0f)
            if (anisotropyEnable && device.samplerAnisotropy) {
                samplerInfo
                    .anisotropyEnable(true)
                    .maxAnisotropy(MAX_ANISOTROPY.toFloat())
            }
            val lp = stack.mallocLong(1)
            vkCheck(
                vkCreateSampler(device.vkDevice, samplerInfo, null, lp),
                "Failed to create sampler"
            )
            vkSampler = lp[0]
        }
    }

    fun cleanup() {
        vkDestroySampler(device.vkDevice, vkSampler, null)
    }

    companion object {
        private const val MAX_ANISOTROPY = 16
    }
}
