package io.github.fopwoc.core

import java.io.IOException
import java.util.Properties
import org.pmw.tinylog.Logger

class EngineProperties private constructor() {

    val physDeviceName: String
    val ups: Int
    val validate: Boolean
    val requestedImages: Int
    val vSync: Boolean
    val shaderRecompilation: Boolean
    val fov: Float
    val zNear: Float
    val zFar: Float
    val defaultTexturePath: String
    val maxMaterials: Int

    val shadowBias: Float
    val shadowDebug: Boolean
    val shadowMapSize: Int
    val shadowPcf: Boolean

    init {
        val props = Properties()
        try {
            EngineProperties::class.java.getResourceAsStream("/$FILENAME").use { stream ->
                props.load(stream)
                ups = props.getOrDefault("ups", DEFAULT_UPS).toString().toInt()
                validate = props.getOrDefault("vkValidate", false).toString().toBoolean()
                physDeviceName = props.getProperty("physDeviceName")
                requestedImages = props.getOrDefault("requestedImages", DEFAULT_REQUESTED_IMAGES).toString().toInt()
                vSync = props.getOrDefault("vsync", true).toString().toBoolean()
                fov = Math.toRadians(
                    props.getOrDefault("fov", DEFAULT_FOV).toString().toFloat()
                        .toDouble()
                ).toFloat()
                zNear = props.getOrDefault("zNear", DEFAULT_Z_NEAR).toString().toFloat()
                zFar = props.getOrDefault("zFar", DEFAULT_Z_FAR).toString().toFloat()
                shaderRecompilation = props.getOrDefault("shaderRecompilation", false).toString().toBoolean()
                defaultTexturePath = props.getProperty("defaultTexturePath")
                maxMaterials = Integer.parseInt(props.getOrDefault("maxMaterials", DEFAULT_MAX_MATERIALS).toString())
                shadowPcf = props.getOrDefault("shadowPcf", false).toString().toBoolean()
                shadowBias = props.getOrDefault("shadowBias", DEFAULT_SHADOW_BIAS).toString().toFloat()
                shadowMapSize = props.getOrDefault("shadowMapSize", DEFAULT_SHADOW_MAP_SIZE).toString().toInt()
                shadowDebug = props.getOrDefault("shadowDebug", false).toString().toBoolean()
            }
        } catch (excp: IOException) {
            Logger.error("Could not read [{}] properties file", FILENAME, excp)
            throw excp
        }
    }

    companion object {
        private const val DEFAULT_REQUESTED_IMAGES = 3
        private const val DEFAULT_UPS = 30
        private const val FILENAME = "engine.properties"

        private const val DEFAULT_FOV = 60.0f
        private const val DEFAULT_Z_FAR = 100.0f
        private const val DEFAULT_Z_NEAR = 1.0f

        private const val DEFAULT_MAX_MATERIALS = 500

        private const val DEFAULT_SHADOW_BIAS = 0.00005f
        private const val DEFAULT_SHADOW_MAP_SIZE = 2048

        @Volatile
        private var instance: EngineProperties? = null
        fun getInstance(): EngineProperties {
            return instance ?: synchronized(this) {
                instance ?: EngineProperties().also {
                    instance = it
                }
            }
        }
    }
}
