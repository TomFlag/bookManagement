// kotlin
package com.hata.jooq

import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.math.BigDecimal

@Service
class JooqSampleService(private val dsl: DSLContext) {

    fun insertSample(): Pair<Long, Long> {
        // author を挿入して id を取得
        val authorId = dsl.insertInto(DSL.table("authors"))
            .columns(DSL.field("name"), DSL.field("birth_date"))
            .values("Alice", LocalDate.of(1980, 1, 1))
            .returning(DSL.field("id", Long::class.java))
            .fetchOne()!!.get(DSL.field("id", Long::class.java))!!

        // book を挿入して id を取得
        val bookId = dsl.insertInto(DSL.table("books"))
            .columns(DSL.field("title"), DSL.field("price"), DSL.field("status"))
            .values("Sample Book", BigDecimal("9.99"), "UNPUBLISHED")
            .returning(DSL.field("id", Long::class.java))
            .fetchOne()!!.get(DSL.field("id", Long::class.java))!!

        // 中間テーブルに紐付け
        dsl.insertInto(DSL.table("book_authors"))
            .columns(DSL.field("book_id"), DSL.field("author_id"), DSL.field("author_order"))
            .values(bookId, authorId, 1)
            .execute()

        return Pair(authorId, bookId)
    }

    fun printAll() {
        val records = dsl.select()
            .from(DSL.table("books"))
            .leftJoin(DSL.table("book_authors")).on(DSL.field("books.id").eq(DSL.field("book_authors.book_id")))
            .leftJoin(DSL.table("authors")).on(DSL.field("authors.id").eq(DSL.field("book_authors.author_id")))
            .fetch()

        records.forEach { println(it) }
    }
}