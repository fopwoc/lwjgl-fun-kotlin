
package io.github.fopwoc.model

data class ModelData(
    val modelId: String,
    val meshDataList: Array<MeshData> = arrayOf(),
    val materialList: Array<Material> = arrayOf(),
)
