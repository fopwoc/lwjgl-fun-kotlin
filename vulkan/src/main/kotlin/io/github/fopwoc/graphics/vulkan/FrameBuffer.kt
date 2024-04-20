package io.github.fopwoc.graphics.vulkan

import io.github.fopwoc.graphics.vulkan.VulkanUtils.vkCheck
import java.nio.LongBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO
import org.lwjgl.vulkan.VK10.vkCreateFramebuffer
import org.lwjgl.vulkan.VK10.vkDestroyFramebuffer
import org.lwjgl.vulkan.VkFramebufferCreateInfo


class FrameBuffer(
    private val device: Device,
    width: Int,
    height: Int,
    pAttachments: LongBuffer,
    renderPass: Long,
    layers: Int,
) {
    val vkFrameBuffer: Long

    init {
        MemoryStack.stackPush().use { stack ->
            val fci = VkFramebufferCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                .pAttachments(pAttachments)
                .width(width)
                .height(height)
                .layers(layers)
                .renderPass(renderPass)

            val lp = stack.mallocLong(1)
            vkCheck(
                vkCreateFramebuffer(device.vkDevice, fci, null, lp),
                "Failed to create FrameBuffer"
            )
            vkFrameBuffer = lp[0]
        }
    }

    fun cleanup() {
        vkDestroyFramebuffer(device.vkDevice, vkFrameBuffer, null)
    }
}
