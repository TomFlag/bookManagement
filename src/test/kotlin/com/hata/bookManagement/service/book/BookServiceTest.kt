package com.hata.bookManagement.service.book

import com.hata.bookManagement.dto.book.BookRequest
import com.hata.bookManagement.dto.book.BookUpdateRequest
import com.hata.bookManagement.service.book.BookService
import com.hata.bookManagement.test.TestDb
import com.hata.jooq.enums.PublicationStatus
import com.hata.jooq.tables.Authors.AUTHORS
import com.hata.jooq.tables.BookAuthors.BOOK_AUTHORS
import com.hata.jooq.tables.Books.BOOKS
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.math.BigDecimal
import java.time.LocalDate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookServiceTest {

    private lateinit var dsl: DSLContext
    private lateinit var service: BookService
    private lateinit var postgresContainer: org.testcontainers.containers.PostgreSQLContainer<*>

    @BeforeAll
    fun setupAll() {
        val (d, container) = TestDb.start()
        dsl = d
        postgresContainer = container
        service = BookService(dsl)
    }

    @AfterAll
    fun tearDownAll() {
        // Container lifecycle is managed by TestDb (shared per-JVM). Do not stop it here.
    }

    @BeforeEach
    fun cleanup() {
        dsl.execute("TRUNCATE TABLE public.book_authors, public.books, public.authors RESTART IDENTITY CASCADE")
    }

    @Test
    fun createBook() {
        // prepare author
        val authorRec = dsl.insertInto(AUTHORS)
            .set(AUTHORS.NAME, "Author X")
            .set(AUTHORS.BIRTH_DATE, LocalDate.of(1985, 6, 7).atStartOfDay().atOffset(java.time.ZoneOffset.ofHours(9)))
            .returning(AUTHORS.ID)
            .fetchOne()!!

        val authorId = authorRec.getValue(AUTHORS.ID) as Long

        val req = BookRequest(
            title = "My Book",
            authorIds = listOf(authorId),
            price = BigDecimal("1200.00"),
            status = PublicationStatus.UNPUBLISHED.name
        )

        val resp = dsl.transactionResult { cfg ->
            val txDsl = DSL.using(cfg)
            val txService = BookService(txDsl)
            txService.createBook(req)
        }

        assertNotNull(resp.id)
        assertEquals("My Book", resp.title)
        assertEquals(listOf(authorId), resp.authorIds)
        assertEquals(BigDecimal("1200.00"), resp.price)
        assertEquals(PublicationStatus.UNPUBLISHED.name, resp.status)

        // verify persisted
        val stored = dsl.select(BOOKS.ID, BOOKS.TITLE).from(BOOKS).where(BOOKS.ID.eq(resp.id)).fetchOne()
        assertNotNull(stored)
    }

    @Test
    fun updateBook() {
        // create initial author and book via service
        val a1 = dsl.insertInto(AUTHORS)
            .set(AUTHORS.NAME, "Author A")
            .set(AUTHORS.BIRTH_DATE, LocalDate.of(1970,1,1).atStartOfDay().atOffset(java.time.ZoneOffset.ofHours(9)))
            .returning(AUTHORS.ID)
            .fetchOne()!!
        val a1Id = a1.getValue(AUTHORS.ID) as Long

        val createReq = BookRequest(
            title = "Original",
            authorIds = listOf(a1Id),
            price = BigDecimal("1500.00"),
            status = PublicationStatus.UNPUBLISHED.name
        )

        val created = dsl.transactionResult { cfg ->
            val txDsl = DSL.using(cfg)
            val txService = BookService(txDsl)
            txService.createBook(createReq)
        }

        // prepare new author
        val a2 = dsl.insertInto(AUTHORS)
            .set(AUTHORS.NAME, "Author B")
            .set(AUTHORS.BIRTH_DATE, LocalDate.of(1980,2,2).atStartOfDay().atOffset(java.time.ZoneOffset.ofHours(9)))
            .returning(AUTHORS.ID)
            .fetchOne()!!
        val a2Id = a2.getValue(AUTHORS.ID) as Long

        val updateReq = BookUpdateRequest(
            title = "Updated",
            authorIds = listOf(a2Id),
            price = BigDecimal("2000.00"),
            status = PublicationStatus.PUBLISHED.name
        )

        val updated = dsl.transactionResult { cfg ->
            val txDsl = DSL.using(cfg)
            val txService = BookService(txDsl)
            txService.updateBook(created.id, updateReq)
        }

        assertEquals("Updated", updated.title)
        assertEquals(listOf(a2Id), updated.authorIds)
        assertEquals(BigDecimal("2000.00"), updated.price)
        assertEquals(PublicationStatus.PUBLISHED.name, updated.status)
    }

}
