package io.github.fopwoc.graphics.shadows

import io.github.fopwoc.graphics.vulkan.Attachment
import io.github.fopwoc.graphics.vulkan.Device
import io.github.fopwoc.graphics.vulkan.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT
import org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
import org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
import org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
import org.lwjgl.vulkan.VK10.VK_ACCESS_MEMORY_READ_BIT
import org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_CLEAR
import org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE
import org.lwjgl.vulkan.VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE
import org.lwjgl.vulkan.VK10.VK_ATTACHMENT_STORE_OP_STORE
import org.lwjgl.vulkan.VK10.VK_DEPENDENCY_BY_REGION_BIT
import org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL
import org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL
import org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED
import org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS
import org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT
import org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
import org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
import org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO
import org.lwjgl.vulkan.VK10.VK_SUBPASS_EXTERNAL
import org.lwjgl.vulkan.VK10.vkCreateRenderPass
import org.lwjgl.vulkan.VK10.vkDestroyRenderPass
import org.lwjgl.vulkan.VkAttachmentDescription
import org.lwjgl.vulkan.VkAttachmentReference
import org.lwjgl.vulkan.VkRenderPassCreateInfo
import org.lwjgl.vulkan.VkSubpassDependency
import org.lwjgl.vulkan.VkSubpassDescription

class ShadowsRenderPass(private val device: Device, depthAttachment: Attachment) {
    var vkRenderPass: Long

    init {
        MemoryStack.stackPush().use { stack ->
            val attachmentsDesc = VkAttachmentDescription.calloc(1, stack)
            attachmentsDesc[0]
                .format(depthAttachment.image.format)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .samples(MAX_SAMPLES)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL)
            val depthReference = VkAttachmentReference.calloc(stack)
                .attachment(0)
                .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)

            // Render subpass
            val subpass = VkSubpassDescription.calloc(1, stack)
                .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                .pDepthStencilAttachment(depthReference)

            // Subpass dependencies
            val subpassDependencies = VkSubpassDependency.calloc(2, stack)
            subpassDependencies[0]
                .srcSubpass(VK_SUBPASS_EXTERNAL)
                .dstSubpass(0)
                .srcStageMask(VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
                .dstStageMask(
                    VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT or
                        VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                )
                .srcAccessMask(VK_ACCESS_MEMORY_READ_BIT)
                .dstAccessMask(
                    VK_ACCESS_COLOR_ATTACHMENT_READ_BIT or
                        VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                        or VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT or
                        VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
                )
                .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT)
            subpassDependencies[1]
                .srcSubpass(0)
                .dstSubpass(VK_SUBPASS_EXTERNAL)
                .srcStageMask(
                    VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT or
                        VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT
                )
                .dstStageMask(VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
                .srcAccessMask(
                    VK_ACCESS_COLOR_ATTACHMENT_READ_BIT or
                        VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                        or VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT or
                        VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
                )
                .dstAccessMask(VK_ACCESS_MEMORY_READ_BIT)
                .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT)

            // Render pass
            val renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                .pAttachments(attachmentsDesc)
                .pSubpasses(subpass)
                .pDependencies(subpassDependencies)
            val lp = stack.mallocLong(1)
            vkCheck(
                vkCreateRenderPass(device.vkDevice, renderPassInfo, null, lp),
                "Failed to create render pass"
            )
            vkRenderPass = lp[0]
        }
    }

    fun cleanup() {
        vkDestroyRenderPass(device.vkDevice, vkRenderPass, null)
    }

    companion object {
        private const val MAX_SAMPLES = 1
    }
}
