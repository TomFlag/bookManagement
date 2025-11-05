package com.hata.bookManagement.service.book

import com.hata.bookManagement.dto.book.BookRequest
import com.hata.bookManagement.dto.book.BookResponse
import com.hata.bookManagement.dto.book.BookUpdateRequest
import com.hata.jooq.enums.PublicationStatus
import com.hata.jooq.tables.Authors.AUTHORS
import com.hata.jooq.tables.BookAuthors.BOOK_AUTHORS
import com.hata.jooq.tables.Books.BOOKS
import com.hata.jooq.tables.records.BooksRecord
import org.jooq.DSLContext
import org.jooq.UpdateSetFirstStep
import org.jooq.UpdateSetMoreStep
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal

@Service
class BookService(private val dsl: DSLContext) {
    @Transactional
    fun createBook(request: BookRequest): BookResponse {
        if (request.authorIds.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "book must have at least one author")
        }

        val statusEnum = request.status?.let {
            try {
                PublicationStatus.valueOf(it)
            } catch (ex: IllegalArgumentException) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid status: $it")
            }
        }

        var insertStep = dsl.insertInto(BOOKS)
            .set(BOOKS.TITLE, request.title)

        if (request.price != null) {
            insertStep = insertStep.set(BOOKS.PRICE, request.price)
        }
        if (statusEnum != null) {
            insertStep = insertStep.set(BOOKS.STATUS, statusEnum)
        }

        val bookRecord = insertStep
            .returning(BOOKS.ID, BOOKS.TITLE, BOOKS.PRICE, BOOKS.STATUS)
            .fetchOne() ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to insert book")

        val bookId = bookRecord.get(BOOKS.ID)!!
        val storedPrice = bookRecord.get(BOOKS.PRICE) as BigDecimal
        val storedStatus = (bookRecord.get(BOOKS.STATUS) as PublicationStatus).name

        if (request.authorIds.isNotEmpty()) {
            val batch = request.authorIds.map { authorId ->
                dsl.insertInto(BOOK_AUTHORS)
                    .set(BOOK_AUTHORS.BOOK_ID, bookId)
                    .set(BOOK_AUTHORS.AUTHOR_ID, authorId)
            }
            // execute inserts
            batch.forEach { it.execute() }
        }

        return BookResponse(
            id = bookId,
            title = bookRecord.get(BOOKS.TITLE)!!,
            authorIds = request.authorIds,
            price = storedPrice,
            status = storedStatus
        )
    }

    @Transactional
    fun updateBook(id: Long, request: BookUpdateRequest): BookResponse {
        // book 存在確認
        val exists = dsl.select(BOOKS.ID)
            .from(BOOKS)
            .where(BOOKS.ID.eq(id))
            .fetchOne() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "book not found")

        // authorIds が明示的に提供されている場合は空禁止
        if (request.authorIds != null && request.authorIds.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "book must have at least one author")
        }

        // status の検証
        val statusEnum = request.status?.let {
            try {
                PublicationStatus.valueOf(it)
            } catch (ex: IllegalArgumentException) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid status: $it")
            }
        }

        // 著者を差し替える場合は、指定された authorId が全て存在するか確認
        if (request.authorIds != null) {
            val cnt = dsl.selectCount()
                .from(AUTHORS)
                .where(AUTHORS.ID.`in`(request.authorIds))
                .fetchOne(0, Int::class.java) ?: 0
            if (cnt != request.authorIds.size) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "one or more authors not found")
            }
        }

        // BOOKS の更新（指定があるフィールドのみ）
        var needUpdate = false
        val firstStep: UpdateSetFirstStep<BooksRecord> = dsl.update(BOOKS)
        var moreStep: UpdateSetMoreStep<BooksRecord>? = null
        if (request.title != null) {
            moreStep = null ?: firstStep.set(BOOKS.TITLE, request.title)
            needUpdate = true
        }
        if (statusEnum != null) {
            moreStep = moreStep?.set(BOOKS.STATUS, statusEnum) ?: firstStep.set(BOOKS.STATUS, statusEnum)
            needUpdate = true
        }
        if (request.price != null) {
            moreStep = moreStep?.set(BOOKS.PRICE, request.price) ?: firstStep.set(BOOKS.PRICE, request.price)
            needUpdate = true
        }
        val finalUpdate = moreStep ?: firstStep

        val bookRecord = if (needUpdate) {
            // needUpdate == true のときは moreStep が必ず非 null のはずなので非 null を明示
            moreStep!!
                .where(BOOKS.ID.eq(id))
                .returning(BOOKS.ID, BOOKS.TITLE, BOOKS.PRICE, BOOKS.STATUS)
                .fetchOne() ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to update book")
        } else {
            dsl.select(BOOKS.ID, BOOKS.TITLE, BOOKS.PRICE, BOOKS.STATUS)
                .from(BOOKS)
                .where(BOOKS.ID.eq(id))
                .fetchOne() ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to fetch book")
        }

        // 著者差し替え処理（nullなら変更しない）
        if (request.authorIds != null) {
            // 既存関連を削除し、新しい順序で挿入
            dsl.deleteFrom(BOOK_AUTHORS).where(BOOK_AUTHORS.BOOK_ID.eq(id)).execute()
            request.authorIds.forEachIndexed { idx, authorId ->
                dsl.insertInto(BOOK_AUTHORS)
                    .set(BOOK_AUTHORS.BOOK_ID, id)
                    .set(BOOK_AUTHORS.AUTHOR_ID, authorId)
                    .set(BOOK_AUTHORS.AUTHOR_ORDER, idx + 1)
                    .execute()
            }
        }

        // 最終的な著者リストを取得（差し替え無しなら既存を取得）
        val finalAuthorIds = request.authorIds
            ?: dsl.select(BOOK_AUTHORS.AUTHOR_ID)
                .from(BOOK_AUTHORS)
                .where(BOOK_AUTHORS.BOOK_ID.eq(id))
                .orderBy(BOOK_AUTHORS.AUTHOR_ORDER)
                .fetch(BOOK_AUTHORS.AUTHOR_ID)
                .map { it as Long }

        val storedPrice = bookRecord.get(BOOKS.PRICE) as java.math.BigDecimal
        val storedStatus = (bookRecord.get(BOOKS.STATUS) as PublicationStatus).name

        return BookResponse(
            id = bookRecord.get(BOOKS.ID) as Long,
            title = bookRecord.get(BOOKS.TITLE) as String,
            authorIds = finalAuthorIds,
            price = storedPrice,
            status = storedStatus
        )
    }
}