package com.hata.bookManagement.dto

import java.math.BigDecimal

data class BookUpdateRequest(
    val title: String? = null,
    val authorIds: List<Long>? = null, // null=変更しない、空リストはバリデーションエラー
    val price: BigDecimal? = null,
    val status: String? = null
)
