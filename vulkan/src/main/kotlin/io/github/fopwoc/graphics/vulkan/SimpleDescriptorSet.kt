
package io.github.fopwoc.graphics.vulkan

import io.github.fopwoc.graphics.vulkan.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET
import org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets
import org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets
import org.lwjgl.vulkan.VkDescriptorBufferInfo
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo
import org.lwjgl.vulkan.VkWriteDescriptorSet

open class SimpleDescriptorSet(
    descriptorPool: DescriptorPool,
    descriptorSetLayout: DescriptorSetLayout,
    buffer: VulkanBuffer,
    binding: Int,
    type: Int,
    size: Long
) : DescriptorSet {

    override var vkDescriptorSet: Long = 0

    init {
        MemoryStack.stackPush().use { stack ->
            val device = descriptorPool.device
            val pDescriptorSetLayout = stack.mallocLong(1)
            pDescriptorSetLayout.put(0, descriptorSetLayout.vkDescriptorLayout)
            val allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .descriptorPool(descriptorPool.vkDescriptorPool)
                .pSetLayouts(pDescriptorSetLayout)
            val pDescriptorSet = stack.mallocLong(1)
            vkCheck(
                vkAllocateDescriptorSets(device.vkDevice, allocInfo, pDescriptorSet),
                "Failed to create descriptor set"
            )

            vkDescriptorSet = pDescriptorSet[0]

            val bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                .buffer(buffer.buffer)
                .offset(0)
                .range(size)
            val descrBuffer = VkWriteDescriptorSet.calloc(1, stack)
            descrBuffer[0]
                .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .dstSet(vkDescriptorSet)
                .dstBinding(binding)
                .descriptorType(type)
                .descriptorCount(1)
                .pBufferInfo(bufferInfo)
            vkUpdateDescriptorSets(device.vkDevice, descrBuffer, null)
        }
    }
}
