package io.github.fopwoc.graphics.vulkan

import io.github.fopwoc.graphics.vulkan.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.VK_FENCE_CREATE_SIGNALED_BIT
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO
import org.lwjgl.vulkan.VK10.vkCreateFence
import org.lwjgl.vulkan.VK10.vkDestroyFence
import org.lwjgl.vulkan.VK10.vkResetFences
import org.lwjgl.vulkan.VK10.vkWaitForFences
import org.lwjgl.vulkan.VkFenceCreateInfo

class Fence(val device: Device, signaled: Boolean) {
    val vkFence: Long

    init {
        MemoryStack.stackPush().use { stack ->
            val fenceCreateInfo = VkFenceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
                .flags(if (signaled) VK_FENCE_CREATE_SIGNALED_BIT else 0)

            val lp = stack.mallocLong(1)
            vkCheck(
                vkCreateFence(device.vkDevice, fenceCreateInfo, null, lp),
                "Failed to create semaphore"
            )
            vkFence = lp[0]
        }
    }

    fun fenceWait() {
        vkWaitForFences(device.vkDevice, vkFence, true, Long.MAX_VALUE)
    }

    fun reset() {
        vkResetFences(device.vkDevice, vkFence)
    }

    fun cleanup() {
        vkDestroyFence(device.vkDevice, vkFence, null)
    }
}
