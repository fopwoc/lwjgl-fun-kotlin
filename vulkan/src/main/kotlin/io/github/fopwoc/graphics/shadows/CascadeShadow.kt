package io.github.fopwoc.graphics.shadows

import io.github.fopwoc.graphics.vulkan.GraphConstants
import io.github.fopwoc.scene.Scene
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f

class CascadeShadow {
    var projViewMatrix: Matrix4f = Matrix4f()
    var splitDistance: Float = 0F

    companion object {
        fun updateCascadeShadows(cascadeShadows: Array<CascadeShadow>, scene: Scene) {
            val viewMatrix: Matrix4f = scene.camera.viewMatrix
            val projMatrix: Matrix4f = scene.projection.projectionMatrix
            val lightPos: Vector4f = scene.directionalLight!!.position

            val cascadeSplitLambda = 0.95f

            val cascadeSplits = FloatArray(GraphConstants.SHADOW_MAP_CASCADE_COUNT)

            val nearClip = projMatrix.perspectiveNear()
            val farClip = projMatrix.perspectiveFar()
            val clipRange = farClip - nearClip

            val minZ = nearClip
            val maxZ = nearClip + clipRange

            val range = maxZ - nearClip
            val ratio = maxZ / nearClip

            // Calculate split depths based on view camera frustum
            // Based on method presented in https://developer.nvidia.com/gpugems/GPUGems3/gpugems3_ch10.html
            for (i in 0..<GraphConstants.SHADOW_MAP_CASCADE_COUNT) {
                val p = (i + 1) / GraphConstants.SHADOW_MAP_CASCADE_COUNT.toFloat()
                val uniform: Float = nearClip + range * p
                val d = cascadeSplitLambda * ((nearClip * Math.pow(ratio.toDouble(), p.toDouble())) - uniform) + uniform
                cascadeSplits[i] = ((d - nearClip) / clipRange).toFloat()
            }

            // Calculate orthographic projection matrix for each cascade
            var lastSplitDist: Float = 0.0f
            for (i in 0..<GraphConstants.SHADOW_MAP_CASCADE_COUNT) {
                val splitDist = cascadeSplits[i]

                val frustumCorners = arrayOf(
                    Vector3f(-1.0f, 1.0f, 0.0f),
                    Vector3f(1.0f, 1.0f, 0.0f),
                    Vector3f(1.0f, -1.0f, 0.0f),
                    Vector3f(-1.0f, -1.0f, 0.0f),
                    Vector3f(-1.0f, 1.0f, 1.0f),
                    Vector3f(1.0f, 1.0f, 1.0f),
                    Vector3f(1.0f, -1.0f, 1.0f),
                    Vector3f(-1.0f, -1.0f, 1.0f)
                )

                // Project frustum corners into world space
                val invCam = Matrix4f(projMatrix).mul(viewMatrix).invert()
                for (j in 0..7) {
                    val invCorner = Vector4f(frustumCorners[j], 1.0f).mul(invCam)
                    frustumCorners[j] =
                        Vector3f(invCorner.x / invCorner.w, invCorner.y / invCorner.w, invCorner.z / invCorner.w)
                }

                for (j in 0..3) {
                    val dist = Vector3f(frustumCorners[j + 4]).sub(frustumCorners[j])
                    frustumCorners[j + 4] = Vector3f(frustumCorners[j]).add(Vector3f(dist).mul(splitDist))
                    frustumCorners[j] = Vector3f(frustumCorners[j]).add(Vector3f(dist).mul(lastSplitDist))
                }

                // Get frustum center
                val frustumCenter = Vector3f(0.0f)
                for (j in 0..7) {
                    frustumCenter.add(frustumCorners[j])
                }
                frustumCenter.div(8.0f)

                var radius = 0.0f
                for (j in 0..7) {
                    val distance = Vector3f(frustumCorners[j]).sub(frustumCenter).length()
                    radius = Math.max(radius, distance)
                }
                radius = Math.ceil((radius * 16.0f).toDouble()).toFloat() / 16.0f

                val maxExtents = Vector3f(radius)
                val minExtents = Vector3f(maxExtents).mul(-1f)

                val lightDir = Vector3f(lightPos.x, lightPos.y, lightPos.z).mul(-1f).normalize()
                val eye = Vector3f(frustumCenter).sub(Vector3f(lightDir).mul(-minExtents.z))
                val up = Vector3f(0.0f, 1.0f, 0.0f)
                val lightViewMatrix = Matrix4f().lookAt(eye, frustumCenter, up)
                val lightOrthoMatrix = Matrix4f().ortho(
                    minExtents.x,
                    maxExtents.x,
                    minExtents.y,
                    maxExtents.y,
                    0.0f,
                    maxExtents.z - minExtents.z,
                    true
                )

                // Store split distance and matrix in cascade
                val cascadeShadow = cascadeShadows[i]
                cascadeShadow.splitDistance = (nearClip + splitDist * clipRange) * -1.0f
                cascadeShadow.projViewMatrix = lightOrthoMatrix.mul(lightViewMatrix)

                lastSplitDist = cascadeSplits[i]
            }
        }
    }
}
