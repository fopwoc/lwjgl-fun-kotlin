package io.github.fopwoc.graphics.vulkan

import io.github.fopwoc.graphics.vulkan.VulkanUtils.vkCheck
import io.github.fopwoc.model.ShaderModule
import io.github.fopwoc.model.ShaderModuleData
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO
import org.lwjgl.vulkan.VK10.vkCreateShaderModule
import org.lwjgl.vulkan.VK10.vkDestroyShaderModule
import org.lwjgl.vulkan.VkShaderModuleCreateInfo
import org.pmw.tinylog.Logger


class ShaderProgram(
    val device: Device,
    shaderModuleData: Array<ShaderModuleData>,
) {

    val shaderModules: Array<ShaderModule>

    init {

        try {
            val numModules = shaderModuleData.size
            shaderModules = Array(numModules) {
                val moduleContents = Files.readAllBytes(File(shaderModuleData[it].shaderSpvFile).toPath())
                val moduleHandle = createShaderModule(device, moduleContents)
                ShaderModule(
                    shaderModuleData[it].shaderStage, moduleHandle,
                    shaderModuleData[it].specInfo
                )
            }
        } catch (excp: IOException) {
            Logger.error("Error reading shader files", excp)
            throw RuntimeException(excp)
        }
    }

    fun cleanup() {
        shaderModules.forEach {
            vkDestroyShaderModule(device.vkDevice, it.handle, null)
        }
    }


    companion object {
        private fun createShaderModule(device: Device, code: ByteArray): Long {
            MemoryStack.stackPush().use { stack ->
                val pCode: ByteBuffer = stack.malloc(code.size).put(0, code)
                val moduleCreateInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                    .pCode(pCode)
                val lp = stack.mallocLong(1)
                vkCheck(
                    vkCreateShaderModule(device.vkDevice, moduleCreateInfo, null, lp),
                    "Failed to create shader module"
                )
                return lp[0]
            }
        }
    }
}








