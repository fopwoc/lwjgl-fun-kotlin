package io.github.fopwoc.graphics.vulkan

import org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout
import org.pmw.tinylog.Logger


interface DescriptorSetLayout {
    val vkDescriptorLayout: Long
    val device: Device

    fun cleanup() {
        Logger.debug("Destroying descriptor set layout")
        vkDestroyDescriptorSetLayout(device.vkDevice, vkDescriptorLayout, null)
    }
}




