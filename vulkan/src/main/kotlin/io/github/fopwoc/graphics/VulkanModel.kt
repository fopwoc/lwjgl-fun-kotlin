package io.github.fopwoc.graphics

import io.github.fopwoc.graphics.vulkan.CommandBuffer
import io.github.fopwoc.graphics.vulkan.CommandPool
import io.github.fopwoc.graphics.vulkan.Device
import io.github.fopwoc.graphics.vulkan.Fence
import io.github.fopwoc.graphics.vulkan.GraphConstants
import io.github.fopwoc.graphics.vulkan.Queue
import io.github.fopwoc.graphics.vulkan.Texture
import io.github.fopwoc.graphics.vulkan.TextureCache
import io.github.fopwoc.graphics.vulkan.VulkanBuffer
import io.github.fopwoc.model.Material
import io.github.fopwoc.model.MeshData
import io.github.fopwoc.model.ModelData
import io.github.fopwoc.model.TransferBuffers
import io.github.fopwoc.model.VulkanMaterial
import io.github.fopwoc.model.VulkanMesh
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT
import org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
import org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT
import org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT
import org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT
import org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_SRGB
import org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM
import org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
import org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
import org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
import org.lwjgl.vulkan.VK10.vkCmdCopyBuffer
import org.lwjgl.vulkan.VkBufferCopy


class VulkanModel(val modelId: String) {
    val vulkanMaterialList: MutableList<VulkanMaterial> = mutableListOf()

    fun cleanup() {
        vulkanMaterialList.forEach { material ->
            material.vulkanMeshList.forEach { it.cleanup() }
        }
    }

    companion object {
        fun transformModels(
            modelDataList: List<ModelData>,
            textureCache: TextureCache,
            commandPool: CommandPool,
            queue: Queue
        ): List<VulkanModel> {
            val vulkanModelList = mutableListOf<VulkanModel>()
            val device = commandPool.device
            val cmd = CommandBuffer(
                commandPool,
                primary = true,
                oneTimeSubmit = true
            )
            val stagingBufferList = mutableListOf<VulkanBuffer>()
            val textureList: MutableList<Texture> = mutableListOf()

            cmd.beginRecording()

            for ((modelId, meshDataList, materialList) in modelDataList) {
                val vulkanModel = VulkanModel(modelId)
                vulkanModelList.add(vulkanModel)

                // Create textures defined for the materials
                var defaultVulkanMaterial: VulkanMaterial? = null
                for (material in materialList) {
                    val vulkanMaterial = transformMaterial(material, device, textureCache, cmd, textureList)
                    vulkanModel.vulkanMaterialList.add(vulkanMaterial)
                }

                // Transform meshes loading their data into GPU buffers
                for (meshData in meshDataList) {
                    val verticesBuffers = createVerticesBuffers(device, meshData)
                    val indicesBuffers = createIndicesBuffers(device, meshData)
                    stagingBufferList.add(verticesBuffers.srcBuffer)
                    stagingBufferList.add(indicesBuffers.srcBuffer)
                    recordTransferCommand(cmd, verticesBuffers)
                    recordTransferCommand(cmd, indicesBuffers)

                    val vulkanMesh = VulkanMesh(
                        verticesBuffers.dstBuffer,
                        indicesBuffers.dstBuffer,
                        meshData.indices.size
                    )

                    var vulkanMaterial: VulkanMaterial
                    val materialIdx: Int = meshData.materialIdx
                    if (materialIdx >= 0 && materialIdx < vulkanModel.vulkanMaterialList.size) {
                        vulkanMaterial = vulkanModel.vulkanMaterialList[materialIdx]
                    } else {
                        if (defaultVulkanMaterial == null) {
                            defaultVulkanMaterial =
                                transformMaterial(Material(), device, textureCache, cmd, textureList)
                        }
                        vulkanMaterial = defaultVulkanMaterial
                    }
                    vulkanMaterial.vulkanMeshList.add(vulkanMesh)
                }
            }

            cmd.endRecording()
            val fence = Fence(device, true)
            fence.reset()
            MemoryStack.stackPush().use { stack ->
                queue.submit(
                    stack.pointers(cmd.vkCommandBuffer),
                    null,
                    null,
                    null,
                    fence
                )
            }
            fence.fenceWait()
            fence.cleanup()
            cmd.cleanup()

            stagingBufferList.forEach { it.cleanup() }
            textureList.forEach { it.cleanupStgBuffer() }

            return vulkanModelList
        }

        private fun createVerticesBuffers(device: Device, meshData: MeshData): TransferBuffers {
            val positions: FloatArray = meshData.positions
            val normals: FloatArray = meshData.normals
            val tangents: FloatArray = meshData.tangents
            val biTangents: FloatArray = meshData.biTangents

            var textCoords: FloatArray = meshData.textCoords
            if (textCoords.isEmpty()) {
                textCoords = FloatArray(positions.size / 3 * 2)
            }

            val numElements = positions.size + normals.size + tangents.size + biTangents.size + textCoords.size
            val bufferSize = numElements * GraphConstants.FLOAT_LENGTH
            val srcBuffer = VulkanBuffer(
                device,
                bufferSize.toLong(),
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            )
            val dstBuffer = VulkanBuffer(
                device, bufferSize.toLong(),
                VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK_BUFFER_USAGE_VERTEX_BUFFER_BIT or VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0
            )


            val mappedMemory = srcBuffer.map()
            val data = MemoryUtil.memFloatBuffer(mappedMemory, srcBuffer.requestedSize.toInt())

            val rows = positions.size / 3
            repeat(rows) {
                val startPos = it * 3
                val startTextCoord = it * 2
                data.put(positions[startPos])
                data.put(positions[startPos + 1])
                data.put(positions[startPos + 2])
                data.put(normals[startPos])
                data.put(normals[startPos + 1])
                data.put(normals[startPos + 2])
                data.put(tangents[startPos])
                data.put(tangents[startPos + 1])
                data.put(tangents[startPos + 2])
                data.put(biTangents[startPos])
                data.put(biTangents[startPos + 1])
                data.put(biTangents[startPos + 2])
                data.put(textCoords[startTextCoord])
                data.put(textCoords[startTextCoord + 1])
            }

            srcBuffer.unMap()
            return TransferBuffers(srcBuffer, dstBuffer)
        }

        private fun createIndicesBuffers(device: Device, meshData: MeshData): TransferBuffers {
            val indices: IntArray = meshData.indices
            val numIndices = indices.size
            val bufferSize = numIndices * GraphConstants.INT_LENGTH
            val srcBuffer = VulkanBuffer(
                device,
                bufferSize.toLong(),
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            )
            val dstBuffer = VulkanBuffer(
                device,
                bufferSize.toLong(),
                VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                0
            )

            val mappedMemory = srcBuffer.map()
            val data = MemoryUtil.memIntBuffer(mappedMemory, srcBuffer.requestedSize.toInt())
            data.put(indices)
            srcBuffer.unMap()
            return TransferBuffers(srcBuffer, dstBuffer)
        }

        private fun recordTransferCommand(cmd: CommandBuffer, transferBuffers: TransferBuffers) {
            MemoryStack.stackPush().use { stack ->
                val copyRegion = VkBufferCopy.calloc(1, stack)
                    .srcOffset(0)
                    .dstOffset(0)
                    .size(transferBuffers.srcBuffer.requestedSize)

                vkCmdCopyBuffer(
                    cmd.vkCommandBuffer,
                    transferBuffers.srcBuffer.buffer,
                    transferBuffers.dstBuffer.buffer,
                    copyRegion
                )
            }
        }

        private fun transformMaterial(
            material: Material,
            device: Device,
            textureCache: TextureCache,
            cmd: CommandBuffer,
            textureList: MutableList<Texture>
        ): VulkanMaterial {
            val texture = textureCache.createTexture(device, material.texturePath, VK_FORMAT_R8G8B8A8_SRGB)
            val hasTexture = material.texturePath!!.trim().isNotEmpty()
            val normalMapTexture = textureCache.createTexture(device, material.normalMapPath, VK_FORMAT_R8G8B8A8_UNORM)
            val hasNormalMapTexture = material.normalMapPath!!.trim().isNotEmpty()
            val metalRoughTexture = textureCache.createTexture(device, material.metalRoughMap, VK_FORMAT_R8G8B8A8_UNORM)
            val hasMetalRoughTexture = material.metalRoughMap!!.trim().isNotEmpty()

            texture.recordTextureTransition(cmd)
            textureList.add(texture)
            normalMapTexture.recordTextureTransition(cmd)
            textureList.add(normalMapTexture)
            metalRoughTexture.recordTextureTransition(cmd)
            textureList.add(metalRoughTexture)

            return VulkanMaterial(
                material.diffuseColor,
                texture,
                hasTexture,
                normalMapTexture,
                hasNormalMapTexture,
                metalRoughTexture,
                hasMetalRoughTexture,
                material.metallicFactor,
                material.roughnessFactor,
                mutableListOf()
            )
        }
    }
}
