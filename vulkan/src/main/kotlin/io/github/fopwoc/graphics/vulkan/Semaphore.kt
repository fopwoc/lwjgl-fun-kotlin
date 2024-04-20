package io.github.fopwoc.graphics.vulkan

import io.github.fopwoc.graphics.vulkan.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO
import org.lwjgl.vulkan.VK10.vkCreateSemaphore
import org.lwjgl.vulkan.VK10.vkDestroySemaphore
import org.lwjgl.vulkan.VkSemaphoreCreateInfo


class Semaphore(val device: Device) {

    val vkSemaphore: Long

    init {
        MemoryStack.stackPush().use { stack ->
            val semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO)

            val lp = stack.mallocLong(1)
            vkCheck(
                vkCreateSemaphore(device.vkDevice, semaphoreCreateInfo, null, lp),
                "Failed to create semaphore"
            )
            vkSemaphore = lp[0]
        }
    }

    fun cleanup() {
        vkDestroySemaphore(device.vkDevice, vkSemaphore, null)
    }
}
