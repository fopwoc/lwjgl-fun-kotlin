package io.github.fopwoc.graphics.vulkan

import io.github.fopwoc.graphics.vulkan.VulkanUtils.vkCheck
import io.github.fopwoc.model.PipeLineCreationInfo
import io.github.fopwoc.model.ShaderModule
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE
import org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA
import org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_SRC_ALPHA
import org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ZERO
import org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD
import org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_A_BIT
import org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_B_BIT
import org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_G_BIT
import org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_R_BIT
import org.lwjgl.vulkan.VK10.VK_COMPARE_OP_LESS_OR_EQUAL
import org.lwjgl.vulkan.VK10.VK_CULL_MODE_NONE
import org.lwjgl.vulkan.VK10.VK_DYNAMIC_STATE_SCISSOR
import org.lwjgl.vulkan.VK10.VK_DYNAMIC_STATE_VIEWPORT
import org.lwjgl.vulkan.VK10.VK_FRONT_FACE_CLOCKWISE
import org.lwjgl.vulkan.VK10.VK_POLYGON_MODE_FILL
import org.lwjgl.vulkan.VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST
import org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT
import org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO
import org.lwjgl.vulkan.VK10.vkCreateGraphicsPipelines
import org.lwjgl.vulkan.VK10.vkCreatePipelineLayout
import org.lwjgl.vulkan.VK10.vkDestroyPipeline
import org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo
import org.lwjgl.vulkan.VkPushConstantRange
import org.pmw.tinylog.Logger
import java.nio.ByteBuffer

class Pipeline(
    pipelineCache: PipelineCache,
    pipeLineCreationInfo: PipeLineCreationInfo,
) {
    private val device: Device
    val vkPipeline: Long
    val vkPipelineLayout: Long

    init {
        Logger.debug("Creating pipeline")
        device = pipelineCache.device

        MemoryStack.stackPush().use { stack ->
            val lp = stack.mallocLong(1)

            val main: ByteBuffer = stack.UTF8("main")

            val shaderModules: Array<ShaderModule> = pipeLineCreationInfo.shaderProgram.shaderModules
            val numModules = shaderModules.size
            val shaderStages = VkPipelineShaderStageCreateInfo.calloc(numModules, stack)
            for (i in 0..<numModules) {
                val shaderModule = shaderModules[i]
                shaderStages[i]
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(shaderModule.shaderStage)
                    .module(shaderModule.handle)
                    .pName(main)
                shaderStages[i].pSpecializationInfo(shaderModule.specInfo)
            }

            val vkPipelineInputAssemblyStateCreateInfo = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)

            val vkPipelineViewportStateCreateInfo = VkPipelineViewportStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                .viewportCount(1)
                .scissorCount(1)

            val vkPipelineRasterizationStateCreateInfo = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                .polygonMode(VK_POLYGON_MODE_FILL)
                .cullMode(VK_CULL_MODE_NONE)
                .frontFace(VK_FRONT_FACE_CLOCKWISE)
                .lineWidth(1.0f)

            val vkPipelineMultisampleStateCreateInfo: VkPipelineMultisampleStateCreateInfo =
                VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)

            var ds: VkPipelineDepthStencilStateCreateInfo? = null
            if (pipeLineCreationInfo.hasDepthAttachment) {
                ds = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                    .depthTestEnable(true)
                    .depthWriteEnable(true)
                    .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL)
                    .depthBoundsTestEnable(false)
                    .stencilTestEnable(false)
            }

            val blendAttState = VkPipelineColorBlendAttachmentState.calloc(
                pipeLineCreationInfo.numColorAttachments,
                stack
            )

            repeat(pipeLineCreationInfo.numColorAttachments) {
                blendAttState[it]
                    .colorWriteMask(
                        VK_COLOR_COMPONENT_R_BIT or
                            VK_COLOR_COMPONENT_G_BIT or
                            VK_COLOR_COMPONENT_B_BIT or
                            VK_COLOR_COMPONENT_A_BIT
                    )
                    .blendEnable(pipeLineCreationInfo.useBlend)
                if (pipeLineCreationInfo.useBlend) {
                    blendAttState[it].colorBlendOp(VK_BLEND_OP_ADD)
                        .alphaBlendOp(VK_BLEND_OP_ADD)
                        .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                        .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                        .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                        .dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO)
                }
            }

            val colorBlendState = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                .pAttachments(blendAttState)

            val vkPipelineDynamicStateCreateInfo = VkPipelineDynamicStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                .pDynamicStates(
                    stack.ints(
                        VK_DYNAMIC_STATE_VIEWPORT,
                        VK_DYNAMIC_STATE_SCISSOR
                    )
                )

            var vpcr: VkPushConstantRange.Buffer? = null
            if (pipeLineCreationInfo.pushConstantsSize > 0) {
                vpcr = VkPushConstantRange.calloc(1, stack)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)
                    .offset(0)
                    .size(pipeLineCreationInfo.pushConstantsSize)
            }

            val descriptorSetLayouts: Array<DescriptorSetLayout> = pipeLineCreationInfo.descriptorSetLayouts
            val numLayouts = descriptorSetLayouts.size ?: 0
            val ppLayout = stack.mallocLong(numLayouts)
            for (i in 0..<numLayouts) {
                ppLayout.put(i, descriptorSetLayouts[i].vkDescriptorLayout)
            }

            val pPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                .pSetLayouts(ppLayout)
                .pPushConstantRanges(vpcr)

            vkCheck(
                vkCreatePipelineLayout(device.vkDevice, pPipelineLayoutCreateInfo, null, lp),
                "Failed to create pipeline layout"
            )
            vkPipelineLayout = lp[0]

            val pipeline = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                .pStages(shaderStages)
                .pVertexInputState(pipeLineCreationInfo.viInputStateInfo.vi)
                .pInputAssemblyState(vkPipelineInputAssemblyStateCreateInfo)
                .pViewportState(vkPipelineViewportStateCreateInfo)
                .pRasterizationState(vkPipelineRasterizationStateCreateInfo)
                .pMultisampleState(vkPipelineMultisampleStateCreateInfo)
                .pColorBlendState(colorBlendState)
                .pDynamicState(vkPipelineDynamicStateCreateInfo)
                .layout(vkPipelineLayout)
                .renderPass(pipeLineCreationInfo.vkRenderPass)
            if (ds != null) {
                pipeline.pDepthStencilState(ds)
            }
            vkCheck(
                vkCreateGraphicsPipelines(
                    device.vkDevice,
                    pipelineCache.vkPipelineCache,
                    pipeline,
                    null,
                    lp
                ),
                "Error creating graphics pipeline"
            )
            vkPipeline = lp[0]
        }
    }

    fun cleanup() {
        Logger.debug("Destroying pipeline")
        vkDestroyPipelineLayout(device.vkDevice, vkPipelineLayout, null)
        vkDestroyPipeline(device.vkDevice, vkPipeline, null)
    }
}
