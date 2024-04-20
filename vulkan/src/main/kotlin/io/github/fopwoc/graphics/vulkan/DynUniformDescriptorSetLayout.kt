package io.github.fopwoc.graphics.vulkan

import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC


class DynUniformDescriptorSetLayout(device: Device?, binding: Int, stage: Int) :
    SimpleDescriptorSetLayout(device!!, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, binding, stage)



