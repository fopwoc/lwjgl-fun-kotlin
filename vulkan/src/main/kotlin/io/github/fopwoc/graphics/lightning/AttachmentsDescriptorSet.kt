package io.github.fopwoc.graphics.lightning

import io.github.fopwoc.graphics.vulkan.Attachment
import io.github.fopwoc.graphics.vulkan.DescriptorPool
import io.github.fopwoc.graphics.vulkan.DescriptorSet
import io.github.fopwoc.graphics.vulkan.Device
import io.github.fopwoc.graphics.vulkan.TextureSampler
import io.github.fopwoc.graphics.vulkan.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
import org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL
import org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET
import org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets
import org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets
import org.lwjgl.vulkan.VkDescriptorImageInfo
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo
import org.lwjgl.vulkan.VkWriteDescriptorSet

class AttachmentsDescriptorSet(
    descriptorPool: DescriptorPool,
    descriptorSetLayout: AttachmentsLayout,
    attachments: Array<Attachment>,
    binding: Int,
) : DescriptorSet {

    val device: Device
    private val binding: Int
    private val textureSampler: TextureSampler
    override var vkDescriptorSet: Long

    init {
        MemoryStack.stackPush().use { stack ->
            device = descriptorPool.device
            this.binding = binding
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
            textureSampler = TextureSampler(device, 1, false)
            update(attachments)
        }
    }

    fun update(attachments: Array<Attachment>) {
        MemoryStack.stackPush().use { stack ->
            val numAttachments = attachments.size
            val descrBuffer = VkWriteDescriptorSet.calloc(numAttachments, stack)

            for (i in 0..<numAttachments) {
                val attachment = attachments[i]
                val imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                    .sampler(textureSampler.vkSampler)
                    .imageView(attachment.imageView.vkImageView)
                if (attachment.depthAttachment) {
                    imageInfo.imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL)
                } else {
                    imageInfo.imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                }
                descrBuffer[i]
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(vkDescriptorSet)
                    .dstBinding(binding + i)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(imageInfo)
            }

            vkUpdateDescriptorSets(device.vkDevice, descrBuffer, null)
        }
    }

    fun cleanup() {
        textureSampler.cleanup()
    }
}
