package io.github.fopwoc.model

@JvmRecord
data class InheritanceInfo(
    val vkRenderPass: Long,
    val vkFrameBuffer: Long,
    val subPass: Int
)
