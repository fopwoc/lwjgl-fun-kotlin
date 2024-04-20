package io.github.fopwoc.graphics.vulkan

import io.github.fopwoc.graphics.vulkan.VulkanUtils.vkCheck
import io.github.fopwoc.model.VertexInputStateInfo
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO
import org.lwjgl.vulkan.VK10.vkCreatePipelineCache
import org.lwjgl.vulkan.VK10.vkDestroyPipelineCache
import org.lwjgl.vulkan.VkPipelineCacheCreateInfo
import org.pmw.tinylog.Logger


class PipelineCache(val device: Device) {

    val vkPipelineCache: Long

    init {
        Logger.debug("Creating pipeline cache")
        MemoryStack.stackPush().use { stack ->
            val createInfo = VkPipelineCacheCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO)

            val lp = stack.mallocLong(1)
            vkCheck(
                vkCreatePipelineCache(device.vkDevice, createInfo, null, lp),
                "Error creating pipeline cache"
            )
            vkPipelineCache = lp[0]
        }
    }

    fun cleanup() {
        Logger.debug("Destroying pipeline cache")
        vkDestroyPipelineCache(device.vkDevice, vkPipelineCache, null)
    }
}


