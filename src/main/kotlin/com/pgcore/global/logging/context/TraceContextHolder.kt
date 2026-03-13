package com.pgcore.global.logging.context

object TraceContextHolder {
    private val holder = ThreadLocal<TraceContext>()

    fun set(context: TraceContext) {
        holder.set(context)
    }

    fun get(): TraceContext? = holder.get()

    fun clear() {
        holder.remove()
    }
}
