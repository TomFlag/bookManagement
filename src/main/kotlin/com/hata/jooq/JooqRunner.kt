// kotlin
package com.hata.jooq

import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class JooqRunner(private val sampleService: JooqSampleService) : CommandLineRunner {
    override fun run(vararg args: String?) {
        val (authorId, bookId) = sampleService.insertSample()
        println("Inserted author=$authorId book=$bookId")
        sampleService.printAll()
    }
}