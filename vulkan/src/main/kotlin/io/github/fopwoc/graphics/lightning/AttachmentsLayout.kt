package io.github.fopwoc.graphics.lightning

import io.github.fopwoc.graphics.vulkan.DescriptorSetLayout
import io.github.fopwoc.graphics.vulkan.Device
import io.github.fopwoc.graphics.vulkan.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
import org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO
import org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo
import org.pmw.tinylog.Logger

class AttachmentsLayout(
    override val device: Device,
    numAttachments: Int
) : DescriptorSetLayout {

    override val vkDescriptorLayout: Long
    init {
        Logger.debug("Creating Attachments Layout")
        MemoryStack.stackPush().use { stack ->
            val layoutBindings = VkDescriptorSetLayoutBinding.calloc(numAttachments, stack)
            for (i in 0..<numAttachments) {
                layoutBindings[i]
                    .binding(i)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT)
            }

            val layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                .pBindings(layoutBindings)
            val lp = stack.mallocLong(1)

            vkCheck(
                vkCreateDescriptorSetLayout(device.vkDevice, layoutInfo, null, lp),
                "Failed to create descriptor set layout"
            )

            vkDescriptorLayout = lp[0]
        }
    }
}
