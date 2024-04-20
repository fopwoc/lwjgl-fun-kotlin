package io.github.fopwoc.graphics.vulkan

import io.github.fopwoc.core.EngineProperties
import io.github.fopwoc.graphics.IndexedLinkedHashMap


class TextureCache {

    private val textureMap: IndexedLinkedHashMap<String, Texture> = IndexedLinkedHashMap()

    fun createTexture(device: Device?, texturePath: String?, format: Int): Texture {
        var path = texturePath
        if (texturePath == null || texturePath.trim().isEmpty()) {
            val engProperties = EngineProperties.getInstance()
            path = engProperties.defaultTexturePath
        }
        var texture = textureMap[path!!]
        if (texture == null) {
            texture = Texture(device!!, path, format)
            textureMap[path] = texture
        }
        return texture
    }


    fun getAsList(): List<Texture> {
        return textureMap.values.toList()
    }




    fun getPosition(texturePath: String): Int {
        var result = -1
        result = textureMap.getIndexOf(texturePath)

        return result
    }


    fun getTexture(texturePath: String): Texture? {
        return textureMap.get(texturePath.trim())
    }

    fun cleanup() {
        textureMap.forEach { it.value.cleanup() }
        textureMap.clear()
    }
}


