package io.github.fopwoc.graphics

import io.github.fopwoc.core.EngineProperties
import io.github.fopwoc.core.Window
import io.github.fopwoc.graphics.geometry.GeometryRenderActivity
import io.github.fopwoc.graphics.lightning.LightingRenderActivity
import io.github.fopwoc.graphics.shadows.ShadowRenderActivity
import io.github.fopwoc.graphics.vulkan.Attachment
import io.github.fopwoc.graphics.vulkan.CommandPool
import io.github.fopwoc.graphics.vulkan.Device
import io.github.fopwoc.graphics.vulkan.PhysicalDevice
import io.github.fopwoc.graphics.vulkan.PipelineCache
import io.github.fopwoc.graphics.vulkan.Queue
import io.github.fopwoc.graphics.vulkan.Surface
import io.github.fopwoc.graphics.vulkan.SwapChain
import io.github.fopwoc.graphics.vulkan.TextureCache
import io.github.fopwoc.graphics.vulkan.VulkanInstance
import io.github.fopwoc.model.ModelData
import io.github.fopwoc.scene.Scene
import org.pmw.tinylog.Logger

class Render(window: Window, scene: Scene) {

    private val instance: VulkanInstance
    private val device: Device
    private val graphQueue: Queue.GraphicsQueue
    private val physicalDevice: PhysicalDevice
    private val pipelineCache: PipelineCache
    private val surface: Surface
    private var swapChain: SwapChain
    private val commandPool: CommandPool
    private val presentQueue: Queue.PresentQueue
    private val geometryRenderActivity: GeometryRenderActivity
    private var lightingRenderActivity: LightingRenderActivity
    private val shadowRenderActivity: ShadowRenderActivity
    private val vulkanModels: MutableList<VulkanModel>
    private val textureCache: TextureCache = TextureCache()

    init {
        val engProps = EngineProperties.getInstance()
        instance = VulkanInstance(engProps.validate)
        physicalDevice = PhysicalDevice.createPhysicalDevice(instance, engProps.physDeviceName)
        device = Device(instance, physicalDevice)
        surface = Surface(physicalDevice, window.windowHandle)
        graphQueue = Queue.GraphicsQueue(device, 0)
        presentQueue = Queue.PresentQueue(device, surface, 0)
        swapChain = SwapChain(
            device, surface, window, engProps.requestedImages,
            engProps.vSync, presentQueue, arrayOf(graphQueue)
        )
        commandPool = CommandPool(device, graphQueue.queueFamilyIndex)
        pipelineCache = PipelineCache(device)
        geometryRenderActivity = GeometryRenderActivity(swapChain, commandPool, pipelineCache, scene)

        shadowRenderActivity = ShadowRenderActivity(swapChain, pipelineCache, scene)
        val attachments: MutableList<Attachment> = ArrayList()
        attachments.addAll(geometryRenderActivity.getAttachments())
        attachments.add(shadowRenderActivity.getDepthAttachment())
        lightingRenderActivity = LightingRenderActivity(
            swapChain, commandPool, pipelineCache,
            geometryRenderActivity.getAttachments(), scene
        )
        vulkanModels = ArrayList()
        lightingRenderActivity = LightingRenderActivity(
            swapChain, commandPool, pipelineCache,
            geometryRenderActivity.getAttachments(), scene
        )
    }

    fun loadModels(modelDataList: List<ModelData>) {
        Logger.debug("Loading {} model(s)", modelDataList.size)
        vulkanModels.addAll(VulkanModel.transformModels(modelDataList, textureCache, commandPool, graphQueue))
        Logger.debug("Loaded {} model(s)", modelDataList.size)

        geometryRenderActivity.registerModels(vulkanModels)
        shadowRenderActivity.registerModels(vulkanModels)
    }

    fun render(window: Window, scene: Scene) {
        if (window.width <= 0 && window.height <= 0) {
            return
        }

        if (window.resized || swapChain.acquireNextImage()) {
            window.resetResized()
            resize(window)
            scene.projection.resize(window.width, window.height)
            swapChain.acquireNextImage()
        }

        val commandBuffer = geometryRenderActivity.beginRecording()
        geometryRenderActivity.recordCommandBuffer(commandBuffer, vulkanModels)
        shadowRenderActivity.recordCommandBuffer(commandBuffer, vulkanModels)
        geometryRenderActivity.endRecording(commandBuffer)
        geometryRenderActivity.submit(graphQueue)
        lightingRenderActivity.prepareCommandBuffer(shadowRenderActivity.cascadeShadows)
        lightingRenderActivity.submit(graphQueue)

        if (swapChain.presentImage(presentQueue)) {
            window.setResized(true)
        }
    }

    private fun resize(window: Window) {
        val engProps = EngineProperties.getInstance()

        device.waitIdle()
        graphQueue.waitIdle()

        swapChain.cleanup()

        swapChain = SwapChain(
            device, surface, window, engProps.requestedImages, engProps.vSync,
            presentQueue, arrayOf(graphQueue)
        )
        geometryRenderActivity.resize(swapChain)
        shadowRenderActivity.resize(swapChain)
        lightingRenderActivity.resize(
            swapChain,
            arrayOf(
                *geometryRenderActivity.getAttachments(),
                shadowRenderActivity.getDepthAttachment()
            )
        )
    }

    fun cleanup() {
        textureCache.cleanup()
        vulkanModels.forEach { it.cleanup() }

        presentQueue.waitIdle()
        graphQueue.waitIdle()
        device.waitIdle()
        shadowRenderActivity.cleanup()
        lightingRenderActivity.cleanup()
        geometryRenderActivity.cleanup()
        commandPool.cleanup()
        swapChain.cleanup()
        surface.cleanup()
        device.cleanup()
        physicalDevice.cleanup()
        instance.cleanup()
    }
}
