package io.github.fopwoc.graphics.vulkan

import io.github.fopwoc.graphics.vulkan.VulkanUtils.memoryTypeFromProperties
import io.github.fopwoc.graphics.vulkan.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_SRGB
import org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED
import org.lwjgl.vulkan.VK10.VK_IMAGE_TILING_OPTIMAL
import org.lwjgl.vulkan.VK10.VK_IMAGE_TYPE_2D
import org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO
import org.lwjgl.vulkan.VK10.vkAllocateMemory
import org.lwjgl.vulkan.VK10.vkBindImageMemory
import org.lwjgl.vulkan.VK10.vkCreateImage
import org.lwjgl.vulkan.VK10.vkDestroyImage
import org.lwjgl.vulkan.VK10.vkFreeMemory
import org.lwjgl.vulkan.VK10.vkGetImageMemoryRequirements
import org.lwjgl.vulkan.VkImageCreateInfo
import org.lwjgl.vulkan.VkMemoryAllocateInfo
import org.lwjgl.vulkan.VkMemoryRequirements

class Image(val device: Device, imageData: ImageData) {

    val format: Int
    val mipLevels: Int
    val vkImage: Long
    val vkMemory: Long

    init {
        MemoryStack.stackPush().use { stack ->
            this.format = imageData.format
            this.mipLevels = imageData.mipLevels

            val imageCreateInfo = VkImageCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                .imageType(VK_IMAGE_TYPE_2D)
                .format(format)
                .extent {
                    it.width(imageData.width)
                        .height(imageData.height)
                        .depth(1)
                }
                .mipLevels(mipLevels)
                .arrayLayers(imageData.arrayLayers)
                .samples(imageData.sampleCount)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                .tiling(VK_IMAGE_TILING_OPTIMAL)
                .usage(imageData.usage)

            val lp = stack.mallocLong(1)
            vkCheck(
                vkCreateImage(device.vkDevice, imageCreateInfo, null, lp),
                "Failed to create image"
            )
            vkImage = lp[0]

            // Get memory requirements for this object
            val memReqs = VkMemoryRequirements.calloc(stack)
            vkGetImageMemoryRequirements(device.vkDevice, vkImage, memReqs)

            val memAlloc = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memReqs.size())
                .memoryTypeIndex(
                    memoryTypeFromProperties(
                        device.physicalDevice,
                        memReqs.memoryTypeBits(),
                        0
                    )
                )

            // Allocate memory
            vkCheck(
                vkAllocateMemory(device.vkDevice, memAlloc, null, lp),
                "Failed to allocate memory"
            )
            vkMemory = lp.get(0)

            // Bind memory
            vkCheck(
                vkBindImageMemory(device.vkDevice, vkImage, vkMemory, 0),
                "Failed to bind image memory"
            )
        }
    }

    fun cleanup() {
        vkDestroyImage(device.vkDevice, vkImage, null)
        vkFreeMemory(device.vkDevice, vkMemory, null)
    }

    data class ImageData(
        val format: Int = VK_FORMAT_R8G8B8A8_SRGB,
        val arrayLayers: Int = 1,
        val height: Int = 0,
        val width: Int = 0,
        val mipLevels: Int = 1,
        val sampleCount: Int = 1,
        val usage: Int = 0,
    )
}
