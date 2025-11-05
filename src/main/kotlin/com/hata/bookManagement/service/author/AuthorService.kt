package com.hata.bookManagement.service.author

import com.hata.bookManagement.dto.author.AuthorRequest
import com.hata.bookManagement.dto.author.AuthorResponse
import com.hata.bookManagement.dto.author.AuthorUpdateRequest
import com.hata.bookManagement.dto.book.BookResponse
import com.hata.bookManagement.exception.BadRequestException
import com.hata.bookManagement.exception.ConflictException
import com.hata.bookManagement.exception.NotFoundException
import com.hata.jooq.tables.Authors.AUTHORS
import com.hata.jooq.tables.BookAuthors.BOOK_AUTHORS
import com.hata.jooq.tables.Books.BOOKS
import org.jooq.DSLContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneId

@Service
class AuthorService(private val dsl: DSLContext) {

    private val jst: ZoneId = ZoneId.of("Asia/Tokyo")

    // ----------------- helpers -----------------
    private inline fun <T> mapConflict(action: () -> T): T {
        try {
            return action()
        } catch (e: org.jooq.exception.DataAccessException) {
            throw ConflictException("author already exists", e)
        } catch (e: java.sql.SQLIntegrityConstraintViolationException) {
            throw ConflictException("author already exists", e)
        }
    }

    private fun toOffsetDate(ld: java.time.LocalDate): OffsetDateTime = ld.atStartOfDay(jst).toOffsetDateTime()

    private fun toLocalDateFromDb(value: Any?): java.time.LocalDate {
        val odt = value as? OffsetDateTime ?: throw BadRequestException("invalid birth_date")
        return odt.toInstant().atZone(jst).toLocalDate()
    }

    private fun validateNameNotBlank(name: String, fieldName: String = "name") {
        if (name.isBlank()) throw BadRequestException("$fieldName must not be blank")
    }

    private fun validateBirthDateNotFuture(ld: java.time.LocalDate?, fieldName: String = "birthDate") {
        if (ld != null && ld.isAfter(java.time.LocalDate.now(jst))) {
            throw BadRequestException("$fieldName must not be in the future")
        }
    }

    @Transactional
    fun createAuthor(request: AuthorRequest): AuthorResponse {
        // 入力検証
        validateNameNotBlank(request.name)
        validateBirthDateNotFuture(request.birthDate)

        val odt = toOffsetDate(request.birthDate)

        return mapConflict {
            val record = dsl.insertInto(AUTHORS)
                .set(AUTHORS.NAME, request.name)
                .set(AUTHORS.BIRTH_DATE, odt)
                .returning(AUTHORS.ID, AUTHORS.NAME, AUTHORS.BIRTH_DATE)
                .fetchOne() ?: throw IllegalStateException("failed to insert author")

            val birthLocalDate = toLocalDateFromDb(record.getValue(AUTHORS.BIRTH_DATE))

            AuthorResponse(
                id = requireNotNull(record.getValue(AUTHORS.ID)),
                name = requireNotNull(record.getValue(AUTHORS.NAME)),
                birthDate = birthLocalDate
            )
        }
    }

    @Transactional
    fun updateAuthor(id: Long, request: AuthorUpdateRequest): AuthorResponse {
        // 入力検証
        if (request.newName != null) validateNameNotBlank(request.newName, "newName")
        validateBirthDateNotFuture(request.newBirthDate, "newBirthDate")

        // 既存著者を id で取得
        val existing = dsl.select(AUTHORS.ID, AUTHORS.NAME, AUTHORS.BIRTH_DATE)
            .from(AUTHORS)
            .where(AUTHORS.ID.eq(id))
            .fetchOne() ?: throw NotFoundException("author not found")

        // 更新する値が何も無ければエラー
        if (request.newName == null && request.newBirthDate == null) {
            throw BadRequestException("nothing to update")
        }

        val newBirthOffsetDate = request.newBirthDate?.let { toOffsetDate(it) }

        // 最初の set を条件に合わせて作成し、残りをチェインする
        val firstSet = if (request.newName != null) {
            dsl.update(AUTHORS).set(AUTHORS.NAME, request.newName)
        } else {
            dsl.update(AUTHORS).set(AUTHORS.BIRTH_DATE, newBirthOffsetDate!!)
        }

        val updateStep = if (request.newName != null && newBirthOffsetDate != null) {
            firstSet.set(AUTHORS.BIRTH_DATE, newBirthOffsetDate)
        } else {
            firstSet
        }

        return mapConflict {
            val updated = updateStep
                .where(AUTHORS.ID.eq(requireNotNull(existing.getValue(AUTHORS.ID))))
                .returning(AUTHORS.ID, AUTHORS.NAME, AUTHORS.BIRTH_DATE)
                .fetchOne()

            val record = updated ?: throw ConflictException("failed to update author")

            val birthLocalDate = toLocalDateFromDb(record.getValue(AUTHORS.BIRTH_DATE))

            AuthorResponse(
                id = requireNotNull(record.getValue(AUTHORS.ID)),
                name = requireNotNull(record.getValue(AUTHORS.NAME)),
                birthDate = birthLocalDate
            )
        }
    }

    @Transactional(readOnly = true)
    fun getBooksByAuthor(authorId: Long): List<BookResponse> {
        // author 存在確認
        dsl.select(AUTHORS.ID)
            .from(AUTHORS)
            .where(AUTHORS.ID.eq(authorId))
            .fetchOne() ?: throw NotFoundException("author not found")

        // 著者に紐づく書籍 ID を取得
        val bookIds = dsl.selectDistinct(BOOK_AUTHORS.BOOK_ID)
            .from(BOOK_AUTHORS)
            .where(BOOK_AUTHORS.AUTHOR_ID.eq(authorId))
            .fetch(BOOK_AUTHORS.BOOK_ID)
            .map { (it as Number).toLong() }

        if (bookIds.isEmpty()) return emptyList()

        // 取得した書籍群について、各書籍の情報と著者リストを取得してグルーピング
        val rows = dsl.select(BOOKS.ID, BOOKS.TITLE, BOOKS.PRICE, BOOKS.STATUS, BOOK_AUTHORS.AUTHOR_ID, BOOK_AUTHORS.AUTHOR_ORDER)
            .from(BOOKS)
            .join(BOOK_AUTHORS).on(BOOKS.ID.eq(BOOK_AUTHORS.BOOK_ID))
            .where(BOOKS.ID.`in`(bookIds))
            .orderBy(BOOKS.ID, BOOK_AUTHORS.AUTHOR_ORDER)
            .fetch()

        val grouped = rows.groupBy { it.get(BOOKS.ID) as Long }

        return grouped.map { (bookId, recs) ->
            val first = recs.first()
            val title = first.get(BOOKS.TITLE) as String
            val price = first.get(BOOKS.PRICE) ?: java.math.BigDecimal.ZERO
            val status = first.get(BOOKS.STATUS)?.name ?: "UNKNOWN"
            val authorIds = recs.map { (it.get(BOOK_AUTHORS.AUTHOR_ID) as Number).toLong() }
            BookResponse(
                id = bookId,
                title = title,
                authorIds = authorIds,
                price = price,
                status = status
            )
        }
    }

}