package io.github.fopwoc.graphics.vulkan

import io.github.fopwoc.graphics.vulkan.Image.ImageData
import io.github.fopwoc.graphics.vulkan.ImageView.ImageViewData
import java.nio.ByteBuffer
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.min
import org.lwjgl.stb.STBImage.stbi_failure_reason
import org.lwjgl.stb.STBImage.stbi_image_free
import org.lwjgl.stb.STBImage.stbi_load
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT
import org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_READ_BIT
import org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT
import org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT
import org.lwjgl.vulkan.VK10.VK_FILTER_LINEAR
import org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT
import org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
import org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL
import org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL
import org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED
import org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_SAMPLED_BIT
import org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT
import org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT
import org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
import org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
import org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
import org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
import org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT
import org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER
import org.lwjgl.vulkan.VK10.vkCmdBlitImage
import org.lwjgl.vulkan.VK10.vkCmdCopyBufferToImage
import org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier
import org.lwjgl.vulkan.VkBufferImageCopy
import org.lwjgl.vulkan.VkImageBlit
import org.lwjgl.vulkan.VkImageMemoryBarrier
import org.lwjgl.vulkan.VkImageSubresourceRange
import org.lwjgl.vulkan.VkOffset3D
import org.pmw.tinylog.Logger


class Texture(device: Device, val fileName: String, imageFormat: Int) {

    private var stgBuffer: VulkanBuffer? = null

    val image: Image
    val imageView: ImageView
    var recordedTransition: Boolean
    val width: Int
    val height: Int
    val mipLevels: Int

    var hasTransparencies: Boolean = false
        private set

    init {
        Logger.debug("Creating texture [{}]", fileName)
        recordedTransition = false

        val buffer: ByteBuffer?
        MemoryStack.stackPush().use { stack ->
            val w = stack.mallocInt(1)
            val h = stack.mallocInt(1)
            val channels = stack.mallocInt(1)

            buffer = stbi_load(fileName, w, h, channels, 4)
            if (buffer == null) {
                throw RuntimeException("Image file [$fileName" + "] not loaded: " + stbi_failure_reason())
            }

            val numPixels = buffer.capacity() / 4
            var offset = 0

            repeat(numPixels) {
                val a = 0xFF and buffer[offset + 3].toInt()
                if (a < 255) {
                    hasTransparencies = true
                    return@repeat
                }
                offset += 4
            }

            width = w.get()
            height = h.get()
            mipLevels = floor(log2(min(width, height).toDouble())).toInt() + 1

            createStgBuffer(device, buffer)

            val imageData = ImageData(
                width = width,
                height = height,
                usage = VK_IMAGE_USAGE_TRANSFER_SRC_BIT or
                    VK_IMAGE_USAGE_TRANSFER_DST_BIT or
                    VK_IMAGE_USAGE_SAMPLED_BIT,
                format = imageFormat,
                mipLevels = mipLevels
            )

            image = Image(device, imageData)

            val imageViewData =
                ImageViewData().format(image.format).aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevels(mipLevels)

            imageView = ImageView(device, image.vkImage, imageViewData)
        }

        stbi_image_free(buffer!!)
    }

    private fun createStgBuffer(device: Device, data: ByteBuffer) {
        val size = data.remaining()
        stgBuffer = VulkanBuffer(
            device, size.toLong(), VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        )

        // TODO: UNSAFE
        val mappedMemory: Long = stgBuffer!!.map()
        val buffer = MemoryUtil.memByteBuffer(mappedMemory, stgBuffer!!.requestedSize.toInt())
        buffer.put(data)
        data.flip()
        stgBuffer!!.unMap()
    }

    fun recordTextureTransition(cmd: CommandBuffer) {
        if (stgBuffer != null && !recordedTransition) {
            Logger.debug("Recording transition for texture [{}]", fileName)
            recordedTransition = true
            MemoryStack.stackPush().use { stack ->
                recordImageTransition(stack, cmd, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                recordCopyBuffer(stack, cmd, stgBuffer!!)
                recordGenerateMipMaps(stack, cmd)
            }
        } else {
            Logger.debug("Texture [{}] has already been transitioned", fileName)
        }
    }

    fun recordGenerateMipMaps(stack: MemoryStack, cmd: CommandBuffer) {
        val subResourceRange = VkImageSubresourceRange.calloc(stack)
            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
            .baseArrayLayer(0)
            .levelCount(1)
            .layerCount(1)

        val barrier = VkImageMemoryBarrier.calloc(1, stack)
            .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
            .image(image.vkImage)
            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .subresourceRange(subResourceRange)

        var mipWidth: Int = width
        var mipHeight: Int = height

        for (i in 1..<mipLevels) {
            subResourceRange.baseMipLevel(i - 1)
            barrier.subresourceRange(subResourceRange)
                .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)

            vkCmdPipelineBarrier(
                cmd.vkCommandBuffer,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                0,
                null,
                null,
                barrier
            )

            val srcOffset0 = VkOffset3D.calloc(stack).x(0).y(0).z(0)
            val srcOffset1 = VkOffset3D.calloc(stack).x(mipWidth).y(mipHeight).z(1)
            val dstOffset0 = VkOffset3D.calloc(stack).x(0).y(0).z(0)
            val dstOffset1 = VkOffset3D.calloc(stack)
                .x(if (mipWidth > 1) mipWidth / 2 else 1).y(if (mipHeight > 1) mipHeight / 2 else 1).z(1)

            val blit = VkImageBlit.calloc(1, stack)
                .srcOffsets(0, srcOffset0)
                .srcOffsets(1, srcOffset1)
                .srcSubresource {
                    it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .mipLevel(i - 1)
                        .baseArrayLayer(0)
                        .layerCount(1)
                }
                .dstOffsets(0, dstOffset0)
                .dstOffsets(1, dstOffset1)
                .dstSubresource {
                    it
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .mipLevel(i)
                        .baseArrayLayer(0)
                        .layerCount(1)
                }

            vkCmdBlitImage(
                cmd.vkCommandBuffer,
                image.vkImage,
                VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                image.vkImage,
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                blit,
                VK_FILTER_LINEAR
            )

            barrier.oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)

            vkCmdPipelineBarrier(
                cmd.vkCommandBuffer,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                0,
                null,
                null,
                barrier
            )

            if (mipWidth > 1) mipWidth /= 2
            if (mipHeight > 1) mipHeight /= 2
        }

        barrier.subresourceRange {
            it.baseMipLevel(mipLevels - 1)
        }.oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
            .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
            .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)

        vkCmdPipelineBarrier(
            cmd.vkCommandBuffer,
            VK_PIPELINE_STAGE_TRANSFER_BIT,
            VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
            0,
            null,
            null,
            barrier
        )
    }

    private fun recordImageTransition(stack: MemoryStack, cmd: CommandBuffer, oldLayout: Int, newLayout: Int) {
        val barrier = VkImageMemoryBarrier.calloc(1, stack)
            .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
            .oldLayout(oldLayout)
            .newLayout(newLayout)
            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .image(image.vkImage)
            .subresourceRange {
                it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(mipLevels)
                    .baseArrayLayer(0)
                    .layerCount(1)
            }

        val srcStage: Int
        val srcAccessMask: Int
        val dstAccessMask: Int
        val dstStage: Int

        if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
            srcStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
            srcAccessMask = 0
            dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT
            dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT
        } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL &&
            newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
        ) {
            srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT
            srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT
            dstStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
            dstAccessMask = VK_ACCESS_SHADER_READ_BIT
        } else {
            throw java.lang.RuntimeException("Unsupported layout transition")
        }

        barrier.srcAccessMask(srcAccessMask)
        barrier.dstAccessMask(dstAccessMask)

        vkCmdPipelineBarrier(
            cmd.vkCommandBuffer,
            srcStage,
            dstStage,
            0,
            null,
            null,
            barrier
        )
    }

    private fun recordCopyBuffer(stack: MemoryStack, cmd: CommandBuffer, bufferData: VulkanBuffer) {
        val region = VkBufferImageCopy.calloc(1, stack)
            .bufferOffset(0)
            .bufferRowLength(0)
            .bufferImageHeight(0)
            .imageSubresource {
                it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1)
            }
            .imageOffset { it.x(0).y(0).z(0) }
            .imageExtent { it.width(width).height(height).depth(1) }
        vkCmdCopyBufferToImage(
            cmd.vkCommandBuffer,
            bufferData.buffer,
            image.vkImage,
            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            region
        )
    }

    private fun setHasTransparencies(buf: ByteBuffer) {
    }

    fun cleanup() {
        cleanupStgBuffer()
        imageView.cleanup()
        image.cleanup()
    }

    fun cleanupStgBuffer() {
        if (stgBuffer != null) {
            stgBuffer!!.cleanup()
            stgBuffer = null
        }
    }
}
