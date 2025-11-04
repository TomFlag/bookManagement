package com.hata.bookManagement.dto

import java.math.BigDecimal

data class BookRequest(
    val title: String,
    val authorIds: List<Long> = emptyList(),
    val price: BigDecimal? = null,
    val status: String? = null
)
