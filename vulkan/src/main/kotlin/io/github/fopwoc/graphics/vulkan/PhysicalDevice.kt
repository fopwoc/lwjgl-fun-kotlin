package io.github.fopwoc.graphics.vulkan

import io.github.fopwoc.graphics.vulkan.VulkanUtils.vkCheck
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.KHRSwapchain
import org.lwjgl.vulkan.VK10.VK_QUEUE_GRAPHICS_BIT
import org.lwjgl.vulkan.VK10.vkEnumerateDeviceExtensionProperties
import org.lwjgl.vulkan.VK10.vkEnumeratePhysicalDevices
import org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceFeatures
import org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties
import org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceProperties
import org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceQueueFamilyProperties
import org.lwjgl.vulkan.VkExtensionProperties
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties
import org.lwjgl.vulkan.VkPhysicalDeviceProperties
import org.lwjgl.vulkan.VkQueueFamilyProperties
import org.pmw.tinylog.Logger


class PhysicalDevice(val vkPhysicalDevice: VkPhysicalDevice) {
    val vkDeviceExtensions: VkExtensionProperties.Buffer
    val vkMemoryProperties: VkPhysicalDeviceMemoryProperties
    val vkPhysicalDeviceFeatures: VkPhysicalDeviceFeatures
    val vkPhysicalDeviceProperties: VkPhysicalDeviceProperties
    val vkQueueFamilyProps: VkQueueFamilyProperties.Buffer

    val deviceName: String
        get() = vkPhysicalDeviceProperties.deviceNameString()

    init {
        MemoryStack.stackPush().use { stack ->
            val intBuffer = stack.mallocInt(1)

            // Get device properties
            vkPhysicalDeviceProperties = VkPhysicalDeviceProperties.calloc()
            vkGetPhysicalDeviceProperties(vkPhysicalDevice, vkPhysicalDeviceProperties)

            // Get device extensions
            vkCheck(
                vkEnumerateDeviceExtensionProperties(vkPhysicalDevice, null as String?, intBuffer, null),
                "Failed to get number of device extension properties"
            )
            vkDeviceExtensions = VkExtensionProperties.calloc(intBuffer[0])
            vkCheck(
                vkEnumerateDeviceExtensionProperties(vkPhysicalDevice, null as String?, intBuffer, vkDeviceExtensions),
                "Failed to get extension properties"
            )

            // Get Queue family properties
            vkGetPhysicalDeviceQueueFamilyProperties(vkPhysicalDevice, intBuffer, null)
            vkQueueFamilyProps = VkQueueFamilyProperties.calloc(intBuffer[0])
            vkGetPhysicalDeviceQueueFamilyProperties(vkPhysicalDevice, intBuffer, vkQueueFamilyProps)

            vkPhysicalDeviceFeatures = VkPhysicalDeviceFeatures.calloc()
            vkGetPhysicalDeviceFeatures(vkPhysicalDevice, vkPhysicalDeviceFeatures)

            // Get Memory information and properties
            vkMemoryProperties = VkPhysicalDeviceMemoryProperties.calloc()
            vkGetPhysicalDeviceMemoryProperties(vkPhysicalDevice, vkMemoryProperties)
        }
    }

    private fun hasKHRSwapChainExtension(): Boolean {
        var result = false
        val numExtensions = vkDeviceExtensions.capacity()
        for (i in 0..<numExtensions) {
            val extensionName = vkDeviceExtensions[i].extensionNameString()
            if (KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME == extensionName) {
                result = true
                break
            }
        }
        return result
    }

    private fun hasGraphicsQueueFamily(): Boolean {
        var result = false
        val numQueueFamilies = vkQueueFamilyProps.capacity()
        for (i in 0..<numQueueFamilies) {
            val familyProps = vkQueueFamilyProps[i]
            if (familyProps.queueFlags() and VK_QUEUE_GRAPHICS_BIT != 0) {
                result = true
                break
            }
        }
        return result
    }

    fun cleanup() {
        Logger.debug("Destroying physical device [{}]", vkPhysicalDeviceProperties.deviceNameString())
        vkMemoryProperties.free()
        vkPhysicalDeviceFeatures.free()
        vkQueueFamilyProps.free()
        vkDeviceExtensions.free()
        vkPhysicalDeviceProperties.free()
    }

    companion object {
        fun createPhysicalDevice(
            instance: VulkanInstance,
            preferredDeviceName: String
        ): PhysicalDevice {
            Logger.debug("Selecting physical devices");

            var selectedPhysicalDevice: PhysicalDevice? = null

            MemoryStack.stackPush().use { stack ->
                val pPhysicalDevices: PointerBuffer = getPhysicalDevices(instance, stack)!!
                val numDevices = pPhysicalDevices.capacity() ?: 0
                if (numDevices <= 0) {
                    throw RuntimeException("No physical devices found")
                }

                // Populate available devices
                // Populate available devices
                val devices: MutableList<PhysicalDevice> = ArrayList()
                for (i in 0..<numDevices) {
                    val vkPhysicalDevice = VkPhysicalDevice(pPhysicalDevices[i], instance.vkInstance)
                    val physicalDevice = PhysicalDevice(vkPhysicalDevice)
                    val deviceName: String = physicalDevice.deviceName
                    if (physicalDevice.hasGraphicsQueueFamily() && physicalDevice.hasKHRSwapChainExtension()) {
                        Logger.debug("Device [{}] supports required extensions", deviceName)
                        if (preferredDeviceName == deviceName) {
                            selectedPhysicalDevice = physicalDevice
                            break
                        }
                        devices.add(physicalDevice)
                    } else {
                        Logger.debug("Device [{}] does not support required extensions", deviceName)
                        physicalDevice.cleanup()
                    }
                }

                // No preferred device or it does not meet requirements, just pick the first one
                selectedPhysicalDevice =
                    if (selectedPhysicalDevice == null && devices.isNotEmpty()) devices.removeAt(0) else selectedPhysicalDevice;

                // Clean up non-selected devices
                for (physicalDevice in devices) {
                    physicalDevice.cleanup()
                }

                if (selectedPhysicalDevice == null) {
                    throw RuntimeException("No suitable physical devices found");
                }

                Logger.debug("Selected device: [{}]", selectedPhysicalDevice?.deviceName)
            }

            return selectedPhysicalDevice!!
        }

        fun getPhysicalDevices(
            vulkanInstance: VulkanInstance,
            stack: MemoryStack
        ): PointerBuffer? {
            val pPhysicalDevices: PointerBuffer

            // Get number of physical devices
            val intBuffer = stack.mallocInt(1)
            vkCheck(
                vkEnumeratePhysicalDevices(vulkanInstance.vkInstance, intBuffer, null),
                "Failed to get number of physical devices"
            )
            val numDevices = intBuffer[0]
            Logger.debug("Detected {} physical device(s)", numDevices)

            // Populate physical devices list pointer
            pPhysicalDevices = stack.mallocPointer(numDevices)
            vkCheck(
                vkEnumeratePhysicalDevices(vulkanInstance.vkInstance, intBuffer, pPhysicalDevices),
                "Failed to get physical devices"
            )
            return pPhysicalDevices
        }
    }
}

