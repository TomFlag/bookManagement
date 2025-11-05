package com.hata.bookManagement.dto.book

import java.math.BigDecimal

data class BookResponse(
    val id: Long,
    val title: String,
    val authorIds: List<Long>,
    val price: BigDecimal,
    val status: String
)
