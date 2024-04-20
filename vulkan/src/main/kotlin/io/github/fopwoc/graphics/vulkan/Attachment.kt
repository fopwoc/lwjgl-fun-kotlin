package io.github.fopwoc.graphics.vulkan

import io.github.fopwoc.graphics.vulkan.ImageView.ImageViewData
import org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT
import org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT
import org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
import org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT
import org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_SAMPLED_BIT


fun Attachment(
    device: Device,
    width: Int,
    height: Int,
    format: Int,
    usage: Int,
): Attachment {

    val image = Image(
        device,
        Image.ImageData(
            width = width,
            height = height,
            usage = (usage or VK_IMAGE_USAGE_SAMPLED_BIT),
            format = format
        )
    )

    var depthAttachment = false
    var aspectMask = 0
    if ((usage and VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) > 0) {
        aspectMask = VK_IMAGE_ASPECT_COLOR_BIT
        depthAttachment = false
    }
    if ((usage and VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT) > 0) {
        aspectMask = VK_IMAGE_ASPECT_DEPTH_BIT
        depthAttachment = true
    }

    val imageViewData = ImageViewData().format(image.format).aspectMask(aspectMask)
    val imageView = ImageView(device, image.vkImage, imageViewData)

    return Attachment(image, imageView, depthAttachment)
}

class Attachment(
    val image: Image,
    val imageView: ImageView,
    val depthAttachment: Boolean,
) {



    fun cleanup() {
        imageView.cleanup()
        image.cleanup()
    }

    companion object {
        fun calcAspectMask(usage: Int): Int {
            var aspectMask = 0
            if (usage and VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT > 0) {
                aspectMask = VK_IMAGE_ASPECT_COLOR_BIT
            }
            if (usage and VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT > 0) {
                aspectMask = VK_IMAGE_ASPECT_DEPTH_BIT
            }
            return aspectMask
        }
    }
}
