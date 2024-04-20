
package io.github.fopwoc.graphics.vulkan

import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER


class SamplerDescriptorSetLayout(device: Device?, binding: Int, stage: Int) :
    SimpleDescriptorSetLayout(device!!, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, binding, stage)


