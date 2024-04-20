package io.github.fopwoc.graphics.vulkan

import org.joml.Matrix4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK10.VK_MAX_MEMORY_TYPES
import org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT
import org.lwjgl.vulkan.VK10.VK_SUCCESS
import org.lwjgl.vulkan.VK10.vkCmdPushConstants
import org.lwjgl.vulkan.VkCommandBuffer
import java.lang.System.getProperty
import java.nio.ByteBuffer
import java.util.Locale

object VulkanUtils {
    enum class OSType {
        WINDOWS,
        MACOS,
        LINUX,
        OTHER
    }

    fun getOS(): OSType {
        val os = getProperty("os.name", "generic").lowercase(Locale.ENGLISH)

        return if ((os.indexOf("mac") >= 0) || (os.indexOf("darwin") >= 0)) {
            OSType.MACOS
        } else if (os.indexOf("win") >= 0) {
            OSType.WINDOWS
        } else if (os.indexOf("nux") >= 0) {
            OSType.LINUX
        } else {
            OSType.OTHER
        }
    }

    fun vkCheck(err: Int, errMsg: String) {
        if (err != VK_SUCCESS) {
            throw RuntimeException("$errMsg: $err")
        }
    }

    fun memoryTypeFromProperties(physDevice: PhysicalDevice, typeBits: Int, reqsMask: Int): Int {
        var typeBitsTmp = typeBits

        var result = -1
        val memoryTypes = physDevice.vkMemoryProperties.memoryTypes()
        for (i in 0..<VK_MAX_MEMORY_TYPES) {
            if (typeBitsTmp and 1 == 1 && memoryTypes[i].propertyFlags() and reqsMask == reqsMask) {
                result = i
                break
            }
            typeBitsTmp = typeBits shr 1
        }
        if (result < 0) {
            throw java.lang.RuntimeException("Failed to find memoryType")
        }
        return result
    }

    fun copyMatrixToBuffer(vulkanBuffer: VulkanBuffer, matrix: Matrix4f, offset: Int = 0) {
        val mappedMemory = vulkanBuffer.map()
        val matrixBuffer: ByteBuffer = MemoryUtil.memByteBuffer(mappedMemory, vulkanBuffer.requestedSize.toInt())
        matrix.get(offset, matrixBuffer)
        vulkanBuffer.unMap()
    }

    fun setMatrixAsPushConstant(pipeLine: Pipeline, cmdHandle: VkCommandBuffer, matrix: Matrix4f) {
        MemoryStack.stackPush().use { stack ->
            val pushConstantBuffer =
                stack.malloc(GraphConstants.MAT4X4_SIZE)
            matrix[0, pushConstantBuffer]
            vkCmdPushConstants(
                cmdHandle,
                pipeLine.vkPipelineLayout,
                VK_SHADER_STAGE_VERTEX_BIT,
                0,
                pushConstantBuffer
            )
        }
    }
}
