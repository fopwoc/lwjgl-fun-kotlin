package io.github.fopwoc.graphics.geometry

import io.github.fopwoc.core.EngineProperties
import io.github.fopwoc.graphics.VulkanModel
import io.github.fopwoc.graphics.vulkan.Attachment
import io.github.fopwoc.graphics.vulkan.CommandBuffer
import io.github.fopwoc.graphics.vulkan.CommandPool
import io.github.fopwoc.graphics.vulkan.DescriptorPool
import io.github.fopwoc.graphics.vulkan.DescriptorSetLayout
import io.github.fopwoc.graphics.vulkan.Device
import io.github.fopwoc.graphics.vulkan.DynUniformDescriptorSet
import io.github.fopwoc.graphics.vulkan.DynUniformDescriptorSetLayout
import io.github.fopwoc.graphics.vulkan.Fence
import io.github.fopwoc.graphics.vulkan.GraphConstants
import io.github.fopwoc.graphics.vulkan.Pipeline
import io.github.fopwoc.graphics.vulkan.PipelineCache
import io.github.fopwoc.graphics.vulkan.Queue
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
import io.github.fopwoc.graphics.vulkan.VulkanUtils.setMatrixAsPushConstant
import io.github.fopwoc.model.DescriptorTypeCount
import io.github.fopwoc.model.PipeLineCreationInfo
import io.github.fopwoc.model.ShaderModuleData
import io.github.fopwoc.model.SyncSemaphores
import io.github.fopwoc.model.VulkanMaterial
import io.github.fopwoc.scene.Scene
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC
import org.lwjgl.vulkan.VK10.VK_INDEX_TYPE_UINT32
import org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
import org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS
import org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
import org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT
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
import org.lwjgl.vulkan.VK10.vkCmdSetScissor
import org.lwjgl.vulkan.VK10.vkCmdSetViewport
import org.lwjgl.vulkan.VkClearValue
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkRect2D
import org.lwjgl.vulkan.VkRenderPassBeginInfo
import org.lwjgl.vulkan.VkViewport
import java.nio.LongBuffer

class GeometryRenderActivity(
    var swapChain: SwapChain,
    commandPool: CommandPool,
    val pipelineCache: PipelineCache,
    val scene: Scene,
) {

    private val device: Device = swapChain.device
    private val geometryFrameBuffer: GeometryFrameBuffer = GeometryFrameBuffer(swapChain)
    private val materialSize: Int

    private val commandBuffers: Array<CommandBuffer>
    private var descriptorPool: DescriptorPool
    private val descriptorSetMap: HashMap<String, TextureDescriptorSet>
    private val fences: Array<Fence>
    private val geometryDescriptorSetLayouts: Array<DescriptorSetLayout>
    private val materialDescriptorSetLayout: DynUniformDescriptorSetLayout
    private val materialsBuffer: VulkanBuffer
    private val materialsDescriptorSet: DynUniformDescriptorSet
    private val pipeLine: Pipeline
    private val projMatrixDescriptorSet: UniformDescriptorSet
    private val projMatrixUniform: VulkanBuffer
    private var shaderProgram: ShaderProgram
    private val textureDescriptorSetLayout: SamplerDescriptorSetLayout
    private val textureSampler: TextureSampler
    private val uniformDescriptorSetLayout: UniformDescriptorSetLayout
    private val viewMatricesBuffer: Array<VulkanBuffer>
    private val viewMatricesDescriptorSets: Array<UniformDescriptorSet>

    init {
        val numImages: Int = swapChain.getNumImages()
        materialSize = calcMaterialsUniformSize()
        shaderProgram = createShaders(device)
        descriptorPool = createDescriptorPool(swapChain, device)
        createDescriptorSets(device, descriptorPool, materialSize, numImages).also {
            uniformDescriptorSetLayout = it.uniformDescriptorSetLayout
            textureDescriptorSetLayout = it.textureDescriptorSetLayout
            materialDescriptorSetLayout = it.materialDescriptorSetLayout
            geometryDescriptorSetLayouts = it.geometryDescriptorSetLayouts
            descriptorSetMap = it.descriptorSetMap
            textureSampler = it.textureSampler
            projMatrixUniform = it.projMatrixUniform
            projMatrixDescriptorSet = it.projMatrixDescriptorSet
            viewMatricesDescriptorSets = it.viewMatricesDescriptorSets
            viewMatricesBuffer = it.viewMatricesBuffer
            materialsBuffer = it.materialsBuffer
            materialsDescriptorSet = it.materialsDescriptorSet
        }
        pipeLine = createPipeline(
            geometryFrameBuffer,
            shaderProgram,
            geometryDescriptorSetLayouts,
            pipelineCache
        )
        createCommandBuffers(device, commandPool, numImages).also { (commandBuffers, fences) ->
            this.commandBuffers = commandBuffers
            this.fences = fences
        }
        copyMatrixToBuffer(projMatrixUniform, scene.projection.projectionMatrix)
    }

    private fun calcMaterialsUniformSize(): Int {
        val physDevice = device.physicalDevice
        val minUboAlignment = physDevice.vkPhysicalDeviceProperties.limits().minUniformBufferOffsetAlignment()
        val mult = GraphConstants.VEC4_SIZE * 9 / minUboAlignment + 1
        return (mult * minUboAlignment).toInt()
    }

    fun registerModels(vulkanModelList: List<VulkanModel>) {
        device.waitIdle()
        var materialCount = 0
        for (vulkanModel in vulkanModelList) {
            for (vulkanMaterial in vulkanModel.vulkanMaterialList) {
                val materialOffset = materialCount * materialSize
                updateTextureDescriptorSet(vulkanMaterial.texture)
                updateTextureDescriptorSet(vulkanMaterial.normalMap)
                updateTextureDescriptorSet(vulkanMaterial.metalRoughMap)
                updateMaterialsBuffer(materialsBuffer, vulkanMaterial, materialOffset)
                materialCount++
            }
        }
    }

    private fun updateMaterialsBuffer(vulkanBuffer: VulkanBuffer, material: VulkanMaterial, offset: Int) {
        val mappedMemory = vulkanBuffer.map()
        val materialBuffer = MemoryUtil.memByteBuffer(mappedMemory, vulkanBuffer.requestedSize.toInt())
        material.diffuseColor.get(offset, materialBuffer)
        materialBuffer.putFloat(offset + GraphConstants.FLOAT_LENGTH * 4, if (material.hasTexture) 1.0f else 0.0f)
        materialBuffer.putFloat(offset + GraphConstants.FLOAT_LENGTH * 5, if (material.hasNormalMap) 1.0f else 0.0f)
        materialBuffer.putFloat(
            offset + GraphConstants.FLOAT_LENGTH * 6,
            if (material.hasMetalRoughMap) 1.0f else 0.0f
        )
        materialBuffer.putFloat(offset + GraphConstants.FLOAT_LENGTH * 7, material.roughnessFactor)
        materialBuffer.putFloat(offset + GraphConstants.FLOAT_LENGTH * 8, material.metallicFactor)
        vulkanBuffer.unMap()
    }

    private fun updateTextureDescriptorSet(texture: Texture) {
        val textureFileName = texture.fileName
        var textureDescriptorSet = descriptorSetMap[textureFileName]
        if (textureDescriptorSet == null) {
            textureDescriptorSet = TextureDescriptorSet(
                descriptorPool, textureDescriptorSetLayout,
                texture, textureSampler, 0
            )
            descriptorSetMap[textureFileName] = textureDescriptorSet
        }
    }

    fun recordCommandBuffer(commandBuffer: CommandBuffer, vulkanModelList: List<VulkanModel>) {
        MemoryStack.stackPush().use { stack ->
            val swapChainExtent = swapChain.swapChainExtent
            val width = swapChainExtent.width()
            val height = swapChainExtent.height()
            val idx = swapChain.currentFrame

            val frameBuffer = geometryFrameBuffer.frameBuffer
            val attachments: Array<Attachment> =
                geometryFrameBuffer.geometryAttachments.attachments
            val clearValues = VkClearValue.calloc(attachments.size, stack)
            for (attachment in attachments) {
                if (attachment.depthAttachment) {
                    clearValues.apply {
                        it.depthStencil()
                            .depth(1.0f)
                    }
                } else {
                    clearValues.apply {
                        it.color()
                            .float32(0, 0.0f)
                            .float32(1, 0.0f)
                            .float32(2, 0.0f)
                            .float32(3, 1f)
                    }
                }
            }
            clearValues.flip()

            val renderPassBeginInfo = VkRenderPassBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .renderPass(geometryFrameBuffer.geometryRenderPass.vkRenderPass)
                .pClearValues(clearValues)
                .renderArea { a: VkRect2D -> a.extent()[width] = height }
                .framebuffer(frameBuffer.vkFrameBuffer)
            val cmdHandle: VkCommandBuffer = commandBuffer.vkCommandBuffer
            vkCmdBeginRenderPass(cmdHandle, renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE)

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeLine.vkPipeline)

            val viewport = VkViewport.calloc(1, stack)
                .x(0f)
                .y(height.toFloat())
                .height(-height.toFloat())
                .width(width.toFloat())
                .minDepth(0.0f)
                .maxDepth(1.0f)
            vkCmdSetViewport(cmdHandle, 0, viewport)

            val scissor = VkRect2D.calloc(1, stack)
                .extent {
                    it
                        .width(width)
                        .height(height)
                }
                .offset {
                    it
                        .x(0)
                        .y(0)
                }
            vkCmdSetScissor(cmdHandle, 0, scissor)

            val descriptorSets: LongBuffer = stack.mallocLong(6)
                .put(0, projMatrixDescriptorSet.vkDescriptorSet)
                .put(1, viewMatricesDescriptorSets[idx].vkDescriptorSet)
                .put(5, materialsDescriptorSet.vkDescriptorSet)
            copyMatrixToBuffer(viewMatricesBuffer[idx], scene.camera.viewMatrix)

            recordEntities(stack, cmdHandle, descriptorSets, vulkanModelList)

            vkCmdEndRenderPass(cmdHandle)
        }
    }

    private fun recordEntities(
        stack: MemoryStack,
        cmdHandle: VkCommandBuffer,
        descriptorSets: LongBuffer,
        vulkanModelList: List<VulkanModel>,
    ) {
        val offsets = stack.mallocLong(1)
        offsets.put(0, 0L)

        val vertexBuffer = stack.mallocLong(1)
        val dynDescrSetOffset = stack.callocInt(1)
        var materialCount = 0

        for (vulkanModel in vulkanModelList) {
            val modelId = vulkanModel.modelId

            val entities = scene.getEntitiesByModelId(modelId)
            if (entities!!.isEmpty()) {
                materialCount += vulkanModel.vulkanMaterialList.size
                continue
            }

            for (material in vulkanModel.vulkanMaterialList) {
                val materialOffset = materialCount * materialSize
                dynDescrSetOffset.put(0, materialOffset)
                val textureDescriptorSet = descriptorSetMap.getValue(material.texture.fileName)
                val normalMapDescriptorSet = descriptorSetMap.getValue(material.normalMap.fileName)
                val metalRoughDescriptorSet = descriptorSetMap.getValue(material.metalRoughMap.fileName)

                for (mesh in material.vulkanMeshList) {
                    descriptorSets.put(2, textureDescriptorSet.vkDescriptorSet)
                    descriptorSets.put(3, normalMapDescriptorSet.vkDescriptorSet)
                    descriptorSets.put(4, metalRoughDescriptorSet.vkDescriptorSet)

                    vertexBuffer.put(0, mesh.verticesBuffer.buffer)
                    vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets)
                    vkCmdBindIndexBuffer(cmdHandle, mesh.indicesBuffer.buffer, 0, VK_INDEX_TYPE_UINT32)

                    for (entity in entities) {
                        vkCmdBindDescriptorSets(
                            cmdHandle,
                            VK_PIPELINE_BIND_POINT_GRAPHICS,
                            pipeLine.vkPipelineLayout,
                            0,
                            descriptorSets,
                            dynDescrSetOffset
                        )
                        setMatrixAsPushConstant(pipeLine, cmdHandle, entity.modelMatrix)
                        vkCmdDrawIndexed(cmdHandle, mesh.numIndices, 1, 0, 0, 0)
                    }
                }

                materialCount++
            }
        }
    }

    fun resize(swapChain: SwapChain) {
        copyMatrixToBuffer(projMatrixUniform, scene.projection.projectionMatrix)
        this.swapChain = swapChain
        geometryFrameBuffer.resize(swapChain)
    }

    fun submit(queue: Queue) {
        MemoryStack.stackPush().use { stack ->
            val idx = swapChain.currentFrame
            val commandBuffer = commandBuffers[idx]
            val currentFence = fences[idx]
            val syncSemaphores: SyncSemaphores = swapChain.syncSemaphoresList[idx]
            queue.submit(
                stack.pointers(commandBuffer.vkCommandBuffer),
                stack.longs(syncSemaphores.imgAcquisitionSemaphore.vkSemaphore),
                stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT),
                stack.longs(syncSemaphores.geometryCompleteSemaphore.vkSemaphore),
                currentFence
            )
        }
    }

    fun beginRecording(): CommandBuffer {
        val idx = swapChain.currentFrame
        val fence = fences[idx]
        val commandBuffer = commandBuffers[idx]
        fence.fenceWait()
        fence.reset()
        commandBuffer.reset()
        commandBuffer.beginRecording()
        return commandBuffer
    }

    fun endRecording(commandBuffer: CommandBuffer) {
        commandBuffer.endRecording()
    }

    fun cleanup() {
        pipeLine.cleanup()
        materialsBuffer.cleanup()
        viewMatricesBuffer.forEach { it.cleanup() }
        projMatrixUniform.cleanup()
        textureSampler.cleanup()
        materialDescriptorSetLayout.cleanup()
        textureDescriptorSetLayout.cleanup()
        uniformDescriptorSetLayout.cleanup()
        descriptorPool.cleanup()
        shaderProgram.cleanup()
        geometryFrameBuffer.cleanup()
        commandBuffers.forEach { it.cleanup() }
        fences.forEach { it.cleanup() }
    }

    fun getAttachments(): Array<Attachment> {
        return geometryFrameBuffer.geometryAttachments.attachments
    }

    companion object {
        private const val GEOMETRY_FRAGMENT_SHADER_FILE_GLSL = "resources/shaders/geometry_fragment.glsl"
        private const val GEOMETRY_FRAGMENT_SHADER_FILE_SPV = "$GEOMETRY_FRAGMENT_SHADER_FILE_GLSL.spv"
        private const val GEOMETRY_VERTEX_SHADER_FILE_GLSL = "resources/shaders/geometry_vertex.glsl"
        private const val GEOMETRY_VERTEX_SHADER_FILE_SPV = "$GEOMETRY_VERTEX_SHADER_FILE_GLSL.spv"

        private fun createShaders(device: Device): ShaderProgram {
            val engineProperties = EngineProperties.getInstance()
            if (engineProperties.shaderRecompilation) {
                compileShaderIfChanged(GEOMETRY_VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader)
                compileShaderIfChanged(GEOMETRY_FRAGMENT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_fragment_shader)
            }

            return ShaderProgram(
                device,
                arrayOf(
                    ShaderModuleData(VK_SHADER_STAGE_VERTEX_BIT, GEOMETRY_VERTEX_SHADER_FILE_SPV),
                    ShaderModuleData(VK_SHADER_STAGE_FRAGMENT_BIT, GEOMETRY_FRAGMENT_SHADER_FILE_SPV)
                )
            )
        }

        private fun createDescriptorPool(swapChain: SwapChain, device: Device): DescriptorPool {
            val engineProps = EngineProperties.getInstance()
            val descriptorTypeCounts = arrayOf(
                DescriptorTypeCount(
                    count = swapChain.getNumImages() + 1,
                    descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER
                ),
                DescriptorTypeCount(
                    count = engineProps.maxMaterials * 3,
                    descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
                ),
                DescriptorTypeCount(
                    count = 1,
                    descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC
                )
            )

            return DescriptorPool(device, descriptorTypeCounts)
        }

        // TODO: so bad, rework this
        private data class DescriptorSet(
            val uniformDescriptorSetLayout: UniformDescriptorSetLayout,
            val textureDescriptorSetLayout: SamplerDescriptorSetLayout,
            val materialDescriptorSetLayout: DynUniformDescriptorSetLayout,
            val geometryDescriptorSetLayouts: Array<DescriptorSetLayout>,
            val descriptorSetMap: HashMap<String, TextureDescriptorSet>,
            val textureSampler: TextureSampler,
            val projMatrixUniform: VulkanBuffer,
            val projMatrixDescriptorSet: UniformDescriptorSet,
            val viewMatricesDescriptorSets: Array<UniformDescriptorSet>,
            val viewMatricesBuffer: Array<VulkanBuffer>,
            val materialsBuffer: VulkanBuffer,
            val materialsDescriptorSet: DynUniformDescriptorSet,
        )

        private fun createDescriptorSets(
            device: Device,
            descriptorPool: DescriptorPool,
            materialSize: Int,
            numImages: Int,
        ): DescriptorSet {
            val engineProps = EngineProperties.getInstance()

            val uniformDescriptorSetLayout = UniformDescriptorSetLayout(device, 0, VK_SHADER_STAGE_VERTEX_BIT)
            val textureDescriptorSetLayout = SamplerDescriptorSetLayout(device, 0, VK_SHADER_STAGE_FRAGMENT_BIT)
            val materialDescriptorSetLayout = DynUniformDescriptorSetLayout(device, 0, VK_SHADER_STAGE_FRAGMENT_BIT)

            val geometryDescriptorSetLayouts = arrayOf<DescriptorSetLayout>(
                uniformDescriptorSetLayout,
                uniformDescriptorSetLayout,
                textureDescriptorSetLayout,
                textureDescriptorSetLayout,
                textureDescriptorSetLayout,
                materialDescriptorSetLayout
            )

            val descriptorSetMap = HashMap<String, TextureDescriptorSet>()
            val textureSampler = TextureSampler(device, 1, true)
            val projMatrixUniform = VulkanBuffer(
                device,
                GraphConstants.MAT4X4_SIZE.toLong(),
                VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                0
            )
            val projMatrixDescriptorSet =
                UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout, projMatrixUniform, 0)

            val materialsBuffer = VulkanBuffer(
                device,
                materialSize.toLong() * engineProps.maxMaterials,
                VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                0
            )
            val materialsDescriptorSet = DynUniformDescriptorSet(
                descriptorPool,
                materialDescriptorSetLayout,
                materialsBuffer,
                0,
                materialSize.toLong()
            )

            val viewMatricesBuffer = Array(numImages) {
                VulkanBuffer(
                    device,
                    GraphConstants.MAT4X4_SIZE.toLong(),
                    VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                    0
                )
            }

            val viewMatricesDescriptorSets = Array(numImages) {
                UniformDescriptorSet(
                    descriptorPool,
                    uniformDescriptorSetLayout,
                    viewMatricesBuffer[it],
                    0
                )
            }

            return DescriptorSet(
                uniformDescriptorSetLayout,
                textureDescriptorSetLayout = textureDescriptorSetLayout,
                materialDescriptorSetLayout = materialDescriptorSetLayout,
                geometryDescriptorSetLayouts = geometryDescriptorSetLayouts,
                descriptorSetMap = descriptorSetMap,
                textureSampler = textureSampler,
                projMatrixUniform = projMatrixUniform,
                projMatrixDescriptorSet = projMatrixDescriptorSet,
                viewMatricesDescriptorSets = viewMatricesDescriptorSets,
                viewMatricesBuffer = viewMatricesBuffer,
                materialsBuffer = materialsBuffer,
                materialsDescriptorSet = materialsDescriptorSet
            )
        }

        private fun createPipeline(
            geometryFrameBuffer: GeometryFrameBuffer,
            shaderProgram: ShaderProgram,
            geometryDescriptorSetLayouts: Array<DescriptorSetLayout>,
            pipelineCache: PipelineCache,
        ): Pipeline {
            val pipeLineCreationInfo = PipeLineCreationInfo(
                vkRenderPass = geometryFrameBuffer.geometryRenderPass.vkRenderPass,
                shaderProgram = shaderProgram,
                numColorAttachments = GeometryAttachments.NUMBER_COLOR_ATTACHMENTS,
                hasDepthAttachment = true,
                useBlend = true,
                pushConstantsSize = GraphConstants.MAT4X4_SIZE,
                viInputStateInfo = VertexBufferStructure(),
                descriptorSetLayouts = geometryDescriptorSetLayouts
            )

            return Pipeline(pipelineCache, pipeLineCreationInfo).also {
                pipeLineCreationInfo.cleanup()
            }
        }

        private fun createCommandBuffers(
            device: Device,
            commandPool: CommandPool,
            numImages: Int,
        ): Pair<Array<CommandBuffer>, Array<Fence>> {
            val commandBuffers = Array(numImages) {
                CommandBuffer(commandPool, primary = true, oneTimeSubmit = false)
            }

            val fences = Array(numImages) {
                Fence(device, signaled = true)
            }

            return Pair(commandBuffers, fences)
        }
    }
}
