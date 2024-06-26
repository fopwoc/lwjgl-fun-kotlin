package io.github.fopwoc.graphics

class IndexedLinkedHashMap<K, V> : LinkedHashMap<K, V>() {
    private val indexList: MutableList<K> = ArrayList()
    fun getIndexOf(key: K): Int {
        return indexList.indexOf(key)
    }

    fun getValueAtIndex(i: Int): V? {
        return super.get(indexList[i])
    }

    override fun put(key: K, value: V): V? {
        if (!super.containsKey(key)) indexList.add(key)
        return super.put(key, value)
    }
}
