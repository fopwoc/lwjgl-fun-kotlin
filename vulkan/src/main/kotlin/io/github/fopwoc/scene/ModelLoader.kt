package io.github.fopwoc.scene

import io.github.fopwoc.model.Material
import io.github.fopwoc.model.MeshData
import io.github.fopwoc.model.ModelData
import java.io.File
import java.nio.IntBuffer
import java.util.Collections
import org.joml.Vector4f
import org.lwjgl.assimp.AIColor4D
import org.lwjgl.assimp.AIMaterial
import org.lwjgl.assimp.AIMesh
import org.lwjgl.assimp.AIScene
import org.lwjgl.assimp.AIString
import org.lwjgl.assimp.Assimp
import org.lwjgl.assimp.Assimp.AI_MATKEY_COLOR_DIFFUSE
import org.lwjgl.assimp.Assimp.aiGetMaterialColor
import org.lwjgl.assimp.Assimp.aiGetMaterialTexture
import org.lwjgl.assimp.Assimp.aiImportFile
import org.lwjgl.assimp.Assimp.aiProcess_CalcTangentSpace
import org.lwjgl.assimp.Assimp.aiProcess_FixInfacingNormals
import org.lwjgl.assimp.Assimp.aiProcess_GenSmoothNormals
import org.lwjgl.assimp.Assimp.aiProcess_JoinIdenticalVertices
import org.lwjgl.assimp.Assimp.aiProcess_PreTransformVertices
import org.lwjgl.assimp.Assimp.aiProcess_Triangulate
import org.lwjgl.assimp.Assimp.aiReleaseImport
import org.lwjgl.assimp.Assimp.aiReturn_SUCCESS
import org.lwjgl.assimp.Assimp.aiTextureType_DIFFUSE
import org.lwjgl.assimp.Assimp.aiTextureType_NONE
import org.lwjgl.system.MemoryStack
import org.pmw.tinylog.Logger


object ModelLoader {

    fun loadModel(
        modelId: String,
        modelPath: String,
        texturesDir: String,
        flags: Int = aiProcess_GenSmoothNormals or aiProcess_JoinIdenticalVertices or
            aiProcess_Triangulate or aiProcess_FixInfacingNormals or aiProcess_CalcTangentSpace or
            aiProcess_PreTransformVertices
    ): ModelData {
        Logger.debug("Loading model data [{}]", modelPath)
        if (!File(modelPath).exists()) {
            throw RuntimeException("Model path does not exist [${File(modelPath).absolutePath}]")
        }
        if (!File(texturesDir).exists()) {
            throw RuntimeException("Textures path does not exist [$texturesDir]")
        }

        val aiScene: AIScene = aiImportFile(modelPath, flags)
            ?: throw RuntimeException("Error loading model [modelPath: $modelPath, texturesDir:$texturesDir]")

        val numMaterials = aiScene.mNumMaterials()
        val materialList = Array(numMaterials) {
            processMaterial(AIMaterial.create(aiScene.mMaterials()!![it]), texturesDir)
        }

        val numMeshes = aiScene.mNumMeshes()
        val aiMeshes = aiScene.mMeshes()

        val meshDataList = Array(numMeshes) {
            processMesh(AIMesh.create(aiMeshes!![it]))
        }

        val modelData = ModelData(modelId, meshDataList, materialList)

        aiReleaseImport(aiScene)
        Logger.debug("Loaded model [{}]", modelPath)
        return modelData
    }

    private fun processMaterial(aiMaterial: AIMaterial, texturesDir: String): Material {
        MemoryStack.stackPush().use { stack ->
            val colour = AIColor4D.create()

            var diffuse: Vector4f = Material.DEFAULT_COLOR
            var result = aiGetMaterialColor(
                aiMaterial,
                AI_MATKEY_COLOR_DIFFUSE,
                aiTextureType_NONE,
                0,
                colour
            )
            if (result == aiReturn_SUCCESS) {
                diffuse = Vector4f(colour.r(), colour.g(), colour.b(), colour.a())
            }
            val aiTexturePath = AIString.calloc(stack)
            aiGetMaterialTexture(
                aiMaterial, aiTextureType_DIFFUSE, 0, aiTexturePath, null as IntBuffer?,
                null, null, null, null, null
            )
            var texturePath = aiTexturePath.dataString()
            if (texturePath.isNotEmpty()) {
                texturePath = texturesDir + File.separator + File(texturePath).name
                diffuse = Vector4f(0.0f, 0.0f, 0.0f, 0.0f)
            }

            val aiNormalMapPath = AIString.calloc(stack)
            aiGetMaterialTexture(
                aiMaterial, Assimp.aiTextureType_NORMALS, 0, aiNormalMapPath, null as IntBuffer?,
                null, null, null, null, null
            )
            var normalMapPath = aiNormalMapPath.dataString()
            if (normalMapPath.isNotEmpty()) {
                normalMapPath = texturesDir + File.separator + File(normalMapPath).name
            }

            val aiMetallicRoughnessPath = AIString.calloc(stack)
            aiGetMaterialTexture(
                aiMaterial,
                Assimp.AI_MATKEY_GLTF_PBRMETALLICROUGHNESS_METALLICROUGHNESS_TEXTURE,
                0,
                aiMetallicRoughnessPath,
                null as IntBuffer?,
                null,
                null,
                null,
                null,
                null
            )
            var metallicRoughnessPath = aiMetallicRoughnessPath.dataString()
            if (metallicRoughnessPath.isNotEmpty()) {
                metallicRoughnessPath = texturesDir + File.separator + File(metallicRoughnessPath).name
            }

            val metallicArr = floatArrayOf(0.0f)
            val pMax = intArrayOf(1)
            result = Assimp.aiGetMaterialFloatArray(
                aiMaterial,
                Assimp.AI_MATKEY_METALLIC_FACTOR,
                aiTextureType_NONE,
                0,
                metallicArr,
                pMax
            )
            if (result != aiReturn_SUCCESS) {
                metallicArr[0] = 1.0f
            }

            val roughnessArr = floatArrayOf(0.0f)
            result = Assimp.aiGetMaterialFloatArray(
                aiMaterial,
                Assimp.AI_MATKEY_ROUGHNESS_FACTOR,
                aiTextureType_NONE,
                0,
                roughnessArr,
                pMax
            )
            if (result != aiReturn_SUCCESS) {
                roughnessArr[0] = 1.0f
            }

            return Material(
                texturePath,
                normalMapPath,
                metallicRoughnessPath,
                diffuse,
                roughnessArr[0],
                metallicArr[0]
            )
        }
    }

    private fun processMesh(aiMesh: AIMesh): MeshData {
        val vertices: List<Float> = processVertices(aiMesh)
        val textCoords: MutableList<Float> = processTextCoords(aiMesh).toMutableList()
        val indices: List<Int> = processIndices(aiMesh)
        val normals: List<Float> = processNormals(aiMesh)
        val tangents: List<Float> = processTangents(aiMesh, normals)
        val biTangents: List<Float> = processBitangents(aiMesh, normals)

        // Texture coordinates may not have been populated. We need at least the empty slots
        if (textCoords.isEmpty()) {
            val numElements = vertices.size / 3 * 2
            repeat(numElements) {
                textCoords.add(0.0f)
            }
        }
        val materialIdx = aiMesh.mMaterialIndex()
        return MeshData(
            vertices.toFloatArray(),
            normals.toFloatArray(),
            tangents.toFloatArray(),
            biTangents.toFloatArray(),
            textCoords.toFloatArray(),
            indices.toIntArray(),
            materialIdx
        )
    }

    private fun processIndices(aiMesh: AIMesh): List<Int> {
        val indices = mutableListOf<Int>()

        val numFaces = aiMesh.mNumFaces()
        val aiFaces = aiMesh.mFaces()

        repeat(numFaces) {
            val aiFace = aiFaces[it]
            val buffer = aiFace.mIndices()
            while (buffer.remaining() > 0) {
                indices.add(buffer.get())
            }
        }

        return indices
    }

    private fun processBitangents(aiMesh: AIMesh, normals: List<Float>): List<Float> {
        var biTangents: MutableList<Float> = mutableListOf()
        val aiBitangents = aiMesh.mBitangents()
        while (aiBitangents != null && aiBitangents.remaining() > 0) {
            val aiBitangent = aiBitangents.get()
            biTangents.add(aiBitangent.x())
            biTangents.add(aiBitangent.y())
            biTangents.add(aiBitangent.z())
        }

        // Assimp may not calculate tangents with models that do not have texture coordinates. Just create empty values
        if (biTangents.isEmpty()) {
            biTangents = ArrayList(Collections.nCopies(normals.size, 0.0f))
        }
        return biTangents
    }

    private fun processNormals(aiMesh: AIMesh): List<Float> {
        val normals: MutableList<Float> = mutableListOf()
        val aiNormals = aiMesh.mNormals()
        while (aiNormals != null && aiNormals.remaining() > 0) {
            val aiNormal = aiNormals.get()
            normals.add(aiNormal.x())
            normals.add(aiNormal.y())
            normals.add(aiNormal.z())
        }
        return normals
    }

    private fun processTangents(aiMesh: AIMesh, normals: List<Float>): List<Float> {
        var tangents: MutableList<Float> = mutableListOf()
        val aiTangents = aiMesh.mTangents()
        while (aiTangents != null && aiTangents.remaining() > 0) {
            val aiTangent = aiTangents.get()
            tangents.add(aiTangent.x())
            tangents.add(aiTangent.y())
            tangents.add(aiTangent.z())
        }

        // Assimp may not calculate tangents with models that do not have texture coordinates. Just create empty values
        if (tangents.isEmpty()) {
            tangents = ArrayList(Collections.nCopies(normals.size, 0.0f))
        }
        return tangents
    }

    private fun processTextCoords(aiMesh: AIMesh): List<Float> {
        val textCoords = mutableListOf<Float>()

        val aiTextCoords = aiMesh.mTextureCoords(0)
        val numTextCords = aiTextCoords?.remaining() ?: 0

        repeat(numTextCords) {
            val textCoord = aiTextCoords!!.get()

            textCoords.add(textCoord.x())
            textCoords.add(1 - textCoord.y())
        }

        return textCoords
    }

    private fun processVertices(aiMesh: AIMesh): List<Float> {
        val vertices = mutableListOf<Float>()
        val aiVertices = aiMesh.mVertices()

        while (aiVertices.remaining() > 0) {
            val aiVertex = aiVertices.get()

            vertices.add(aiVertex.x())
            vertices.add(aiVertex.y())
            vertices.add(aiVertex.z())
        }

        return vertices
    }
}
