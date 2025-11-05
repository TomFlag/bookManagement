package com.hata.bookManagement.dto.author

import java.time.LocalDate

/**
 * For ID-based updates the request should contain only the new values to apply.
 * Fields are optional; null means "no change".
 */
data class AuthorUpdateRequest(
    val newName: String? = null,
    val newBirthDate: LocalDate? = null
)
