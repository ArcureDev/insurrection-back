package org.arcure.back

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
open class BackApplication

fun main(args: Array<String>) {
    runApplication<BackApplication>(*args)
}
