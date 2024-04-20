package io.github.fopwoc.graphics.geometry

import io.github.fopwoc.graphics.vulkan.Attachment
import io.github.fopwoc.graphics.vulkan.Device
import org.lwjgl.vulkan.VK10.VK_FORMAT_A2B10G10R10_UNORM_PACK32
import org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT
import org.lwjgl.vulkan.VK10.VK_FORMAT_R16G16B16A16_SFLOAT
import org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
import org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT

class GeometryAttachments(
    device: Device,
    val width: Int,
    val height: Int
) {

    var attachments: Array<Attachment>
    var deptAttachment: Attachment

    init {
        // Albedo attachment
        val albedoAttachment = Attachment(
            device,
            width,
            height,
            VK_FORMAT_R16G16B16A16_SFLOAT,
            VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
        )

        // Normals attachment
        val normalsAttachment = Attachment(
            device,
            width,
            height,
            VK_FORMAT_A2B10G10R10_UNORM_PACK32,
            VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
        )

        // PBR attachment
        val pbrAttachment = Attachment(
            device,
            width,
            height,
            VK_FORMAT_R16G16B16A16_SFLOAT,
            VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
        )

        // Depth attachment
        deptAttachment = Attachment(
            device, width, height,
            VK_FORMAT_D32_SFLOAT, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT
        )

        attachments = arrayOf(
            albedoAttachment,
            normalsAttachment,
            pbrAttachment,
            deptAttachment
        )
    }

    fun cleanup() {
        attachments.forEach { it.cleanup() }
    }

    companion object {
        const val NUMBER_ATTACHMENTS = 4
        const val NUMBER_COLOR_ATTACHMENTS = NUMBER_ATTACHMENTS - 1
    }
}
