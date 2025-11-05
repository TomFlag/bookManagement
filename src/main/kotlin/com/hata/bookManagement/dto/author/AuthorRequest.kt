package com.hata.bookManagement.dto.author

import java.time.LocalDate

data class AuthorRequest(
    val name: String,
    val birthDate: LocalDate
)
