package io.github.fopwoc.model

import io.github.fopwoc.graphics.vulkan.Device
import io.github.fopwoc.graphics.vulkan.Semaphore

@JvmRecord
data class SyncSemaphores(
    val imgAcquisitionSemaphore: Semaphore,
    val geometryCompleteSemaphore: Semaphore,
    val  renderCompleteSemaphore: Semaphore
) {
    constructor(device: Device) : this(
        Semaphore(device),
        Semaphore(device),
        Semaphore(device)
    )

    fun cleanup() {
        imgAcquisitionSemaphore.cleanup()
        geometryCompleteSemaphore.cleanup()
        renderCompleteSemaphore.cleanup()
    }
}
