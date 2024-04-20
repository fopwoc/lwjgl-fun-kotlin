package io.github.fopwoc.graphics.lightning

import io.github.fopwoc.core.EngineProperties
import io.github.fopwoc.graphics.vulkan.GraphConstants
import java.nio.ByteBuffer
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VkSpecializationInfo
import org.lwjgl.vulkan.VkSpecializationMapEntry


class LightSpecConstants {
    private val data: ByteBuffer
    private val specEntryMap: VkSpecializationMapEntry.Buffer
    val specInfo: VkSpecializationInfo

    init {
        val engineProperties = EngineProperties.getInstance()
        data = MemoryUtil.memAlloc(GraphConstants.INT_LENGTH * 3 + GraphConstants.FLOAT_LENGTH)
        data.putInt(GraphConstants.SHADOW_MAP_CASCADE_COUNT)
        data.putInt(if (engineProperties.shadowPcf) 1 else 0)
        data.putFloat(engineProperties.shadowBias)
        data.putInt(if (engineProperties.shadowDebug) 1 else 0)
        data.flip()
        specEntryMap = VkSpecializationMapEntry.calloc(4)
        specEntryMap[0]
            .constantID(0)
            .size(GraphConstants.INT_LENGTH.toLong())
            .offset(0)
        specEntryMap[1]
            .constantID(1)
            .size(GraphConstants.INT_LENGTH.toLong())
            .offset(GraphConstants.INT_LENGTH)
        specEntryMap[2]
            .constantID(2)
            .size(GraphConstants.FLOAT_LENGTH.toLong())
            .offset(GraphConstants.INT_LENGTH * 2)
        specEntryMap[3]
            .constantID(3)
            .size(GraphConstants.INT_LENGTH.toLong())
            .offset(GraphConstants.INT_LENGTH * 2 + GraphConstants.FLOAT_LENGTH)
        specInfo = VkSpecializationInfo.calloc()
        specInfo.pData(data)
            .pMapEntries(specEntryMap)
    }

    fun cleanup() {
        MemoryUtil.memFree(specEntryMap)
        specInfo.free()
        MemoryUtil.memFree(data)
    }
}
