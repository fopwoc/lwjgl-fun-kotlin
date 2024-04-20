package io.github.fopwoc.graphics.vulkan

import io.github.fopwoc.graphics.vulkan.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.vma.Vma.vmaCreateAllocator
import org.lwjgl.util.vma.Vma.vmaDestroyAllocator
import org.lwjgl.util.vma.VmaAllocatorCreateInfo
import org.lwjgl.util.vma.VmaVulkanFunctions
import org.lwjgl.vulkan.VkDevice

class MemoryAllocator(
    instance: VulkanInstance,
    physicalDevice: PhysicalDevice,
    vkDevice: VkDevice
) {
    var vmaAllocator: Long = 0

    init {
        MemoryStack.stackPush().use { stack ->
            val pAllocator = stack.mallocPointer(1)
            val vmaVulkanFunctions = VmaVulkanFunctions.calloc(stack)
                .set(instance.vkInstance, vkDevice)
            val createInfo = VmaAllocatorCreateInfo.calloc(stack)
                .instance(instance.vkInstance)
                .device(vkDevice)
                .physicalDevice(physicalDevice.vkPhysicalDevice)
                .pVulkanFunctions(vmaVulkanFunctions)
            vkCheck(
                vmaCreateAllocator(createInfo, pAllocator),
                "Failed to create VMA allocator"
            )
            vmaAllocator = pAllocator[0]
        }
    }

    fun cleanUp() {
        vmaDestroyAllocator(vmaAllocator)
    }
}
