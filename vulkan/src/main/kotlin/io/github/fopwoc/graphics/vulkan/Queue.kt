package io.github.fopwoc.graphics.vulkan

import io.github.fopwoc.graphics.vulkan.VulkanUtils.vkCheck
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.KHRSurface
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VK10.VK_NULL_HANDLE
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO
import org.lwjgl.vulkan.VK10.VK_TRUE
import org.lwjgl.vulkan.VK10.vkGetDeviceQueue
import org.lwjgl.vulkan.VK10.vkQueueSubmit
import org.lwjgl.vulkan.VK10.vkQueueWaitIdle
import org.lwjgl.vulkan.VkQueue
import org.lwjgl.vulkan.VkSubmitInfo
import org.pmw.tinylog.Logger
import java.nio.IntBuffer
import java.nio.LongBuffer

open class Queue(
    val device: Device,
    val queueFamilyIndex: Int,
    private val queueIndex: Int
) {
    val vkQueue: VkQueue

    init {
        Logger.debug("Creating queue")

        MemoryStack.stackPush().use { stack ->
            val pQueue = stack.mallocPointer(1)
            vkGetDeviceQueue(device.vkDevice, queueFamilyIndex, queueIndex, pQueue)
            val queue = pQueue[0]
            vkQueue = VkQueue(queue, device.vkDevice)
        }
    }

    open fun submit(
        commandBuffers: PointerBuffer?,
        waitSemaphores: LongBuffer?,
        dstStageMasks: IntBuffer?,
        signalSemaphores: LongBuffer?,
        fence: Fence?
    ) {
        MemoryStack.stackPush().use { stack ->
            val submitInfo = VkSubmitInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pCommandBuffers(commandBuffers)
                .pSignalSemaphores(signalSemaphores)
            if (waitSemaphores != null) {
                submitInfo.waitSemaphoreCount(waitSemaphores.capacity())
                    .pWaitSemaphores(waitSemaphores)
                    .pWaitDstStageMask(dstStageMasks)
            } else {
                submitInfo.waitSemaphoreCount(0)
            }
            val fenceHandle = fence?.vkFence ?: VK_NULL_HANDLE
            vkCheck(
                vkQueueSubmit(vkQueue, submitInfo, fenceHandle),
                "Failed to submit command to queue"
            )
        }
    }

    fun waitIdle() {
        vkQueueWaitIdle(vkQueue)
    }

    class GraphicsQueue(
        device: Device,
        queueIndex: Int
    ) : Queue(
        device,
        getGraphicsQueueFamilyIndex(device),
        queueIndex
    ) {
        companion object {
            private fun getGraphicsQueueFamilyIndex(device: Device): Int {
                var index = -1
                val physicalDevice: PhysicalDevice = device.physicalDevice
                val queuePropsBuff = physicalDevice.vkQueueFamilyProps
                val numQueuesFamilies = queuePropsBuff.capacity()
                for (i in 0..<numQueuesFamilies) {
                    val props = queuePropsBuff[i]
                    val graphicsQueue = props.queueFlags() and VK10.VK_QUEUE_GRAPHICS_BIT != 0
                    if (graphicsQueue) {
                        index = i
                        break
                    }
                }
                if (index < 0) {
                    throw RuntimeException("Failed to get graphics Queue family index")
                }
                return index
            }
        }
    }

    class PresentQueue(
        device: Device,
        surface: Surface,
        queueIndex: Int
    ) : Queue(
        device,
        getPresentQueueFamilyIndex(device, surface),
        queueIndex
    ) {
        companion object {
            private fun getPresentQueueFamilyIndex(device: Device, surface: Surface): Int {
                var index = -1
                MemoryStack.stackPush().use { stack ->
                    val physicalDevice = device.physicalDevice
                    val queuePropsBuff = physicalDevice.vkQueueFamilyProps
                    val numQueuesFamilies = queuePropsBuff.capacity()
                    val intBuff = stack.mallocInt(1)
                    for (i in 0..<numQueuesFamilies) {
                        KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(
                            physicalDevice.vkPhysicalDevice,
                            i,
                            surface.vkSurface,
                            intBuff
                        )
                        val supportsPresentation = intBuff[0] == VK_TRUE
                        if (supportsPresentation) {
                            index = i
                            break
                        }
                    }
                }
                if (index < 0) {
                    throw java.lang.RuntimeException("Failed to get Presentation Queue family index")
                }
                return index
            }
        }
    }
}
