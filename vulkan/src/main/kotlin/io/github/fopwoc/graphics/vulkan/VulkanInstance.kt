package io.github.fopwoc.graphics.vulkan

import io.github.fopwoc.graphics.vulkan.VulkanUtils.vkCheck
import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.EXTDebugUtils
import org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT
import org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT
import org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT
import org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT
import org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT
import org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT
import org.lwjgl.vulkan.EXTDebugUtils.VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT
import org.lwjgl.vulkan.EXTDebugUtils.vkCreateDebugUtilsMessengerEXT
import org.lwjgl.vulkan.EXTDebugUtils.vkDestroyDebugUtilsMessengerEXT
import org.lwjgl.vulkan.KHRPortabilitySubset
import org.lwjgl.vulkan.VK10.VK_FALSE
import org.lwjgl.vulkan.VK10.VK_NULL_HANDLE
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO
import org.lwjgl.vulkan.VK10.vkCreateInstance
import org.lwjgl.vulkan.VK10.vkDestroyInstance
import org.lwjgl.vulkan.VK10.vkEnumerateInstanceExtensionProperties
import org.lwjgl.vulkan.VK10.vkEnumerateInstanceLayerProperties
import org.lwjgl.vulkan.VK11.VK_API_VERSION_1_1
import org.lwjgl.vulkan.VkApplicationInfo
import org.lwjgl.vulkan.VkDebugUtilsMessengerCallbackDataEXT
import org.lwjgl.vulkan.VkDebugUtilsMessengerCreateInfoEXT
import org.lwjgl.vulkan.VkExtensionProperties
import org.lwjgl.vulkan.VkInstance
import org.lwjgl.vulkan.VkInstanceCreateInfo
import org.lwjgl.vulkan.VkLayerProperties
import org.pmw.tinylog.Logger
import java.nio.ByteBuffer
import java.nio.IntBuffer
import org.lwjgl.vulkan.KHRPortabilityEnumeration.VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR

class VulkanInstance(validate: Boolean) {
    val vkInstance: VkInstance

    private var debugUtils: VkDebugUtilsMessengerCreateInfoEXT? = null
    private var vkDebugHandle: Long

    init {
        Logger.debug("Creating Vulkan instance")

        MemoryStack.stackPush().use { stack ->
            val appShortName: ByteBuffer = stack.UTF8("VulkanBook")
            val appInfo: VkApplicationInfo = VkApplicationInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                .pApplicationName(appShortName)
                .applicationVersion(1)
                .pEngineName(appShortName)
                .engineVersion(0)
                .apiVersion(VK_API_VERSION_1_1)

            val validationLayers: List<String> = getSupportedValidationLayers()
            val numValidationLayers = validationLayers.size
            var supportValidation = validate
            if (validate && numValidationLayers == 0) {
                supportValidation = false
                Logger.warn(
                    "Request validation but no supported validation layers found. Falling back to no validation"
                )
            }
            Logger.debug("Validation: {}", supportValidation)

            // Set required  layers
            var requiredLayers: PointerBuffer? = null
            if (supportValidation) {
                requiredLayers = stack.mallocPointer(numValidationLayers)
                for (i in 0..<numValidationLayers) {
                    Logger.debug("Using validation layer [{}]", validationLayers[i])
                    requiredLayers.put(i, stack.ASCII(validationLayers[i]))
                }
            }

            val instanceExtensions = getInstanceExtensions()

            // GLFW Extension
            val glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions()
                ?: throw RuntimeException("Failed to find the GLFW platform surface extensions")

            val requiredExtensions: PointerBuffer

            val usePortability = instanceExtensions.contains(PORTABILITY_EXTENSION) &&
                VulkanUtils.getOS() === VulkanUtils.OSType.MACOS
            if (supportValidation) {
                val vkDebugUtilsExtension = stack.UTF8(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME)
                val numExtensions =
                    if (usePortability) glfwExtensions.remaining() + 2 else glfwExtensions.remaining() + 1
                requiredExtensions = stack.mallocPointer(numExtensions)
                requiredExtensions.put(glfwExtensions).put(vkDebugUtilsExtension)
                if (usePortability) {
                    requiredExtensions.put(stack.UTF8(PORTABILITY_EXTENSION))
                }
            } else {
                val numExtensions = if (usePortability) glfwExtensions.remaining() + 1 else glfwExtensions.remaining()
                requiredExtensions = stack.mallocPointer(numExtensions)
                requiredExtensions.put(glfwExtensions)
                if (usePortability) {
                    requiredExtensions.put(stack.UTF8(KHRPortabilitySubset.VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME))
                }
            }
            requiredExtensions.flip()

            var extension = MemoryUtil.NULL
            if (supportValidation) {
                debugUtils = createDebugCallBack()
                extension = debugUtils!!.address()
            }

            val instanceInfo = VkInstanceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                .pNext(extension)
                .pApplicationInfo(appInfo)
                .ppEnabledLayerNames(requiredLayers)
                .ppEnabledExtensionNames(requiredExtensions)
            if (usePortability) {
                instanceInfo.flags(0x00000001) // VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR
            }

            val pInstance = stack.mallocPointer(1)
            vkCheck(vkCreateInstance(instanceInfo, null, pInstance), "Error creating instance")
            vkInstance = VkInstance(pInstance[0], instanceInfo)

            vkDebugHandle = VK_NULL_HANDLE
            if (supportValidation) {
                val longBuff = stack.mallocLong(1)
                vkCheck(
                    vkCreateDebugUtilsMessengerEXT(vkInstance, debugUtils, null, longBuff),
                    "Error creating debug utils"
                )
                vkDebugHandle = longBuff[0]
            }
        }
    }

    fun cleanup() {
        Logger.debug("Destroying Vulkan instance")
        if (vkDebugHandle != VK_NULL_HANDLE) {
            vkDestroyDebugUtilsMessengerEXT(vkInstance, vkDebugHandle, null)
        }

        val debugUtils = this.debugUtils
        if (debugUtils != null) {
            debugUtils.pfnUserCallback().free()
            debugUtils.free()
        }
        vkDestroyInstance(vkInstance, null)
    }

    companion object {
        private const val PORTABILITY_EXTENSION = "VK_KHR_portability_enumeration"

        private const val MESSAGE_SEVERITY_BITMASK = VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT or
            VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT
        private const val MESSAGE_TYPE_BITMASK = VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT or
            VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT or
            VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT
        private fun getSupportedValidationLayers(): List<String> {
            MemoryStack.stackPush().use { stack ->
                val numLayersArr: IntBuffer = stack.callocInt(1)
                vkEnumerateInstanceLayerProperties(numLayersArr, null)
                val numLayers = numLayersArr.get(0)
                Logger.debug("Instance supports [{}] layers", numLayers)

                val propsBuf = VkLayerProperties.calloc(numLayers, stack)
                vkEnumerateInstanceLayerProperties(numLayersArr, propsBuf)
                val supportedLayers: MutableList<String> = ArrayList()
                for (i in 0..<numLayers) {
                    val props = propsBuf[i]
                    val layerName = props.layerNameString()
                    supportedLayers.add(layerName)
                    Logger.debug("Supported layer [{}]", layerName)
                }

                val layersToUse: MutableList<String> = ArrayList()

                // Main validation layer
                if (supportedLayers.contains("VK_LAYER_KHRONOS_validation")) {
                    layersToUse.add("VK_LAYER_KHRONOS_validation")
                    return layersToUse
                }

                // Fallback 1
                if (supportedLayers.contains("VK_LAYER_LUNARG_standard_validation")) {
                    layersToUse.add("VK_LAYER_LUNARG_standard_validation")
                    return layersToUse
                }

                // Fallback 2 (set)
                val requestedLayers: MutableList<String> = ArrayList()
                requestedLayers.add("VK_LAYER_GOOGLE_threading")
                requestedLayers.add("VK_LAYER_LUNARG_parameter_validation")
                requestedLayers.add("VK_LAYER_LUNARG_object_tracker")
                requestedLayers.add("VK_LAYER_LUNARG_core_validation")
                requestedLayers.add("VK_LAYER_GOOGLE_unique_objects")

                return requestedLayers.stream().filter { o: String? ->
                    supportedLayers.contains(o)
                }.toList()
            }
        }

        private fun getInstanceExtensions(): Set<String> {
            val instanceExtensions: MutableSet<String> = HashSet()
            MemoryStack.stackPush().use { stack ->
                val numExtensionsBuf = stack.callocInt(1)
                vkEnumerateInstanceExtensionProperties(null as String?, numExtensionsBuf, null)
                val numExtensions = numExtensionsBuf[0]
                Logger.debug("Instance supports [{}] extensions", numExtensions)
                val instanceExtensionsProps = VkExtensionProperties.calloc(numExtensions, stack)
                vkEnumerateInstanceExtensionProperties(null as String?, numExtensionsBuf, instanceExtensionsProps)
                for (i in 0..<numExtensions) {
                    val props = instanceExtensionsProps[i]
                    val extensionName = props.extensionNameString()
                    instanceExtensions.add(extensionName)
                    Logger.debug("Supported instance extension [{}]", extensionName)
                }
            }
            return instanceExtensions
        }

        private fun createDebugCallBack(): VkDebugUtilsMessengerCreateInfoEXT? {
            return VkDebugUtilsMessengerCreateInfoEXT
                .calloc()
                .sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT)
                .messageSeverity(MESSAGE_SEVERITY_BITMASK)
                .messageType(MESSAGE_TYPE_BITMASK)
                .pfnUserCallback { messageSeverity: Int, messageTypes: Int, pCallbackData: Long, pUserData: Long ->
                    val callbackData =
                        VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData)
                    if (messageSeverity and VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT != 0) {
                        Logger.info("VkDebugUtilsCallback, {}", callbackData.pMessageString())
                    } else if (messageSeverity and VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT != 0) {
                        Logger.warn("VkDebugUtilsCallback, {}", callbackData.pMessageString())
                    } else if (messageSeverity and VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT != 0) {
                        Logger.error("VkDebugUtilsCallback, {}", callbackData.pMessageString())
                    } else {
                        Logger.debug("VkDebugUtilsCallback, {}", callbackData.pMessageString())
                    }
                    VK_FALSE
                }
        }
    }
}
