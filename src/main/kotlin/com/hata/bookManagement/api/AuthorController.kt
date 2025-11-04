package com.hata.bookManagement.api

import com.hata.bookManagement.dto.AuthorRequest
import com.hata.bookManagement.dto.AuthorUpdateRequest
import com.hata.bookManagement.dto.AuthorResponse
import com.hata.bookManagement.dto.BookResponse
import com.hata.bookManagement.service.author.AuthorService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/authors")
class AuthorController(private val service: AuthorService) {

    @GetMapping("/{id}/books")
    fun getBooksByAuthor(@PathVariable id: Long): List<BookResponse> {
        return service.getBooksByAuthor(id)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createAuthor(@RequestBody req: AuthorRequest): AuthorResponse {
        return service.createAuthor(req)
    }

    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    fun updateAuthor(@RequestBody req: AuthorUpdateRequest): AuthorResponse {
        return service.updateAuthor(req)
    }
}