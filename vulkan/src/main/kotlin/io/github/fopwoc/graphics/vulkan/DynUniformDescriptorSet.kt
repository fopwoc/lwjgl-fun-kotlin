package io.github.fopwoc.graphics.vulkan

import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC

class DynUniformDescriptorSet(
    descriptorPool: DescriptorPool?,
    descriptorSetLayout: DescriptorSetLayout?,
    buffer: VulkanBuffer?,
    binding: Int,
    size: Long
) : SimpleDescriptorSet(
    descriptorPool!!,
    descriptorSetLayout!!,
    buffer!!,
    binding,
    VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC,
    size
)
