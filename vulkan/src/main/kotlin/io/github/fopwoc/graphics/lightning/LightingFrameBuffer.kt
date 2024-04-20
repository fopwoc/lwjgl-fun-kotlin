package io.github.fopwoc.graphics.lightning

import io.github.fopwoc.graphics.vulkan.FrameBuffer
import io.github.fopwoc.graphics.vulkan.SwapChain
import java.util.Arrays
import java.util.function.Consumer
import org.lwjgl.system.MemoryStack
import org.pmw.tinylog.Logger


class LightingFrameBuffer(swapChain: SwapChain) {
    val lightingRenderPass: LightingRenderPass

    var frameBuffers: Array<FrameBuffer>
        private set

    init {
        Logger.debug("Creating Lighting FrameBuffer")
        lightingRenderPass = LightingRenderPass(swapChain)
        frameBuffers = createFrameBuffers(swapChain, lightingRenderPass)
    }

    fun resize(swapChain: SwapChain) {
        frameBuffers.forEach { it.cleanup() }
        frameBuffers = createFrameBuffers(swapChain, lightingRenderPass)
    }

    fun cleanup() {
        Logger.debug("Destroying Lighting FrameBuffer")
        frameBuffers.forEach { it.cleanup() }
        lightingRenderPass.cleanup()
    }

    companion object {
        private fun createFrameBuffers(
            swapChain: SwapChain,
            lightingRenderPass: LightingRenderPass
        ): Array<FrameBuffer> {
            MemoryStack.stackPush().use { stack ->
                val extent2D = swapChain.swapChainExtent
                val width = extent2D.width()
                val height = extent2D.height()
                val numImages = swapChain.getNumImages()

                val attachmentsBuff = stack.mallocLong(1)

                return Array(numImages) {
                    attachmentsBuff.put(0, swapChain.imageViews[it].vkImageView)
                    FrameBuffer(
                        swapChain.device,
                        width,
                        height,
                        attachmentsBuff,
                        lightingRenderPass.vkRenderPass,1
                    )
                }
            }
        }
    }
}
