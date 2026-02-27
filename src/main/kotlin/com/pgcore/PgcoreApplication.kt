package com.pgcore

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PgcoreApplication

fun main(args: Array<String>) {
    runApplication<PgcoreApplication>(*args)
}
