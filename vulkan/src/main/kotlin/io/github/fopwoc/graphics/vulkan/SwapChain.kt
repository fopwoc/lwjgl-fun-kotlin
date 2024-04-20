package io.github.fopwoc.graphics.vulkan

import io.github.fopwoc.core.Window
import io.github.fopwoc.graphics.vulkan.VulkanUtils.vkCheck
import io.github.fopwoc.model.SurfaceFormat
import io.github.fopwoc.model.SyncSemaphores
import java.util.function.Consumer
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.KHRSurface
import org.lwjgl.vulkan.KHRSwapchain
import org.lwjgl.vulkan.VK10.VK_FORMAT_B8G8R8A8_SRGB
import org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT
import org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
import org.lwjgl.vulkan.VK10.VK_SHARING_MODE_CONCURRENT
import org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE
import org.lwjgl.vulkan.VK10.VK_SUCCESS
import org.lwjgl.vulkan.VkExtent2D
import org.lwjgl.vulkan.VkPresentInfoKHR
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR
import org.lwjgl.vulkan.VkSurfaceFormatKHR
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR
import org.pmw.tinylog.Logger

class SwapChain(
    val device: Device,
    surface: Surface,
    window: Window,
    requestedImages: Int,
    vsync: Boolean,
    presentationQueue: Queue.PresentQueue,
    concurrentQueues: Array<Queue>
) {

    val imageViews: Array<ImageView>
    val surfaceFormat: SurfaceFormat
    val swapChainExtent: VkExtent2D
    val syncSemaphoresList: Array<SyncSemaphores>
    val vkSwapChain: Long

    var currentFrame: Int

    init {
        Logger.debug("Creating Vulkan SwapChain")

        MemoryStack.stackPush().use { stack ->
            val physicalDevice = device.physicalDevice

            // Get surface capabilities
            val surfCapabilities = VkSurfaceCapabilitiesKHR.calloc(stack)
            vkCheck(
                KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
                    device.physicalDevice.vkPhysicalDevice,
                    surface.vkSurface,
                    surfCapabilities
                ),
                "Failed to get surface capabilities"
            )

            val numImages: Int = calcNumImages(surfCapabilities, requestedImages)

            surfaceFormat = calcSurfaceFormat(physicalDevice, surface)
            swapChainExtent = calcSwapChainExtent(window, surfCapabilities)

            val vkSwapchainCreateInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                .sType(KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                .surface(surface.vkSurface)
                .minImageCount(numImages)
                .imageFormat(surfaceFormat.imageFormat)
                .imageColorSpace(surfaceFormat.colorSpace)
                .imageExtent(swapChainExtent)
                .imageArrayLayers(1)
                .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
                .preTransform(surfCapabilities.currentTransform())
                .compositeAlpha(KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                .clipped(true)
            if (vsync) {
                vkSwapchainCreateInfo.presentMode(KHRSurface.VK_PRESENT_MODE_FIFO_KHR)
            } else {
                vkSwapchainCreateInfo.presentMode(KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR)
            }

            val numQueues = concurrentQueues.size
            val indices: MutableList<Int> = ArrayList()
            for (i in 0..<numQueues) {
                val queue = concurrentQueues[i]
                if (queue.queueFamilyIndex != presentationQueue.queueFamilyIndex) {
                    indices.add(queue.queueFamilyIndex)
                }
            }
            if (indices.size > 0) {
                val intBuffer = stack.mallocInt(indices.size + 1)
                indices.forEach(Consumer { i: Int? -> intBuffer.put(i!!) })
                intBuffer.put(presentationQueue.queueFamilyIndex).flip()
                vkSwapchainCreateInfo.imageSharingMode(VK_SHARING_MODE_CONCURRENT)
                    .queueFamilyIndexCount(intBuffer.capacity())
                    .pQueueFamilyIndices(intBuffer)
            } else {
                vkSwapchainCreateInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
            }

            val lp = stack.mallocLong(1)
            vkCheck(
                KHRSwapchain.vkCreateSwapchainKHR(device.vkDevice, vkSwapchainCreateInfo, null, lp),
                "Failed to create swap chain"
            )
            vkSwapChain = lp[0]

            imageViews = createImageViews(stack, device, vkSwapChain, surfaceFormat.imageFormat)
            syncSemaphoresList = Array(numImages) { SyncSemaphores(device) }
            currentFrame = 0
        }
    }

    fun acquireNextImage(): Boolean {
        var resize = false
        MemoryStack.stackPush().use { stack ->
            val ip = stack.mallocInt(1)
            val err = KHRSwapchain.vkAcquireNextImageKHR(
                device.vkDevice,
                vkSwapChain,
                0L.inv(),
                syncSemaphoresList[currentFrame].imgAcquisitionSemaphore.vkSemaphore,
                MemoryUtil.NULL,
                ip
            )
            if (err == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) {
                resize = true
            } else if (err == KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                // Not optimal but swapchain can still be used
            } else if (err != VK_SUCCESS) {
                throw java.lang.RuntimeException("Failed to acquire image: $err")
            }
            currentFrame = ip[0]
        }
        return resize
    }

    fun presentImage(queue: Queue): Boolean {
        var resize = false
        MemoryStack.stackPush().use { stack ->
            val present = VkPresentInfoKHR.calloc(stack)
                .sType(KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                .pWaitSemaphores(
                    stack.longs(syncSemaphoresList[currentFrame].renderCompleteSemaphore.vkSemaphore)
                )
                .swapchainCount(1)
                .pSwapchains(stack.longs(vkSwapChain))
                .pImageIndices(stack.ints(currentFrame))
            val err = KHRSwapchain.vkQueuePresentKHR(queue.vkQueue, present)
            if (err == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) {
                resize = true
            } else if (err == KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                // Not optimal but swap chain can still be used
            } else if (err != VK_SUCCESS) {
                throw java.lang.RuntimeException("Failed to present KHR: $err")
            }
        }
        currentFrame = (currentFrame + 1) % imageViews.size
        return resize
    }

    fun getNumImages(): Int {
        return imageViews.size
    }

    fun cleanup() {
        Logger.debug("Destroying Vulkan SwapChain")
        swapChainExtent.free()
        imageViews.forEach { it.cleanup() }
        syncSemaphoresList.forEach { it.cleanup() }
        KHRSwapchain.vkDestroySwapchainKHR(device.vkDevice, vkSwapChain, null)
    }

    companion object {
        private fun calcNumImages(
            surfCapabilities: VkSurfaceCapabilitiesKHR,
            requestedImages: Int
        ): Int {
            val maxImages = surfCapabilities.maxImageCount()
            val minImages = surfCapabilities.minImageCount()
            var result = minImages
            if (maxImages != 0) {
                result = Math.min(requestedImages, maxImages)
            }
            result = Math.max(result, minImages)
            Logger.debug(
                "Requested [{}] images, got [{}] images. Surface capabilities, maxImages: [{}], minImages [{}]",
                requestedImages,
                result,
                maxImages,
                minImages
            )
            return result
        }

        private fun calcSurfaceFormat(
            physicalDevice: PhysicalDevice,
            surface: Surface
        ): SurfaceFormat {
            var imageFormat: Int
            var colorSpace: Int
            MemoryStack.stackPush().use { stack ->
                val ip = stack.mallocInt(1)
                vkCheck(
                    KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(
                        physicalDevice.vkPhysicalDevice,
                        surface.vkSurface,
                        ip,
                        null
                    ),
                    "Failed to get the number surface formats"
                )
                val numFormats = ip[0]
                if (numFormats <= 0) {
                    throw RuntimeException("No surface formats retrieved")
                }
                val surfaceFormats = VkSurfaceFormatKHR.calloc(numFormats, stack)
                vkCheck(
                    KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(
                        physicalDevice.vkPhysicalDevice,
                        surface.vkSurface,
                        ip,
                        surfaceFormats
                    ),
                    "Failed to get surface formats"
                )
                imageFormat = VK_FORMAT_B8G8R8A8_SRGB
                colorSpace = surfaceFormats[0].colorSpace()
                for (i in 0..<numFormats) {
                    val surfaceFormatKHR = surfaceFormats[i]
                    if (surfaceFormatKHR.format() == VK_FORMAT_B8G8R8A8_SRGB &&
                        surfaceFormatKHR.colorSpace() == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR
                    ) {
                        imageFormat = surfaceFormatKHR.format()
                        colorSpace = surfaceFormatKHR.colorSpace()
                        break
                    }
                }
            }
            return SurfaceFormat(imageFormat, colorSpace)
        }

        private fun calcSwapChainExtent(
            window: Window,
            surfCapabilities: VkSurfaceCapabilitiesKHR
        ): VkExtent2D {
            val result = VkExtent2D.calloc()
            if (surfCapabilities.currentExtent().width() == -0x1) {
                // Surface size undefined. Set to the window size if within bounds
                var width = Math.min(window.width, surfCapabilities.maxImageExtent().width())
                width = Math.max(width, surfCapabilities.minImageExtent().width())

                var height = Math.min(window.height, surfCapabilities.maxImageExtent().height())
                height = Math.max(height, surfCapabilities.minImageExtent().height())

                result.width(width)
                result.height(height)
            } else {
                // Surface already defined, just use that for the swap chain
                result.set(surfCapabilities.currentExtent())
            }
            return result
        }

        private fun createImageViews(
            stack: MemoryStack,
            device: Device,
            swapChain: Long,
            format: Int
        ): Array<ImageView> {
            val result: Array<ImageView?>
            val ip = stack.mallocInt(1)
            vkCheck(
                KHRSwapchain.vkGetSwapchainImagesKHR(device.vkDevice, swapChain, ip, null),
                "Failed to get number of surface images"
            )
            val numImages = ip[0]
            val swapChainImages = stack.mallocLong(numImages)
            vkCheck(
                KHRSwapchain.vkGetSwapchainImagesKHR(device.vkDevice, swapChain, ip, swapChainImages),
                "Failed to get surface images"
            )

            val imageViewData: ImageView.ImageViewData =
                ImageView.ImageViewData().format(format).aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
            return Array(numImages) { ImageView(device, swapChainImages[it], imageViewData) }
        }
    }
}
