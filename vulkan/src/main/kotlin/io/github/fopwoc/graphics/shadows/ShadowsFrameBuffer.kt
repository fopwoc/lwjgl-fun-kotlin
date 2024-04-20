package io.github.fopwoc.graphics.shadows

import io.github.fopwoc.core.EngineProperties
import io.github.fopwoc.graphics.vulkan.Attachment
import io.github.fopwoc.graphics.vulkan.Device
import io.github.fopwoc.graphics.vulkan.FrameBuffer
import io.github.fopwoc.graphics.vulkan.GraphConstants
import io.github.fopwoc.graphics.vulkan.Image
import io.github.fopwoc.graphics.vulkan.ImageView
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT
import org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT
import org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_SAMPLED_BIT
import org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D_ARRAY
import org.pmw.tinylog.Logger


class ShadowsFrameBuffer( device: Device) {
val depthAttachment: Attachment
val shadowsRenderPass: ShadowsRenderPass
val frameBuffer: FrameBuffer

    init {
        Logger.debug("Creating ShadowsFrameBuffer")
        MemoryStack.stackPush().use { stack ->
            val usage = VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT or VK_IMAGE_USAGE_SAMPLED_BIT
            val engineProperties = EngineProperties.getInstance()
            val shadowMapSize: Int = engineProperties.shadowMapSize
            val imageData: Image.ImageData =
                Image.ImageData(
                    VK_FORMAT_D32_SFLOAT,
                    GraphConstants.SHADOW_MAP_CASCADE_COUNT,
                    shadowMapSize,
                    shadowMapSize,
                    usage = usage or VK_IMAGE_USAGE_SAMPLED_BIT
                )

            val depthImage = Image(device, imageData)
            val aspectMask: Int = Attachment.calcAspectMask(usage)
            val imageViewData: ImageView.ImageViewData = ImageView.ImageViewData().format(depthImage.format).aspectMask(aspectMask)
                .viewType(VK_IMAGE_VIEW_TYPE_2D_ARRAY)
                .layerCount(GraphConstants.SHADOW_MAP_CASCADE_COUNT)
            val depthImageView = ImageView(device, depthImage.vkImage, imageViewData)
            depthAttachment = Attachment(depthImage, depthImageView, true)
            shadowsRenderPass = ShadowsRenderPass(device, depthAttachment)
            val attachmentsBuff = stack.mallocLong(1)
            attachmentsBuff.put(0, depthAttachment.imageView.vkImageView)
            frameBuffer = FrameBuffer(
                device,
                shadowMapSize,
                shadowMapSize,
                attachmentsBuff,
                shadowsRenderPass.vkRenderPass,
                GraphConstants.SHADOW_MAP_CASCADE_COUNT
            )
        }
    }

    fun cleanup() {
        Logger.debug("Destroying ShadowsFrameBuffer")
        shadowsRenderPass.cleanup()
        depthAttachment.cleanup()
        frameBuffer.cleanup()
    }
}
