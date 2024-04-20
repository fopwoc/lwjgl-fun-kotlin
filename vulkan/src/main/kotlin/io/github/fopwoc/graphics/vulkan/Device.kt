package io.github.fopwoc.graphics.vulkan

import io.github.fopwoc.graphics.vulkan.VulkanUtils.getOS
import io.github.fopwoc.graphics.vulkan.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.KHRPortabilitySubset.VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME
import org.lwjgl.vulkan.KHRSwapchain
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO
import org.lwjgl.vulkan.VK10.vkCreateDevice
import org.lwjgl.vulkan.VK10.vkDestroyDevice
import org.lwjgl.vulkan.VK10.vkDeviceWaitIdle
import org.lwjgl.vulkan.VK10.vkEnumerateDeviceExtensionProperties
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkDeviceCreateInfo
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo
import org.lwjgl.vulkan.VkExtensionProperties
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures
import org.pmw.tinylog.Logger


class Device(
    instance: VulkanInstance,
    val physicalDevice: PhysicalDevice,
) {
    var vkDevice: VkDevice
    val samplerAnisotropy: Boolean
    val memoryAllocator: MemoryAllocator

    init {
        Logger.debug("Creating device")
        MemoryStack.stackPush().use { stack ->
            val deviceExtensions: Set<String> = getDeviceExtensions(physicalDevice)
            val usePortability = deviceExtensions.contains(VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME) &&
                getOS() == VulkanUtils.OSType.MACOS
            val numExtensions = if (usePortability) 2 else 1
            val requiredExtensions = stack.mallocPointer(numExtensions)
            requiredExtensions.put(stack.ASCII(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME))
            if (usePortability) {
                requiredExtensions.put(stack.ASCII(VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME))
            }
            requiredExtensions.flip()

            // Set up required features
            val features = VkPhysicalDeviceFeatures.calloc(stack)
            val supportedFeatures = physicalDevice.vkPhysicalDeviceFeatures
            samplerAnisotropy = supportedFeatures.samplerAnisotropy()
            if (samplerAnisotropy) {
                features.samplerAnisotropy(true)
            }
            features.depthClamp(supportedFeatures.depthClamp())
            //features.geometryShader(true)

            // Enable all the queue families
            val queuePropsBuff = physicalDevice.vkQueueFamilyProps
            val numQueuesFamilies = queuePropsBuff.capacity()
            val queueCreationInfoBuf = VkDeviceQueueCreateInfo.calloc(numQueuesFamilies, stack)
            for (i in 0..<numQueuesFamilies) {
                val priorities = stack.callocFloat(queuePropsBuff[i].queueCount())
                queueCreationInfoBuf[i]
                    .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                    .queueFamilyIndex(i)
                    .pQueuePriorities(priorities)
            }

            val deviceCreateInfo = VkDeviceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                .ppEnabledExtensionNames(requiredExtensions)
                .pEnabledFeatures(features)
                .pQueueCreateInfos(queueCreationInfoBuf)

            val pp = stack.mallocPointer(1)
            vkCheck(
                vkCreateDevice(physicalDevice.vkPhysicalDevice, deviceCreateInfo, null, pp),
                "Failed to create device"
            )
            vkDevice = VkDevice(pp[0], physicalDevice.vkPhysicalDevice, deviceCreateInfo)

            memoryAllocator = MemoryAllocator(instance, physicalDevice, vkDevice)
        }
    }

    fun waitIdle() {
        vkDeviceWaitIdle(vkDevice)
    }

    fun cleanup() {
        Logger.debug("Destroying Vulkan device")
        memoryAllocator.cleanUp()
        vkDestroyDevice(vkDevice, null)
    }

    companion object {
        private fun getDeviceExtensions(physicalDevice: PhysicalDevice): Set<String> {
            val deviceExtensions: MutableSet<String> = HashSet()
            MemoryStack.stackPush().use { stack ->
                val numExtensionsBuf = stack.callocInt(1)
                vkEnumerateDeviceExtensionProperties(
                    physicalDevice.vkPhysicalDevice,
                    null as String?,
                    numExtensionsBuf,
                    null
                )
                val numExtensions = numExtensionsBuf[0]
                Logger.debug("Device supports [{}] extensions", numExtensions)
                val propsBuff = VkExtensionProperties.calloc(numExtensions, stack)
                vkEnumerateDeviceExtensionProperties(
                    physicalDevice.vkPhysicalDevice,
                    null as String?,
                    numExtensionsBuf,
                    propsBuff
                )
                for (i in 0..<numExtensions) {
                    val props = propsBuff[i]
                    val extensionName = props.extensionNameString()
                    deviceExtensions.add(extensionName)
                    Logger.debug("Supported device extension [{}]", extensionName)
                }
            }
            return deviceExtensions
        }
    }
}
