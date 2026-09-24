package com.wisso.wizefiles.util

open class MapSet<K, V> protected constructor(
    private val keyOf: (V) -> K,
    private val entries: MutableMap<K, V>
) : AbstractMutableSet<V>() {

    constructor(keyExtractor: (V) -> K) : this(keyExtractor, HashMap())

    override val size: Int
        get() = entries.size

    override fun iterator(): MutableIterator<V> = entries.values.iterator()

    override fun contains(element: V): Boolean = entries.containsKey(keyOf(element))

    override fun add(element: V): Boolean {
        val key = keyOf(element)
        val wasAbsent = !entries.containsKey(key)
        entries[key] = element
        return wasAbsent
    }

    override fun remove(element: V): Boolean {
        val key = keyOf(element)
        if (!entries.containsKey(key)) {
            return false
        }
        entries.remove(key)
        return true
    }

    override fun clear() = entries.clear()
}

open class LinkedMapSet<K, V>(keyExtractor: (V) -> K) :
    MapSet<K, V>(keyExtractor, LinkedHashMap())
