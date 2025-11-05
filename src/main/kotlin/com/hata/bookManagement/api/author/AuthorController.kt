package com.hata.bookManagement.api.author

import com.hata.bookManagement.dto.author.AuthorRequest
import com.hata.bookManagement.dto.author.AuthorResponse
import com.hata.bookManagement.dto.author.AuthorUpdateRequest
import com.hata.bookManagement.dto.book.BookResponse
import com.hata.bookManagement.service.author.AuthorService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

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

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun updateAuthor(@PathVariable id: Long, @RequestBody req: AuthorUpdateRequest): AuthorResponse {
        return service.updateAuthor(id, req)
    }
}