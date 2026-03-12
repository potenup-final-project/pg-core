package com.pgcore.global.logging.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class BusinessLog(
    val event: String,
    val category: String = "BUSINESS",
    val logOnSuccess: Boolean = true,
    val logOnFailure: Boolean = true,
)
