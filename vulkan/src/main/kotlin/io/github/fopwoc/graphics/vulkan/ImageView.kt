package io.github.fopwoc.graphics.vulkan

import io.github.fopwoc.graphics.vulkan.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO
import org.lwjgl.vulkan.VK10.vkCreateImageView
import org.lwjgl.vulkan.VK10.vkDestroyImageView
import org.lwjgl.vulkan.VkImageViewCreateInfo


class ImageView(
    private val device: Device,
    vkImage: Long,
    imageViewData: ImageViewData
) {
    val vkImageView: Long

    private val aspectMask = imageViewData.aspectMask
    private val mipLevels = imageViewData.mipLevels

    init {
        MemoryStack.stackPush().use { stack ->
            val lp = stack.mallocLong(1)
            val viewCreateInfo = VkImageViewCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                .image(vkImage)
                .viewType(imageViewData.viewType)
                .format(imageViewData.format)
                .subresourceRange {
                    it
                        .aspectMask(aspectMask)
                        .baseMipLevel(0)
                        .levelCount(mipLevels)
                        .baseArrayLayer(imageViewData.baseArrayLayer)
                        .layerCount(imageViewData.layerCount)
                }

            vkCheck(
                vkCreateImageView(device.vkDevice, viewCreateInfo, null, lp),
                "Failed to create image view"
            )
            vkImageView = lp[0]
        }
    }

    fun cleanup() {
        vkDestroyImageView(device.vkDevice, vkImageView, null)
    }


    class ImageViewData {
        var aspectMask = 0
            private set
        var baseArrayLayer = 0
            private set
        var format = 0
            private set
        var layerCount = 1
            private set
        var mipLevels = 1
            private set
        var viewType: Int = VK_IMAGE_VIEW_TYPE_2D
            private set

        fun aspectMask(aspectMask: Int): ImageViewData {
            this.aspectMask = aspectMask
            return this
        }

        fun baseArrayLayer(baseArrayLayer: Int): ImageViewData {
            this.baseArrayLayer = baseArrayLayer
            return this
        }

        fun format(format: Int): ImageViewData {
            this.format = format
            return this
        }

        fun layerCount(layerCount: Int): ImageViewData {
            this.layerCount = layerCount
            return this
        }

        fun mipLevels(mipLevels: Int): ImageViewData {
            this.mipLevels = mipLevels
            return this
        }

        fun viewType(viewType: Int): ImageViewData {
            this.viewType = viewType
            return this
        }
    }
}

