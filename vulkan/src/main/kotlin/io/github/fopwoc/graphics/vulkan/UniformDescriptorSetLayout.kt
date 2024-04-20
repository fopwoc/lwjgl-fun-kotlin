
package io.github.fopwoc.graphics.vulkan

import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER

class UniformDescriptorSetLayout(device: Device?, binding: Int, stage: Int) :
    SimpleDescriptorSetLayout(device!!, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, binding, stage)
