package com.pgcore.global.logging.context

object LogContextHolder {
    private val holder = ThreadLocal<MutableMap<String, Any?>>()

    fun put(key: String, value: Any?) {
        val map = holder.get() ?: mutableMapOf<String, Any?>().also { holder.set(it) }
        map[key] = value
    }

    fun putAll(values: Map<String, Any?>) {
        val map = holder.get() ?: mutableMapOf<String, Any?>().also { holder.set(it) }
        map.putAll(values)
    }

    fun getAll(): Map<String, Any?> {
        return holder.get()?.toMap() ?: emptyMap()
    }

    fun clear() {
        holder.remove()
    }
}
