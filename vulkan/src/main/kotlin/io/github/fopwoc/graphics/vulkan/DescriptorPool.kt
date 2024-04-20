package io.github.fopwoc.graphics.vulkan

import io.github.fopwoc.graphics.vulkan.VulkanUtils.vkCheck
import io.github.fopwoc.model.DescriptorTypeCount
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO
import org.lwjgl.vulkan.VK10.vkCreateDescriptorPool
import org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool
import org.lwjgl.vulkan.VK10.vkFreeDescriptorSets
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo
import org.lwjgl.vulkan.VkDescriptorPoolSize
import org.pmw.tinylog.Logger


class DescriptorPool(
    val device: Device,
    descriptorTypeCounts: Array<DescriptorTypeCount>
) {
    val vkDescriptorPool: Long

    init {
        Logger.debug("Creating descriptor pool")
        MemoryStack.stackPush().use { stack ->
            var maxSets = 0
            val numTypes: Int = descriptorTypeCounts.size
            val typeCounts = VkDescriptorPoolSize.calloc(numTypes, stack)
            for (i in 0..<numTypes) {
                maxSets += descriptorTypeCounts[i].count
                typeCounts[i]
                    .type(descriptorTypeCounts[i].descriptorType)
                    .descriptorCount(descriptorTypeCounts[i].count)
            }
            val descriptorPoolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                .flags(VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
                .pPoolSizes(typeCounts)
                .maxSets(maxSets)
            val pDescriptorPool = stack.mallocLong(1)
            vkCheck(
                vkCreateDescriptorPool(device.vkDevice, descriptorPoolInfo, null, pDescriptorPool),
                "Failed to create descriptor pool"
            )
            vkDescriptorPool = pDescriptorPool[0]
        }
    }

    fun freeDescriptorSet(vkDescriptorSet: Long) {
        MemoryStack.stackPush().use { stack ->
            val longBuffer = stack.mallocLong(1)
            longBuffer.put(0, vkDescriptorSet)
            vkCheck(
                vkFreeDescriptorSets(device.vkDevice, vkDescriptorPool, longBuffer),
                "Failed to free descriptor set"
            )
        }
    }

    fun cleanup() {
        Logger.debug("Destroying descriptor pool")
        vkDestroyDescriptorPool(device.vkDevice, vkDescriptorPool, null)
    }
}
