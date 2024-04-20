package io.github.fopwoc.graphics.vulkan

import io.github.fopwoc.graphics.vulkan.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO
import org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo

open class SimpleDescriptorSetLayout(
    override val device: Device,
    descriptorType: Int,
    binding: Int,
    stage: Int
) : DescriptorSetLayout {
    override val vkDescriptorLayout: Long

    init {
        MemoryStack.stackPush().use { stack ->
            val layoutBindings = VkDescriptorSetLayoutBinding.calloc(1, stack)
            layoutBindings[0]
                .binding(binding)
                .descriptorType(descriptorType)
                .descriptorCount(1)
                .stageFlags(stage)
            val layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                .pBindings(layoutBindings)
            val pSetLayout = stack.mallocLong(1)
            vkCheck(
                vkCreateDescriptorSetLayout(device.vkDevice, layoutInfo, null, pSetLayout),
                "Failed to create descriptor set layout"
            )
            vkDescriptorLayout = pSetLayout[0]
        }
    }
}
