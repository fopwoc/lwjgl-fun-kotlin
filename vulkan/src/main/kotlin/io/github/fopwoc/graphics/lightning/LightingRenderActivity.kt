package io.github.fopwoc.graphics.lightning

import io.github.fopwoc.core.EngineProperties
import io.github.fopwoc.graphics.shadows.CascadeShadow
import io.github.fopwoc.graphics.vulkan.Attachment
import io.github.fopwoc.graphics.vulkan.CommandBuffer
import io.github.fopwoc.graphics.vulkan.CommandPool
import io.github.fopwoc.graphics.vulkan.DescriptorPool
import io.github.fopwoc.graphics.vulkan.DescriptorSetLayout
import io.github.fopwoc.graphics.vulkan.Device
import io.github.fopwoc.graphics.vulkan.Fence
import io.github.fopwoc.graphics.vulkan.GraphConstants
import io.github.fopwoc.graphics.vulkan.Pipeline
import io.github.fopwoc.graphics.vulkan.PipelineCache
import io.github.fopwoc.graphics.vulkan.Queue
import io.github.fopwoc.graphics.vulkan.ShaderCompiler.compileShaderIfChanged
import io.github.fopwoc.graphics.vulkan.ShaderProgram
import io.github.fopwoc.graphics.vulkan.StorageDescriptorSet
import io.github.fopwoc.graphics.vulkan.StorageDescriptorSetLayout
import io.github.fopwoc.graphics.vulkan.SwapChain
import io.github.fopwoc.graphics.vulkan.UniformDescriptorSet
import io.github.fopwoc.graphics.vulkan.UniformDescriptorSetLayout
import io.github.fopwoc.graphics.vulkan.VulkanBuffer
import io.github.fopwoc.graphics.vulkan.VulkanUtils.copyMatrixToBuffer
import io.github.fopwoc.model.DescriptorTypeCount
import io.github.fopwoc.model.PipeLineCreationInfo
import io.github.fopwoc.model.ShaderModuleData
import io.github.fopwoc.model.SyncSemaphores
import io.github.fopwoc.scene.Light
import io.github.fopwoc.scene.Scene
import org.joml.Matrix4f
import org.joml.Vector4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
import org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER
import org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
import org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS
import org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
import org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT
import org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO
import org.lwjgl.vulkan.VK10.VK_SUBPASS_CONTENTS_INLINE
import org.lwjgl.vulkan.VK10.vkCmdBeginRenderPass
import org.lwjgl.vulkan.VK10.vkCmdBindDescriptorSets
import org.lwjgl.vulkan.VK10.vkCmdBindPipeline
import org.lwjgl.vulkan.VK10.vkCmdDraw
import org.lwjgl.vulkan.VK10.vkCmdEndRenderPass
import org.lwjgl.vulkan.VK10.vkCmdSetScissor
import org.lwjgl.vulkan.VK10.vkCmdSetViewport
import org.lwjgl.vulkan.VkClearValue
import org.lwjgl.vulkan.VkRect2D
import org.lwjgl.vulkan.VkRenderPassBeginInfo
import org.lwjgl.vulkan.VkViewport
import java.nio.ByteBuffer
import java.nio.LongBuffer

class LightingRenderActivity(
    var swapChain: SwapChain,
    commandPool: CommandPool,
    val pipelineCache: PipelineCache,
    attachments: Array<Attachment>,
    val scene: Scene,
) {

    private val auxVec: Vector4f = Vector4f()
    val device: Device
    private val lightSpecConstants: LightSpecConstants = LightSpecConstants()
    val lightingFrameBuffer: LightingFrameBuffer

    private val attachmentsDescriptorSet: AttachmentsDescriptorSet
    private val attachmentsLayout: AttachmentsLayout
    private val commandBuffers: Array<CommandBuffer>
    private val descriptorPool: DescriptorPool
    private val descriptorSetLayouts: Array<DescriptorSetLayout>
    private val fences: Array<Fence>
    private val invMatricesBuffers: Array<VulkanBuffer>
    private val invMatricesDescriptorSets: Array<UniformDescriptorSet>
    private val lightsBuffers: Array<VulkanBuffer>
    private val lightsDescriptorSets: Array<StorageDescriptorSet>
    private val pipeline: Pipeline
    private val shaderProgram: ShaderProgram
    private val shadowsMatricesBuffers: Array<VulkanBuffer>
    private val shadowsMatricesDescriptorSets: Array<StorageDescriptorSet>
    private val uniformDescriptorSetLayout: UniformDescriptorSetLayout
    private val sceneBuffers: Array<VulkanBuffer>
    private val sceneDescriptorSets: Array<UniformDescriptorSet>
    private val storageDescriptorSetLayout: StorageDescriptorSetLayout

    init {
        device = swapChain.device

        lightingFrameBuffer = LightingFrameBuffer(swapChain)
        val numImages: Int = swapChain.getNumImages()

        shaderProgram = createShaders(device)
        descriptorPool = createDescriptorPool(device, swapChain, attachments)
        createUniforms(device, numImages).also { (
            invMatricesBuffers,
            lightsBuffers,
            sceneBuffers,
            shadowsMatricesBuffers
        ) ->
            this.invMatricesBuffers = invMatricesBuffers
            this.lightsBuffers = lightsBuffers
            this.sceneBuffers = sceneBuffers
            this.shadowsMatricesBuffers = shadowsMatricesBuffers
        }
        createDescriptorSets(
            device = device,
            descriptorPool = descriptorPool,
            lightsBuffers = lightsBuffers,
            attachments = attachments,
            sceneBuffers = sceneBuffers,
            shadowsMatricesBuffers = shadowsMatricesBuffers,
            invMatricesBuffers = invMatricesBuffers,
            numImages = numImages,

        ).also {
                (
                    attachmentsLayout,
                    uniformDescriptorSetLayout,
                    storageDescriptorSetLayout,
                    descriptorSetLayouts,
                    attachmentsDescriptorSet,
                    lightsDescriptorSets,
                    sceneDescriptorSets,
                    invMatricesDescriptorSets,
                    shadowsMatricesDescriptorSets,

                ),
            ->
            this.attachmentsLayout = attachmentsLayout
            this.uniformDescriptorSetLayout = uniformDescriptorSetLayout
            this.storageDescriptorSetLayout = storageDescriptorSetLayout
            this.descriptorSetLayouts = descriptorSetLayouts
            this.attachmentsDescriptorSet = attachmentsDescriptorSet
            this.lightsDescriptorSets = lightsDescriptorSets
            this.sceneDescriptorSets = sceneDescriptorSets
            this.invMatricesDescriptorSets = invMatricesDescriptorSets
            this.shadowsMatricesDescriptorSets = shadowsMatricesDescriptorSets
        }

        pipeline = createPipeline(
            pipelineCache, shaderProgram,
            lightingFrameBuffer,
            descriptorSetLayouts
        )

        createCommandBuffers(device, commandPool, numImages).also { (commandBuffers, fences) ->
            this.commandBuffers = commandBuffers
            this.fences = fences
        }

        repeat(numImages) {
            preRecordCommandBuffer(it)
        }
    }

    fun preRecordCommandBuffer(idx: Int) {
        MemoryStack.stackPush().use { stack ->
            val swapChainExtent = swapChain.swapChainExtent
            val width = swapChainExtent.width()
            val height = swapChainExtent.height()
            val frameBuffer = lightingFrameBuffer.frameBuffers[idx]

            val commandBuffer = commandBuffers[idx]
            commandBuffer.reset()
            val clearValues = VkClearValue.calloc(1, stack)
            clearValues.apply(
                0
            ) {
                it.color()
                    .float32(0, 0.0f)
                    .float32(1, 0.0f)
                    .float32(2, 0.0f)
                    .float32(3, 1f)
            }

            val renderArea = VkRect2D.calloc(stack)
            renderArea.offset()[0] = 0
            renderArea.extent()[width] = height
            val renderPassBeginInfo = VkRenderPassBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .renderPass(lightingFrameBuffer.lightingRenderPass.vkRenderPass)
                .pClearValues(clearValues)
                .framebuffer(frameBuffer.vkFrameBuffer)
                .renderArea(renderArea)
            commandBuffer.beginRecording()

            val cmdHandle = commandBuffer.vkCommandBuffer
            vkCmdBeginRenderPass(
                cmdHandle,
                renderPassBeginInfo,
                VK_SUBPASS_CONTENTS_INLINE
            )

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.vkPipeline)
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

            val descriptorSets: LongBuffer = stack.mallocLong(5)
                .put(0, attachmentsDescriptorSet.vkDescriptorSet)
                .put(1, lightsDescriptorSets[idx].vkDescriptorSet)
                .put(2, sceneDescriptorSets[idx].vkDescriptorSet)
                .put(3, invMatricesDescriptorSets[idx].vkDescriptorSet)
                .put(4, shadowsMatricesDescriptorSets[idx].vkDescriptorSet)

            vkCmdBindDescriptorSets(
                cmdHandle,
                VK_PIPELINE_BIND_POINT_GRAPHICS,
                pipeline.vkPipelineLayout,
                0,
                descriptorSets,
                null
            )
            vkCmdDraw(cmdHandle, 3, 1, 0, 0)
            vkCmdEndRenderPass(cmdHandle)
            commandBuffer.endRecording()
        }
    }

    fun prepareCommandBuffer(cascadeShadows: Array<CascadeShadow>) {
        val idx = swapChain.currentFrame
        val fence = fences[idx]

        fence.fenceWait()
        fence.reset()

        updateLights(
            scene.ambientLight,
            scene.lights,
            scene.camera.viewMatrix,
            lightsBuffers[idx],
            sceneBuffers[idx],
        )
        updateInvMatrices(scene, invMatricesBuffers[idx])
        updateCascadeShadowMatrices(cascadeShadows, shadowsMatricesBuffers[idx])
    }

    fun resize(swapChain: SwapChain, attachments: Array<Attachment>) {
        this.swapChain = swapChain
        attachmentsDescriptorSet.update(attachments)
        lightingFrameBuffer.resize(swapChain)

        val numImages = swapChain.getNumImages()
        repeat(numImages) {
            preRecordCommandBuffer(it)
        }
    }

    fun submit(queue: Queue) {
        MemoryStack.stackPush().use { stack ->
            val idx = swapChain.currentFrame
            val commandBuffer = commandBuffers[idx]
            val currentFence = fences[idx]
            val syncSemaphores: SyncSemaphores = swapChain.syncSemaphoresList[idx]
            queue.submit(
                stack.pointers(commandBuffer.vkCommandBuffer),
                stack.longs(syncSemaphores.geometryCompleteSemaphore.vkSemaphore),
                stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT),
                stack.longs(syncSemaphores.renderCompleteSemaphore.vkSemaphore),
                currentFence
            )
        }
    }

    private fun updateLights(
        ambientLight: Vector4f,
        lights: Array<Light>?,
        viewMatrix: Matrix4f,
        lightsBuffer: VulkanBuffer,
        sceneBuffer: VulkanBuffer,
    ) {
        // Lights
        var mappedMemory = lightsBuffer.map()
        var uniformBuffer = MemoryUtil.memByteBuffer(mappedMemory, lightsBuffer.requestedSize.toInt())

        var offset = 0
        val numLights = lights?.size ?: 0
        for (i in 0..<numLights) {
            val light = lights!!.get(i)
            auxVec.set(light.position)
            auxVec.mul(viewMatrix)
            auxVec.w = light.position.w
            auxVec[offset, uniformBuffer]
            offset += GraphConstants.VEC4_SIZE
            light.color.get(offset, uniformBuffer)
            offset += GraphConstants.VEC4_SIZE
        }
        lightsBuffer.unMap()

        // Scene Uniform
        mappedMemory = sceneBuffer.map()
        uniformBuffer = MemoryUtil.memByteBuffer(mappedMemory, sceneBuffer.requestedSize.toInt())

        ambientLight[0, uniformBuffer]
        offset = GraphConstants.VEC4_SIZE
        uniformBuffer.putInt(offset, numLights)

        sceneBuffer.unMap()
    }

    private fun updateCascadeShadowMatrices(cascadeShadows: Array<CascadeShadow>, shadowsUniformBuffer: VulkanBuffer) {
        val mappedMemory = shadowsUniformBuffer.map()
        val buffer: ByteBuffer = MemoryUtil.memByteBuffer(mappedMemory, shadowsUniformBuffer.requestedSize.toInt())
        var offset = 0
        for (cascadeShadow in cascadeShadows) {
            cascadeShadow.projViewMatrix.get(offset, buffer)
            buffer.putFloat(offset + GraphConstants.MAT4X4_SIZE, cascadeShadow.splitDistance)
            offset += GraphConstants.MAT4X4_SIZE + GraphConstants.VEC4_SIZE
        }
        shadowsUniformBuffer.unMap()
    }

    private fun updateInvMatrices(scene: Scene, invMatricesBuffer: VulkanBuffer) {
        val invProj = Matrix4f(scene.projection.projectionMatrix).invert()
        val invView = Matrix4f(scene.camera.viewMatrix).invert()
        copyMatrixToBuffer(invMatricesBuffer, invProj, 0)
        copyMatrixToBuffer(invMatricesBuffer, invView, GraphConstants.MAT4X4_SIZE)
    }

    fun cleanup() {
        storageDescriptorSetLayout.cleanup()
        uniformDescriptorSetLayout.cleanup()
        attachmentsDescriptorSet.cleanup()
        attachmentsLayout.cleanup()
        descriptorPool.cleanup()
        sceneBuffers.forEach { it.cleanup() }
        lightsBuffers.forEach { it.cleanup() }
        pipeline.cleanup()
        lightSpecConstants.cleanup()
        invMatricesBuffers.forEach { it.cleanup() }
        lightingFrameBuffer.cleanup()
        shadowsMatricesBuffers.forEach { it.cleanup() }
        shaderProgram.cleanup()
        commandBuffers.forEach { it.cleanup() }
        fences.forEach { it.cleanup() }
    }

    companion object {
        private const val LIGHTING_FRAGMENT_SHADER_FILE_GLSL = "resources/shaders/lighting_fragment.glsl"
        private const val LIGHTING_FRAGMENT_SHADER_FILE_SPV = "$LIGHTING_FRAGMENT_SHADER_FILE_GLSL.spv"
        private const val LIGHTING_VERTEX_SHADER_FILE_GLSL = "resources/shaders/lighting_vertex.glsl"
        private const val LIGHTING_VERTEX_SHADER_FILE_SPV = "$LIGHTING_VERTEX_SHADER_FILE_GLSL.spv"

        private fun createShaders(device: Device): ShaderProgram {
            val engineProperties = EngineProperties.getInstance()
            if (engineProperties.shaderRecompilation) {
                compileShaderIfChanged(LIGHTING_VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader)
                compileShaderIfChanged(LIGHTING_FRAGMENT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_fragment_shader)
            }

            return ShaderProgram(
                device,
                arrayOf(
                    ShaderModuleData(VK_SHADER_STAGE_VERTEX_BIT, LIGHTING_VERTEX_SHADER_FILE_SPV),
                    ShaderModuleData(VK_SHADER_STAGE_FRAGMENT_BIT, LIGHTING_FRAGMENT_SHADER_FILE_SPV)
                )
            )
        }

        private fun createDescriptorPool(
            device: Device,
            swapChain: SwapChain,
            attachments: Array<Attachment>,
        ): DescriptorPool {
            return DescriptorPool(
                device,
                arrayOf(
                    DescriptorTypeCount(attachments.size, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER),
                    DescriptorTypeCount(swapChain.getNumImages() * 2, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER),
                    DescriptorTypeCount(swapChain.getNumImages() * 2, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                )
            )
        }

        private data class DescriptorSets(
            val attachmentsLayout: AttachmentsLayout,
            val uniformDescriptorSetLayout: UniformDescriptorSetLayout,
            val storageDescriptorSetLayout: StorageDescriptorSetLayout,
            val descriptorSetLayouts: Array<DescriptorSetLayout>,
            val attachmentsDescriptorSet: AttachmentsDescriptorSet,
            val lightsDescriptorSets: Array<StorageDescriptorSet>,
            val sceneDescriptorSets: Array<UniformDescriptorSet>,
            val invMatricesDescriptorSets: Array<UniformDescriptorSet>,
            val shadowsMatricesDescriptorSets: Array<StorageDescriptorSet>,
        )
        private fun createDescriptorSets(
            device: Device,
            descriptorPool: DescriptorPool,
            lightsBuffers: Array<VulkanBuffer>,
            attachments: Array<Attachment>,
            sceneBuffers: Array<VulkanBuffer>,
            shadowsMatricesBuffers: Array<VulkanBuffer>,
            invMatricesBuffers: Array<VulkanBuffer>,
            numImages: Int,
        ): DescriptorSets {
            val attachmentsLayout = AttachmentsLayout(device, attachments.size)
            val uniformDescriptorSetLayout = UniformDescriptorSetLayout(device, 0, VK_SHADER_STAGE_FRAGMENT_BIT)
            val storageDescriptorSetLayout = StorageDescriptorSetLayout(device, 0, VK_SHADER_STAGE_FRAGMENT_BIT)
            val descriptorSetLayouts = arrayOf(
                attachmentsLayout,
                storageDescriptorSetLayout,
                uniformDescriptorSetLayout,
                uniformDescriptorSetLayout,
                storageDescriptorSetLayout,
            )

            val attachmentsDescriptorSet = AttachmentsDescriptorSet(
                descriptorPool,
                attachmentsLayout,
                attachments,
                0
            )

            val lightsDescriptorSets = Array(numImages) {
                StorageDescriptorSet(
                    descriptorPool,
                    storageDescriptorSetLayout,
                    lightsBuffers[it],
                    0
                )
            }

            val sceneDescriptorSets = Array(numImages) {
                UniformDescriptorSet(
                    descriptorPool,
                    uniformDescriptorSetLayout,
                    sceneBuffers[it],
                    0
                )
            }

            val invMatricesDescriptorSets = Array(numImages) {
                UniformDescriptorSet(
                    descriptorPool,
                    uniformDescriptorSetLayout,
                    invMatricesBuffers.get(it),
                    0
                )
            }

            val shadowsMatricesDescriptorSets = Array(numImages) {
                StorageDescriptorSet(
                    descriptorPool,
                    storageDescriptorSetLayout,
                    shadowsMatricesBuffers[it],
                    0
                )
            }

            return DescriptorSets(
                attachmentsLayout,
                uniformDescriptorSetLayout,
                storageDescriptorSetLayout,
                descriptorSetLayouts,
                attachmentsDescriptorSet,
                lightsDescriptorSets,
                sceneDescriptorSets,
                invMatricesDescriptorSets,
                shadowsMatricesDescriptorSets
            )
        }

        private fun createPipeline(
            pipelineCache: PipelineCache,
            shaderProgram: ShaderProgram,
            lightingFrameBuffer: LightingFrameBuffer,
            descriptorSetLayouts: Array<DescriptorSetLayout>,
        ): Pipeline {
            val pipeLineCreationInfo = PipeLineCreationInfo(
                lightingFrameBuffer.lightingRenderPass.vkRenderPass,
                shaderProgram,
                1,
                hasDepthAttachment = false,
                useBlend = false,
                pushConstantsSize = 0,
                viInputStateInfo = EmptyVertexBufferStructure(),
                descriptorSetLayouts = descriptorSetLayouts
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
                CommandBuffer(commandPool, true, false)
            }
            val fences = Array(numImages) {
                Fence(device, true)
            }

            return Pair(commandBuffers, fences)
        }

        data class CreateUniforms(
            val invMatricesBuffers: Array<VulkanBuffer>,
            val lightsBuffers: Array<VulkanBuffer>,
            val sceneBuffers: Array<VulkanBuffer>,
            val shadowsMatricesBuffers: Array<VulkanBuffer>,
        )

        private fun createUniforms(
            device: Device,
            numImages: Int,
        ): CreateUniforms {
            val invMatricesBuffers = Array(numImages) {
                VulkanBuffer(
                    device,
                    GraphConstants.MAT4X4_SIZE.toLong() * 2,
                    VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                    0
                )
            }

            val lightsBuffers = Array(numImages) {
                VulkanBuffer(
                    device,
                    GraphConstants.INT_LENGTH.toLong() * 4 + GraphConstants.VEC4_SIZE * 2 * GraphConstants.MAX_LIGHTS +
                        GraphConstants.VEC4_SIZE,
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                    0
                )
            }

            val sceneBuffers = Array(numImages) {
                VulkanBuffer(
                    device,
                    GraphConstants.VEC4_SIZE.toLong() * 2,
                    VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                    0
                )
            }

            val shadowsMatricesBuffers = Array(numImages) {
                VulkanBuffer(
                    device,
                    ((GraphConstants.MAT4X4_SIZE + GraphConstants.VEC4_SIZE) * GraphConstants.SHADOW_MAP_CASCADE_COUNT).toLong(),
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                    0
                )
            }

            return CreateUniforms(invMatricesBuffers, lightsBuffers, sceneBuffers, shadowsMatricesBuffers)
        }
    }
}
