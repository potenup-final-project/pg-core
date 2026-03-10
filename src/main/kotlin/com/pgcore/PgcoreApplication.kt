package com.pgcore

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class PgcoreApplication

fun main(args: Array<String>) {
    runApplication<PgcoreApplication>(*args)
}
