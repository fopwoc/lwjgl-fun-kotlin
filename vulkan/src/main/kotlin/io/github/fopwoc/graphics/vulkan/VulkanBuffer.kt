package io.github.fopwoc.graphics.vulkan

import io.github.fopwoc.graphics.vulkan.VulkanUtils.vkCheck
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.util.vma.Vma.vmaCreateBuffer
import org.lwjgl.util.vma.Vma.vmaDestroyBuffer
import org.lwjgl.util.vma.Vma.vmaMapMemory
import org.lwjgl.util.vma.Vma.vmaUnmapMemory
import org.lwjgl.util.vma.VmaAllocationCreateInfo
import org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO
import org.lwjgl.vulkan.VkBufferCreateInfo


class VulkanBuffer(
    val device: Device,
    val requestedSize: Long,
     bufferUsage: Int,  memoryUsage: Int,
     requiredFlags: Int
) {

    private val allocation: Long
    val buffer: Long
    private val pb: PointerBuffer

    private var mappedMemory: Long

    init {
        mappedMemory = NULL

        MemoryStack.stackPush().use { stack ->
            val bufferCreateInfo = VkBufferCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                .size(requestedSize)
                .usage(bufferUsage)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
            val allocInfo = VmaAllocationCreateInfo.calloc(stack)
                .requiredFlags(requiredFlags)
                .usage(memoryUsage)
            val pAllocation = stack.callocPointer(1)
            val lp = stack.mallocLong(1)
            vkCheck(
                vmaCreateBuffer(
                    device.memoryAllocator.vmaAllocator, bufferCreateInfo, allocInfo, lp,
                    pAllocation, null
                ), "Failed to create buffer"
            )
            buffer = lp[0]
            allocation = pAllocation[0]
            pb = MemoryUtil.memAllocPointer(1)
        }
    }


    fun map(): Long {
        if (mappedMemory == NULL) {
            vkCheck(
                vmaMapMemory(device.memoryAllocator.vmaAllocator, allocation, pb),
                "Failed to map allocation"
            )
            mappedMemory = pb[0]
        }
        return mappedMemory
    }

    fun unMap() {
        if (mappedMemory != NULL) {
            vmaUnmapMemory(device.memoryAllocator.vmaAllocator, allocation)
            mappedMemory = NULL
        }
    }

    fun cleanup() {
        pb.free()
        unMap()
        vmaDestroyBuffer(device.memoryAllocator.vmaAllocator, buffer, allocation)
    }
}
