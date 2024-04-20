package io.github.fopwoc.graphics.shadows

import io.github.fopwoc.core.EngineProperties
import io.github.fopwoc.graphics.VulkanModel
import io.github.fopwoc.graphics.geometry.GeometryAttachments
import io.github.fopwoc.graphics.vulkan.Attachment
import io.github.fopwoc.graphics.vulkan.CommandBuffer
import io.github.fopwoc.graphics.vulkan.DescriptorPool
import io.github.fopwoc.graphics.vulkan.DescriptorSetLayout
import io.github.fopwoc.graphics.vulkan.Device
import io.github.fopwoc.graphics.vulkan.GraphConstants
import io.github.fopwoc.graphics.vulkan.Pipeline
import io.github.fopwoc.graphics.vulkan.PipelineCache
import io.github.fopwoc.graphics.vulkan.SamplerDescriptorSetLayout
import io.github.fopwoc.graphics.vulkan.ShaderCompiler.compileShaderIfChanged
import io.github.fopwoc.graphics.vulkan.ShaderProgram
import io.github.fopwoc.graphics.vulkan.SwapChain
import io.github.fopwoc.graphics.vulkan.Texture
import io.github.fopwoc.graphics.vulkan.TextureDescriptorSet
import io.github.fopwoc.graphics.vulkan.TextureSampler
import io.github.fopwoc.graphics.vulkan.UniformDescriptorSet
import io.github.fopwoc.graphics.vulkan.UniformDescriptorSetLayout
import io.github.fopwoc.graphics.vulkan.VertexBufferStructure
import io.github.fopwoc.graphics.vulkan.VulkanBuffer
import io.github.fopwoc.graphics.vulkan.VulkanUtils.copyMatrixToBuffer
import io.github.fopwoc.model.DescriptorTypeCount
import io.github.fopwoc.model.PipeLineCreationInfo
import io.github.fopwoc.model.ShaderModuleData
import io.github.fopwoc.scene.Entity
import io.github.fopwoc.scene.Scene
import org.joml.Matrix4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER
import org.lwjgl.vulkan.VK10.VK_INDEX_TYPE_UINT32
import org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
import org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS
import org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT
import org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_GEOMETRY_BIT
import org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO
import org.lwjgl.vulkan.VK10.VK_SUBPASS_CONTENTS_INLINE
import org.lwjgl.vulkan.VK10.vkCmdBeginRenderPass
import org.lwjgl.vulkan.VK10.vkCmdBindDescriptorSets
import org.lwjgl.vulkan.VK10.vkCmdBindIndexBuffer
import org.lwjgl.vulkan.VK10.vkCmdBindPipeline
import org.lwjgl.vulkan.VK10.vkCmdBindVertexBuffers
import org.lwjgl.vulkan.VK10.vkCmdDrawIndexed
import org.lwjgl.vulkan.VK10.vkCmdEndRenderPass
import org.lwjgl.vulkan.VK10.vkCmdPushConstants
import org.lwjgl.vulkan.VK10.vkCmdSetScissor
import org.lwjgl.vulkan.VK10.vkCmdSetViewport
import org.lwjgl.vulkan.VkClearValue
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkExtent2D
import org.lwjgl.vulkan.VkOffset2D
import org.lwjgl.vulkan.VkRect2D
import org.lwjgl.vulkan.VkRenderPassBeginInfo
import org.lwjgl.vulkan.VkViewport
import java.nio.ByteBuffer
import java.nio.LongBuffer

class ShadowRenderActivity(swapChain: SwapChain, pipelineCache: PipelineCache, val scene: Scene) {

    var cascadeShadows: Array<CascadeShadow>
    private var descriptorPool: DescriptorPool
    private var descriptorSetLayouts: Array<DescriptorSetLayout>
    private var descriptorSetMap: HashMap<String, TextureDescriptorSet> = HashMap()
    private var firstRun: Boolean
    private val device: Device
    private var pipeLine: Pipeline
    private var projMatrixDescriptorSet: Array<UniformDescriptorSet>
    private var shaderProgram: ShaderProgram
    private val shadowsFrameBuffer: ShadowsFrameBuffer
    private var shadowsUniforms: Array<VulkanBuffer>
    private var textureDescriptorSetLayout: SamplerDescriptorSetLayout
    private var textureSampler: TextureSampler
    private var uniformDescriptorSetLayout: UniformDescriptorSetLayout
    private var swapChain: SwapChain

    init {
        firstRun = true
        this.swapChain = swapChain
        device = swapChain.device
        val numImages = swapChain.getNumImages()
        shadowsFrameBuffer = ShadowsFrameBuffer(device)
        shaderProgram = createShaders(device)
        descriptorPool = createDescriptorPool(device, numImages)
        createDescriptorSets(device, descriptorPool, numImages).also {
                (

                    uniformDescriptorSetLayout,
                    textureDescriptorSetLayout,
                    descriptorSetLayouts,
                    textureSampler,
                    shadowsUniforms,
                    projMatrixDescriptorSet,
                ),
            ->

            this.uniformDescriptorSetLayout = uniformDescriptorSetLayout
            this.textureDescriptorSetLayout = textureDescriptorSetLayout
            this.descriptorSetLayouts = descriptorSetLayouts
            this.textureSampler = textureSampler
            this.shadowsUniforms = shadowsUniforms
            this.projMatrixDescriptorSet = projMatrixDescriptorSet
        }
        pipeLine = createPipeline(pipelineCache, shadowsFrameBuffer, shaderProgram, descriptorSetLayouts)
        cascadeShadows = createShadowCascades()
    }

    fun recordCommandBuffer(commandBuffer: CommandBuffer, vulkanModelList: List<VulkanModel>) {
        MemoryStack.stackPush().use { stack ->
            if (firstRun || scene.lightChanged || scene.camera.hasMoved) {
                CascadeShadow.updateCascadeShadows(cascadeShadows, scene)
                if (firstRun) {
                    firstRun = false
                }
            }
            val idx = swapChain.currentFrame
            updateProjViewBuffers(idx)
            val clearValues = VkClearValue.calloc(1, stack)
            clearValues.apply(
                0
            ) { v: VkClearValue -> v.depthStencil().depth(1.0f) }
            val engineProperties = EngineProperties.getInstance()
            val shadowMapSize: Int = engineProperties.shadowMapSize
            val cmdHandle = commandBuffer.vkCommandBuffer
            val viewport = VkViewport.calloc(1, stack)
                .x(0f)
                .y(shadowMapSize.toFloat())
                .height(-shadowMapSize.toFloat())
                .width(shadowMapSize.toFloat())
                .minDepth(0.0f)
                .maxDepth(1.0f)
            vkCmdSetViewport(cmdHandle, 0, viewport)
            val scissor = VkRect2D.calloc(1, stack)
                .extent { it: VkExtent2D ->
                    it
                        .width(shadowMapSize)
                        .height(shadowMapSize)
                }
                .offset { it: VkOffset2D ->
                    it
                        .x(0)
                        .y(0)
                }
            vkCmdSetScissor(cmdHandle, 0, scissor)
            val frameBuffer = shadowsFrameBuffer.frameBuffer
            val renderPassBeginInfo = VkRenderPassBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .renderPass(shadowsFrameBuffer.shadowsRenderPass.vkRenderPass)
                .pClearValues(clearValues)
                .renderArea { a: VkRect2D -> a.extent()[shadowMapSize] = shadowMapSize }
                .framebuffer(frameBuffer.vkFrameBuffer)
            vkCmdBeginRenderPass(cmdHandle, renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE)
            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeLine.vkPipeline)
            val descriptorSets: LongBuffer = stack.mallocLong(2)
                .put(0, projMatrixDescriptorSet[idx].vkDescriptorSet)
            recordEntities(stack, cmdHandle, vulkanModelList, descriptorSets)
            vkCmdEndRenderPass(cmdHandle)
        }
    }

    private fun recordEntities(
        stack: MemoryStack,
        cmdHandle: VkCommandBuffer,
        vulkanModelList: List<VulkanModel>,
        descriptorSets: LongBuffer,
    ) {
        val offsets = stack.mallocLong(1)
        offsets.put(0, 0L)
        val vertexBuffer = stack.mallocLong(1)
        for (vulkanModel in vulkanModelList) {
            val modelId = vulkanModel.modelId
            val entities: List<Entity>? = scene.getEntitiesByModelId(modelId)
            if (entities!!.isEmpty()) {
                continue
            }
            for (material in vulkanModel.vulkanMaterialList) {
                val textureDescriptorSet = descriptorSetMap[material.texture.fileName]
                for (mesh in material.vulkanMeshList) {
                    vertexBuffer.put(0, mesh.verticesBuffer.buffer)
                    vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets)
                    vkCmdBindIndexBuffer(cmdHandle, mesh.indicesBuffer.buffer, 0, VK_INDEX_TYPE_UINT32)
                    for (entity in entities) {
                        descriptorSets.put(1, textureDescriptorSet!!.vkDescriptorSet)
                        vkCmdBindDescriptorSets(
                            cmdHandle,
                            VK_PIPELINE_BIND_POINT_GRAPHICS,
                            pipeLine.vkPipelineLayout,
                            0,
                            descriptorSets,
                            null
                        )
                        setPushConstant(pipeLine, cmdHandle, entity.modelMatrix)
                        vkCmdDrawIndexed(cmdHandle, mesh.numIndices, 1, 0, 0, 0)
                    }
                }
            }
        }
    }

    fun getDepthAttachment(): Attachment {
        return shadowsFrameBuffer.depthAttachment
    }

    fun registerModels(vulkanModelList: List<VulkanModel>) {
        device.waitIdle()
        for (vulkanModel in vulkanModelList) {
            for (vulkanMaterial in vulkanModel.vulkanMaterialList) {
                updateTextureDescriptorSet(vulkanMaterial.texture)
            }
        }
    }

    private fun updateTextureDescriptorSet(texture: Texture) {
        val textureFileName: String = texture.fileName
        var textureDescriptorSet = descriptorSetMap[textureFileName]
        if (textureDescriptorSet == null) {
            textureDescriptorSet = TextureDescriptorSet(
                descriptorPool, textureDescriptorSetLayout,
                texture, textureSampler, 0
            )
            descriptorSetMap[textureFileName] = textureDescriptorSet
        }
    }

    fun resize(swapChain: SwapChain) {
        this.swapChain = swapChain
        CascadeShadow.updateCascadeShadows(cascadeShadows, scene)
    }

    private fun setPushConstant(pipeLine: Pipeline, cmdHandle: VkCommandBuffer, matrix: Matrix4f) {
        MemoryStack.stackPush().use { stack ->
            val pushConstantBuffer: ByteBuffer = stack.malloc(GraphConstants.MAT4X4_SIZE)
            matrix.get(0, pushConstantBuffer)
            vkCmdPushConstants(
                cmdHandle,
                pipeLine.vkPipelineLayout,
                VK_SHADER_STAGE_VERTEX_BIT,
                0,
                pushConstantBuffer
            )
        }
    }

    private fun updateProjViewBuffers(idx: Int) {
        var offset = 0
        for (cascadeShadow in cascadeShadows) {
            copyMatrixToBuffer(shadowsUniforms[idx], cascadeShadow.projViewMatrix, offset)
            offset += GraphConstants.MAT4X4_SIZE
        }
    }

    fun cleanup() {
        pipeLine.cleanup()
        shadowsUniforms.forEach { it.cleanup() }
        uniformDescriptorSetLayout.cleanup()
        textureDescriptorSetLayout.cleanup()
        textureSampler.cleanup()
        descriptorPool.cleanup()
        shaderProgram.cleanup()
        shadowsFrameBuffer.cleanup()
    }

    companion object {
        private const val SHADOW_FRAGMENT_SHADER_FILE_GLSL = "resources/shaders/shadow_fragment.glsl"
        private const val SHADOW_FRAGMENT_SHADER_FILE_SPV = "$SHADOW_FRAGMENT_SHADER_FILE_GLSL.spv"
        private const val SHADOW_GEOMETRY_SHADER_FILE_GLSL = "resources/shaders/shadow_geometry.glsl"
        private const val SHADOW_GEOMETRY_SHADER_FILE_SPV = "$SHADOW_GEOMETRY_SHADER_FILE_GLSL.spv"
        private const val SHADOW_VERTEX_SHADER_FILE_GLSL = "resources/shaders/shadow_vertex.glsl"
        private const val SHADOW_VERTEX_SHADER_FILE_SPV = "$SHADOW_VERTEX_SHADER_FILE_GLSL.spv"

        private fun createShaders(device: Device): ShaderProgram {
            val engineProperties = EngineProperties.getInstance()
            if (engineProperties.shaderRecompilation) {
                compileShaderIfChanged(SHADOW_VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader)
                compileShaderIfChanged(SHADOW_GEOMETRY_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_geometry_shader)
                compileShaderIfChanged(SHADOW_FRAGMENT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_fragment_shader)
            }

            return ShaderProgram(
                device,
                arrayOf(
                    ShaderModuleData(VK_SHADER_STAGE_VERTEX_BIT, SHADOW_VERTEX_SHADER_FILE_SPV),
                    ShaderModuleData(VK_SHADER_STAGE_GEOMETRY_BIT, SHADOW_GEOMETRY_SHADER_FILE_SPV),
                    ShaderModuleData(VK_SHADER_STAGE_FRAGMENT_BIT, SHADOW_FRAGMENT_SHADER_FILE_SPV)
                )
            )
        }

        private fun createDescriptorPool(device: Device, numImages: Int): DescriptorPool {
            val engineProps = EngineProperties.getInstance()

            return DescriptorPool(
                device,
                arrayOf(
                    DescriptorTypeCount(numImages, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER),
                    DescriptorTypeCount(
                        engineProps.maxMaterials,
                        VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
                    )

                )
            )
        }

        data class CreateDescriptorSets(
            val uniformDescriptorSetLayout: UniformDescriptorSetLayout,
            val textureDescriptorSetLayout: SamplerDescriptorSetLayout,
            val descriptorSetLayouts: Array<DescriptorSetLayout>,
            val textureSampler: TextureSampler,
            val shadowsUniforms: Array<VulkanBuffer>,
            val projMatrixDescriptorSet: Array<UniformDescriptorSet>,
        )

        private fun createDescriptorSets(device: Device, descriptorPool: DescriptorPool, numImages: Int): CreateDescriptorSets {
            val uniformDescriptorSetLayout = UniformDescriptorSetLayout(device, 0, VK_SHADER_STAGE_GEOMETRY_BIT)
            val textureDescriptorSetLayout = SamplerDescriptorSetLayout(device, 0, VK_SHADER_STAGE_FRAGMENT_BIT)
            val descriptorSetLayouts = arrayOf<DescriptorSetLayout>(
                uniformDescriptorSetLayout,
                textureDescriptorSetLayout
            )
            val textureSampler = TextureSampler(device, 1, false)

            val shadowsUniforms = Array(numImages) {
                VulkanBuffer(
                    device,
                    GraphConstants.MAT4X4_SIZE.toLong() * GraphConstants.SHADOW_MAP_CASCADE_COUNT,
                    VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                    0
                )
            }

            val projMatrixDescriptorSet = Array(numImages) {
                UniformDescriptorSet(
                    descriptorPool,
                    uniformDescriptorSetLayout,
                    shadowsUniforms[it],
                    0
                )
            }

            return CreateDescriptorSets(
                uniformDescriptorSetLayout,
                textureDescriptorSetLayout,
                descriptorSetLayouts,
                textureSampler,
                shadowsUniforms,
                projMatrixDescriptorSet
            )
        }

        private fun createPipeline(
            pipelineCache: PipelineCache,
            shadowsFrameBuffer: ShadowsFrameBuffer,
            shaderProgram: ShaderProgram,
            descriptorSetLayouts: Array<DescriptorSetLayout>,
        ): Pipeline {
            val pipeLineCreationInfo = PipeLineCreationInfo(
                shadowsFrameBuffer.shadowsRenderPass.vkRenderPass,
                shaderProgram,
                GeometryAttachments.NUMBER_COLOR_ATTACHMENTS,
                true,
                true,
                GraphConstants.MAT4X4_SIZE,
                VertexBufferStructure(),
                descriptorSetLayouts
            )

            return Pipeline(pipelineCache, pipeLineCreationInfo).also {
                pipeLineCreationInfo.cleanup()
            }
        }

        private fun createShadowCascades(): Array<CascadeShadow> {
            return Array(GraphConstants.SHADOW_MAP_CASCADE_COUNT) {
                CascadeShadow()
            }
        }
    }
}
