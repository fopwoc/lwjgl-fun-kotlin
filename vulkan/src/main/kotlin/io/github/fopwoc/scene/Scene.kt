package io.github.fopwoc.scene

import io.github.fopwoc.core.Window
import io.github.fopwoc.graphics.vulkan.GraphConstants
import java.util.Arrays
import org.joml.Vector4f


class Scene(window: Window) {
    val camera: Camera
    private var entitiesMap: HashMap<String, MutableList<Entity>> = hashMapOf()
    var projection: Projection = Projection()
    val ambientLight: Vector4f
    var lights: Array<Light> = emptyArray()
        private set

    var directionalLight: Light? = null
        private set
    var lightChanged = false
        private set

    init {
        projection.resize(window.width, window.height)
        camera = Camera()
        ambientLight = Vector4f()
    }

    fun addEntity(entity: Entity) {
        var entities = entitiesMap[entity.modelId]
        if (entities == null) {
            entities = ArrayList()
            entitiesMap[entity.modelId] = entities
        }
        entities.add(entity)
    }

    fun getEntitiesByModelId(modelId: String?): List<Entity>? {
        return entitiesMap[modelId]
    }

    fun removeEntity(entity: Entity) {
        val entities = entitiesMap[entity.modelId]
        entities?.removeIf { e: Entity -> e.id == entity.id }
    }

    fun removeAllEntities() {
        entitiesMap.clear()
    }


    fun setLightChanged(lightChanged: Boolean) {
        this.lightChanged = lightChanged
    }

    fun setLights(lights: Array<Light>) {
        directionalLight = null
        val numLights = lights.size
        if (numLights > GraphConstants.MAX_LIGHTS) {
            throw java.lang.RuntimeException("Maximum number of lights set to: " + GraphConstants.MAX_LIGHTS)
        }
        this.lights = lights
        val option = Arrays.stream(lights).filter { l: Light -> l.position.w == 0f }
            .findFirst()
        if (option.isPresent) {
            directionalLight = option.get()
        }
        lightChanged = true
    }
}
