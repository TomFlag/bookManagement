package com.hata.bookManagement.dto.author

import java.time.LocalDate

data class AuthorUpdateRequest(
    val name: String,
    val birthDate: LocalDate,
    val newName: String? = null,
    val newBirthDate: LocalDate? = null
)
