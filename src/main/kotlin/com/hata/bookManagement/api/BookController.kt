package com.hata.bookManagement.api

import com.hata.bookManagement.dto.BookRequest
import com.hata.bookManagement.dto.BookResponse
import com.hata.bookManagement.dto.BookUpdateRequest
import com.hata.bookManagement.service.book.BookService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/books")
class BookController(private val service: BookService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createBook(@RequestBody req: BookRequest): BookResponse {
        return service.createBook(req)
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun updateBook(@PathVariable id: Long, @RequestBody req: BookUpdateRequest): BookResponse {
        return service.updateBook(id, req)
    }
}