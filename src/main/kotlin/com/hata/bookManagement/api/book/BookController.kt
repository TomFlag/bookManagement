package com.hata.bookManagement.api.book

import com.hata.bookManagement.dto.book.BookRequest
import com.hata.bookManagement.dto.book.BookResponse
import com.hata.bookManagement.dto.book.BookUpdateRequest
import com.hata.bookManagement.service.book.BookService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

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