package com.hata.bookManagement.service.book

import com.hata.bookManagement.dto.book.BookRequest
import com.hata.bookManagement.dto.book.BookResponse
import com.hata.bookManagement.dto.book.BookUpdateRequest
import com.hata.bookManagement.exception.BadRequestException
import com.hata.bookManagement.exception.ConflictException
import com.hata.bookManagement.exception.NotFoundException
import com.hata.jooq.enums.PublicationStatus
import com.hata.jooq.tables.Authors.AUTHORS
import com.hata.jooq.tables.BookAuthors.BOOK_AUTHORS
import com.hata.jooq.tables.Books.BOOKS
import com.hata.jooq.tables.records.BooksRecord
import org.jooq.DSLContext
import org.jooq.UpdateSetFirstStep
import org.jooq.UpdateSetMoreStep
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BookService(private val dsl: DSLContext) {
    // ----------------- helpers -----------------
    private fun checkPublicationStatus(status: String?): PublicationStatus? {
        return status?.let {
            try {
                PublicationStatus.valueOf(it)
            } catch (_: IllegalArgumentException) {
                throw BadRequestException("invalid status: $it")
            }
        }
    }

    private fun checkAuthorExist(ids: List<Long>): List<Long> {
        val deduped = ids.distinct()
        if (deduped.isEmpty()) {
            throw BadRequestException("book must have at least one author")
        }
        val cnt = dsl.selectCount()
            .from(AUTHORS)
            .where(AUTHORS.ID.`in`(deduped))
            .fetchOne(0, Int::class.java) ?: 0
        if (cnt != deduped.size) {
            throw BadRequestException("one or more authors not found")
        }
        return deduped
    }

    private fun insertBookAuthors(bookId: Long, authorIds: List<Long>) {
        authorIds.forEachIndexed { idx, authorId ->
            dsl.insertInto(BOOK_AUTHORS)
                .set(BOOK_AUTHORS.BOOK_ID, bookId)
                .set(BOOK_AUTHORS.AUTHOR_ID, authorId)
                .set(BOOK_AUTHORS.AUTHOR_ORDER, idx + 1)
                .execute()
        }
    }

    private inline fun <T> mapConflict(action: () -> T): T {
        try {
            return action()
        } catch (e: org.jooq.exception.DataAccessException) {
            throw ConflictException("conflict performing DB operation", e)
        } catch (e: java.sql.SQLIntegrityConstraintViolationException) {
            throw ConflictException("conflict performing DB operation", e)
        }
    }

    @Transactional
    fun createBook(request: BookRequest): BookResponse {
        if (request.authorIds.isEmpty()) {
            throw BadRequestException("book must have at least one author")
        }

        // authorIds に重複が含まれている場合は先に重複除去して順序を保持する
        val authorIds = request.authorIds.distinct()
        if (authorIds.isEmpty()) {
            throw BadRequestException("book must have at least one author")
        }

        val publicationStatus = checkPublicationStatus(request.status)

        var insertStep = dsl.insertInto(BOOKS)
            .set(BOOKS.TITLE, request.title)

        if (request.price != null) {
            insertStep = insertStep.set(BOOKS.PRICE, request.price)
        }
        if (publicationStatus != null) {
            insertStep = insertStep.set(BOOKS.STATUS, publicationStatus)
        }

        // authorIds の存在チェック
        val validatedAuthorIds = checkAuthorExist(authorIds)

        return mapConflict {
            val bookRecord = insertStep
                .returning(BOOKS.ID, BOOKS.TITLE, BOOKS.PRICE, BOOKS.STATUS)
                .fetchOne() ?: throw ConflictException("failed to insert book")

            val id = bookRecord.get(BOOKS.ID) as Long
            val title = bookRecord.get(BOOKS.TITLE) as String
            val price = bookRecord.get(BOOKS.PRICE) ?: java.math.BigDecimal.ZERO
            val status = bookRecord.get(BOOKS.STATUS)?.name ?: "UNKNOWN"

            // author_rel を作成。順序は渡された配列順を author_order に入れる。
            insertBookAuthors(id, validatedAuthorIds)

            BookResponse(
                id = id,
                title = title,
                authorIds = validatedAuthorIds,
                price = price,
                status = status
            )
        }
    }

    @Transactional
    fun updateBook(id: Long, request: BookUpdateRequest): BookResponse {
        // book 存在確認
        if (dsl.selectCount().from(BOOKS).where(BOOKS.ID.eq(id)).fetchOne(0, Int::class.java) == 0) {
            throw NotFoundException("book not found")
        }

        // authorIds が明示的に提供されている場合は空禁止
        if (request.authorIds != null && request.authorIds.isEmpty()) {
            throw BadRequestException("book must have at least one author")
        }

        // status の検証
        val publicationStatus = checkPublicationStatus(request.status)

        // 著者を差し替える場合は、指定された authorId が全て存在するか確認
        if (request.authorIds != null) {
            val validatedAuthorIds = checkAuthorExist(request.authorIds)

            dsl.deleteFrom(BOOK_AUTHORS).where(BOOK_AUTHORS.BOOK_ID.eq(id)).execute()
            insertBookAuthors(id, validatedAuthorIds)
        }

        // BOOKS の更新（指定があるフィールドのみ）
        var needUpdate = false
        val firstStep: UpdateSetFirstStep<BooksRecord> = dsl.update(BOOKS)
        var moreStep: UpdateSetMoreStep<BooksRecord>? = null
        if (request.title != null) {
            moreStep = null ?: firstStep.set(BOOKS.TITLE, request.title)
            needUpdate = true
        }
        if (publicationStatus != null) {
            moreStep = moreStep?.set(BOOKS.STATUS, publicationStatus) ?: firstStep.set(BOOKS.STATUS, publicationStatus)
            needUpdate = true
        }
        if (request.price != null) {
            moreStep = moreStep?.set(BOOKS.PRICE, request.price) ?: firstStep.set(BOOKS.PRICE, request.price)
            needUpdate = true
        }

        val bookRecord = if (needUpdate) {
            moreStep!!
                .where(BOOKS.ID.eq(id))
                .returning(BOOKS.ID, BOOKS.TITLE, BOOKS.PRICE, BOOKS.STATUS)
                .fetchOne() ?: throw ConflictException("failed to update book")
        } else {
            dsl.select(BOOKS.ID, BOOKS.TITLE, BOOKS.PRICE, BOOKS.STATUS)
                .from(BOOKS)
                .where(BOOKS.ID.eq(id))
                .fetchOne() ?: throw ConflictException("failed to fetch book")
        }

        // 最終的な著者リストを取得（差し替え無しなら既存を取得）
        val finalAuthorIds = request.authorIds?.distinct()
            ?: dsl.select(BOOK_AUTHORS.AUTHOR_ID)
                .from(BOOK_AUTHORS)
                .where(BOOK_AUTHORS.BOOK_ID.eq(id))
                .orderBy(BOOK_AUTHORS.AUTHOR_ORDER)
                .fetch(BOOK_AUTHORS.AUTHOR_ID)
                .map { it as Long }

        val storedId = bookRecord.get(BOOKS.ID) as Long
        val storedTitle = bookRecord.get(BOOKS.TITLE) as String
        val storedPrice = bookRecord.get(BOOKS.PRICE) as java.math.BigDecimal
        val storedStatus = (bookRecord.get(BOOKS.STATUS) as PublicationStatus).name

        return BookResponse(
            id = storedId,
            title = storedTitle,
            authorIds = finalAuthorIds,
            price = storedPrice,
            status = storedStatus
        )
    }
}