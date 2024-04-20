package io.github.fopwoc.graphics.vulkan

import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER

class StorageDescriptorSet(
    descriptorPool: DescriptorPool,
    descriptorSetLayout: DescriptorSetLayout,
    buffer: VulkanBuffer,
    binding: Int
) :
    SimpleDescriptorSet(
        descriptorPool,
        descriptorSetLayout,
        buffer,
        binding,
        VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
        buffer.requestedSize
    )
