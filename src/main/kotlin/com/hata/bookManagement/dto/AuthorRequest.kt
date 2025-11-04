package com.hata.bookManagement.dto

import java.time.LocalDate

data class AuthorRequest(
    val name: String,
    val birthDate: LocalDate
)
