package io.github.fopwoc.model

@JvmRecord
data class MeshData(
    val positions: FloatArray,
    val normals: FloatArray,
    val tangents: FloatArray,
    val biTangents: FloatArray,
    val textCoords: FloatArray,
    val indices: IntArray,
    val materialIdx: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MeshData

        if (!positions.contentEquals(other.positions)) return false
        if (!textCoords.contentEquals(other.textCoords)) return false
        if (!indices.contentEquals(other.indices)) return false
        return materialIdx == other.materialIdx
    }

    override fun hashCode(): Int {
        var result = positions.contentHashCode()
        result = 31 * result + textCoords.contentHashCode()
        result = 31 * result + indices.contentHashCode()
        result = 31 * result + materialIdx
        return result
    }
}
