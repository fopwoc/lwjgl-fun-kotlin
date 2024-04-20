package io.github.fopwoc.graphics.geometry

import io.github.fopwoc.graphics.vulkan.FrameBuffer
import io.github.fopwoc.graphics.vulkan.SwapChain
import org.lwjgl.system.MemoryStack
import org.pmw.tinylog.Logger

class GeometryFrameBuffer(swapChain: SwapChain) {
    val geometryRenderPass: GeometryRenderPass

    var frameBuffer: FrameBuffer
        private set
    var geometryAttachments: GeometryAttachments
        private set

    init {
        Logger.debug("Creating GeometryFrameBuffer")
        geometryAttachments = createAttachments(swapChain)
        geometryRenderPass = GeometryRenderPass(swapChain.device, geometryAttachments.attachments)
        frameBuffer = createFrameBuffer(swapChain, geometryAttachments, geometryRenderPass)
    }

    fun resize(swapChain: SwapChain) {
        frameBuffer.cleanup()
        geometryAttachments.cleanup()
        geometryAttachments = createAttachments(swapChain)
        frameBuffer = createFrameBuffer(swapChain, geometryAttachments, geometryRenderPass)
    }

    fun cleanup() {
        Logger.debug("Destroying Geometry FrameBuffer")
        geometryRenderPass.cleanup()
        geometryAttachments.cleanup()
        frameBuffer.cleanup()
    }

    companion object {
        private fun createAttachments(swapChain: SwapChain): GeometryAttachments {
            val extent2D = swapChain.swapChainExtent
            val width = extent2D.width()
            val height = extent2D.height()

            return GeometryAttachments(swapChain.device, width, height)
        }

        private fun createFrameBuffer(
            swapChain: SwapChain,
            geometryAttachments: GeometryAttachments,
            geometryRenderPass: GeometryRenderPass
        ): FrameBuffer {
            return MemoryStack.stackPush().use { stack ->
                val attachments = geometryAttachments.attachments
                val attachmentsBuffer = stack.mallocLong(attachments.size)

                for (attachment in attachments) {
                    attachmentsBuffer.put(attachment.imageView.vkImageView)
                }
                attachmentsBuffer.flip()

                FrameBuffer(
                    swapChain.device,
                    geometryAttachments.width,
                    geometryAttachments.height,
                    attachmentsBuffer,
                    geometryRenderPass.vkRenderPass,
                    1
                )
            }
        }
    }
}
