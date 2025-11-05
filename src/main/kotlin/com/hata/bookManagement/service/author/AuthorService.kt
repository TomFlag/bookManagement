package com.hata.bookManagement.service.author

import com.hata.bookManagement.dto.author.AuthorRequest
import com.hata.bookManagement.dto.author.AuthorResponse
import com.hata.bookManagement.dto.author.AuthorUpdateRequest
import com.hata.bookManagement.dto.book.BookResponse
import com.hata.jooq.enums.PublicationStatus
import com.hata.jooq.tables.Authors.AUTHORS
import com.hata.jooq.tables.BookAuthors.BOOK_AUTHORS
import com.hata.jooq.tables.Books.BOOKS
import org.jooq.DSLContext
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.collections.component1
import kotlin.collections.component2

@Service
class AuthorService(private val dsl: DSLContext) {

    private val jst: ZoneId = ZoneId.of("Asia/Tokyo")

    @Transactional
    fun createAuthor(request: AuthorRequest): AuthorResponse {
        val odt = request.birthDate.atStartOfDay(jst).toOffsetDateTime()

        val record = dsl.insertInto(AUTHORS)
            .set(AUTHORS.NAME, request.name)
            .set(AUTHORS.BIRTH_DATE, odt)
            .returning(AUTHORS.ID, AUTHORS.NAME, AUTHORS.BIRTH_DATE)
            .fetchOne() ?: throw IllegalStateException("failed to insert author")

        val storedOdt = record.getValue(AUTHORS.BIRTH_DATE)!!
        // OffsetDateTime -> Asia/Tokyo の LocalDate に変換して返す
        val birthLocalDate = storedOdt.toInstant().atZone(jst).toLocalDate()

        return AuthorResponse(
            id = record.getValue(AUTHORS.ID)!!,
            name = record.getValue(AUTHORS.NAME)!!,
            birthDate = birthLocalDate
        )
    }

    @Transactional
    fun updateAuthor(request: AuthorUpdateRequest): AuthorResponse {
        // 現在の識別情報を JST の午前0時の OffsetDateTime に変換
        val currentOdt = request.birthDate.atStartOfDay(jst).toOffsetDateTime()
        val newOdt = request.newBirthDate?.atStartOfDay(jst)?.toOffsetDateTime()

        // 存在確認（name と birth_date の完全一致）
        val existing = dsl.select(AUTHORS.ID, AUTHORS.NAME, AUTHORS.BIRTH_DATE)
            .from(AUTHORS)
            .where(AUTHORS.NAME.eq(request.name).and(AUTHORS.BIRTH_DATE.eq(currentOdt)))
            .fetchOne() ?: throw IllegalStateException("author not found")

        // 更新する値が何も無ければエラー
        if (request.newName == null && newOdt == null) {
            throw IllegalArgumentException("nothing to update")
        }

        // 最初の set を条件に合わせて作成し、残りをチェインする
        val firstSet = if (request.newName != null) {
            dsl.update(AUTHORS).set(AUTHORS.NAME, request.newName)
        } else {
            // newName == null なら newOdt は必ず非 null（上のチェックで保証）
            dsl.update(AUTHORS).set(AUTHORS.BIRTH_DATE, newOdt!!)
        }

        val updateStep = if (request.newName != null && newOdt != null) {
            firstSet.set(AUTHORS.BIRTH_DATE, newOdt)
        } else {
            firstSet
        }

        // 更新対象は先に取得した行の ID を使う（安全）
        val updated = updateStep
            .where(AUTHORS.ID.eq(existing.getValue(AUTHORS.ID) as Long))
            .returning(AUTHORS.ID, AUTHORS.NAME, AUTHORS.BIRTH_DATE)
            .fetchOne()

        val record = updated ?: throw IllegalStateException("failed to update author")

        val storedOdt = record.getValue(AUTHORS.BIRTH_DATE) as OffsetDateTime
        val birthLocalDate = storedOdt.toInstant().atZone(jst).toLocalDate()

        return AuthorResponse(
            id = (record.getValue(AUTHORS.ID) as Long),
            name = record.getValue(AUTHORS.NAME) as String,
            birthDate = birthLocalDate
        )
    }

    @Transactional(readOnly = true)
    fun getBooksByAuthor(authorId: Long): List<BookResponse> {
        // author 存在確認
        dsl.select(AUTHORS.ID)
            .from(AUTHORS)
            .where(AUTHORS.ID.eq(authorId))
            .fetchOne() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "author not found")

        // まず著者に紐づく書籍 ID を取得
        val bookIds = dsl.selectDistinct(BOOKS.ID)
            .from(BOOKS)
            .join(BOOK_AUTHORS).on(BOOKS.ID.eq(BOOK_AUTHORS.BOOK_ID))
            .where(BOOK_AUTHORS.AUTHOR_ID.eq(authorId))
            .fetch(BOOKS.ID)
            .map { it as Long }

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
            val price = first.get(BOOKS.PRICE) as java.math.BigDecimal
            val status = (first.get(BOOKS.STATUS) as PublicationStatus).name
            val authorIds = recs.map { it.get(BOOK_AUTHORS.AUTHOR_ID) as Long }
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