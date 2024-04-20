package io.github.fopwoc.graphics.vulkan

import io.github.fopwoc.graphics.vulkan.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO
import org.lwjgl.vulkan.VK10.vkCreateCommandPool
import org.lwjgl.vulkan.VK10.vkDestroyCommandPool
import org.lwjgl.vulkan.VkCommandPoolCreateInfo
import org.pmw.tinylog.Logger

class CommandPool(
    val device: Device,
    queueFamilyIndex: Int
) {
    val vkCommandPool: Long

    init {
        Logger.debug("Creating Vulkan CommandPool")

        MemoryStack.stackPush().use { stack ->
            val cmdPoolInfo = VkCommandPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                .queueFamilyIndex(queueFamilyIndex)

            val lp = stack.mallocLong(1)
            vkCheck(
                vkCreateCommandPool(device.vkDevice, cmdPoolInfo, null, lp),
                "Failed to create command pool"
            )

            vkCommandPool = lp[0]
        }
    }

    fun cleanup() {
        vkDestroyCommandPool(device.vkDevice, vkCommandPool, null)
    }
}
