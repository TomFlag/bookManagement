package com.hata.bookManagement.service.author

import com.hata.bookManagement.dto.author.AuthorRequest
import com.hata.bookManagement.dto.author.AuthorUpdateRequest
import com.hata.jooq.enums.PublicationStatus
import com.hata.jooq.tables.Authors.AUTHORS
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import java.math.BigDecimal
import java.time.LocalDate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthorServiceTest {

    private lateinit var dsl: DSLContext
    private lateinit var service: AuthorService
    private lateinit var postgresContainer: PostgreSQLContainer<*>

    @BeforeAll
    fun setupAll() {
        val (d, container) = com.hata.bookManagement.test.TestDb.start()
        dsl = d
        postgresContainer = container
        service = AuthorService(dsl)
    }

    @BeforeEach
    fun cleanup() {
        dsl.execute("TRUNCATE TABLE public.book_authors, public.books, public.authors RESTART IDENTITY CASCADE")
    }

    @Test
    fun createAuthor() {
        val req = AuthorRequest(name = "Test Author", birthDate = LocalDate.of(1980, 5, 2))

        val resp = service.createAuthor(req)

        assertNotNull(resp.id)
        assertEquals(req.name, resp.name)
        assertEquals(req.birthDate, resp.birthDate)

        // verify persisted
        val stored = dsl.select(AUTHORS.ID, AUTHORS.NAME).from(AUTHORS).where(AUTHORS.ID.eq(resp.id)).fetchOne()
        assertNotNull(stored)
    }

    @Test
    fun updateAuthor() {
        // insert initial author
        val initial = dsl.insertInto(AUTHORS)
            .set(AUTHORS.NAME, "Old Name")
            .set(AUTHORS.BIRTH_DATE, LocalDate.of(1970,3,4).atStartOfDay().atOffset(java.time.ZoneOffset.ofHours(9)))
            .returning(AUTHORS.ID)
            .fetchOne()!!

        val authorId = initial.getValue(AUTHORS.ID) as Long

        val req = AuthorUpdateRequest(
            newName = "New Name",
            newBirthDate = null
        )

        val resp = service.updateAuthor(authorId, req)

        assertEquals("New Name", resp.name)
    }

    @Test
    fun getBooksByAuthor() {
        // create author
        val authorRec = dsl.insertInto(AUTHORS)
            .set(AUTHORS.NAME, "Author A")
            .set(AUTHORS.BIRTH_DATE, LocalDate.of(1980,1,1).atStartOfDay().atOffset(java.time.ZoneOffset.ofHours(9)))
            .returning(AUTHORS.ID)
            .fetchOne()!!

        val authorId = authorRec.getValue(AUTHORS.ID) as Long

        // Insert book and book_author in a single statement (CTE) so the DB check for
        // 'books must have authors' does not fail mid-statement.
        val rec = dsl.resultQuery(
            "WITH b AS (INSERT INTO public.books (title, price, status) VALUES (?, ?, cast(? as public.publication_status)) RETURNING id) " +
                    "INSERT INTO public.book_authors (book_id, author_id, author_order) SELECT b.id, ?, 1 FROM b RETURNING book_id",
            "Book Title",
            BigDecimal("1500.00"),
            PublicationStatus.PUBLISHED.name,
            authorId
        ).fetchOne()!!

        val bookId = rec.getValue(0) as Long

        val books = service.getBooksByAuthor(authorId)

        assertEquals(1, books.size)
        val b = books[0]
        assertEquals(bookId, b.id)
        assertEquals("Book Title", b.title)
    }

    @Test
    fun createAuthorDuplicate_shouldReturnConflict() {
        val req = AuthorRequest(name = "Dup Author", birthDate = LocalDate.of(1990, 1, 1))

        // first insert should succeed
        val first = service.createAuthor(req)
        assertNotNull(first.id)

        // second insert with same name+birthDate should fail due to unique constraint
        org.junit.jupiter.api.assertThrows<com.hata.bookManagement.exception.ConflictException> {
            service.createAuthor(req)
        }
    }

    @Test
    fun createAuthorBlankName_shouldThrowIllegalArgument() {
        val req = AuthorRequest(name = " ", birthDate = LocalDate.of(1990, 1, 1))

            org.junit.jupiter.api.assertThrows<com.hata.bookManagement.exception.BadRequestException> {
            service.createAuthor(req)
        }
    }

    @Test
    fun createAuthorFutureBirthDate_shouldThrowBadRequest() {
        val tomorrow = LocalDate.now().plusDays(1)
        val req = AuthorRequest(name = "Future Author", birthDate = tomorrow)

        org.junit.jupiter.api.assertThrows<com.hata.bookManagement.exception.BadRequestException> {
            service.createAuthor(req)
        }
    }

    @Test
    fun createAuthorBirthDateNotFuture_allowed() {
        // use a date that is guaranteed not-future in JST to avoid timezone race
        val jst = java.time.ZoneId.of("Asia/Tokyo")
        val safeDate = LocalDate.now(jst).minusDays(1)
        // use a unique name to avoid accidental unique-constraint collisions across runs
        val uniqueName = "Today Author ${System.currentTimeMillis()}"
        val req = AuthorRequest(name = uniqueName, birthDate = safeDate)

        val resp = service.createAuthor(req)

        assertNotNull(resp.id)
        assertEquals(req.birthDate, resp.birthDate)
    }

    @Test
    fun updateAuthor_notFound_shouldThrowNotFound() {
        val req = AuthorUpdateRequest(newName = "X", newBirthDate = null)

        org.junit.jupiter.api.assertThrows<com.hata.bookManagement.exception.NotFoundException> {
            service.updateAuthor(99999L, req)
        }
    }

    @Test
    fun updateAuthor_nothingToUpdate_shouldThrowBadRequest() {
        // insert initial author
        val initial = dsl.insertInto(AUTHORS)
            .set(AUTHORS.NAME, "Old Name2")
            .set(AUTHORS.BIRTH_DATE, LocalDate.of(1970,3,4).atStartOfDay().atOffset(java.time.ZoneOffset.ofHours(9)))
            .returning(AUTHORS.ID)
            .fetchOne()!!

        val authorId = initial.getValue(AUTHORS.ID) as Long

        val req = AuthorUpdateRequest(newName = null, newBirthDate = null)

        org.junit.jupiter.api.assertThrows<com.hata.bookManagement.exception.BadRequestException> {
            service.updateAuthor(authorId, req)
        }
    }

    @Test
    fun updateAuthor_blankNewName_shouldThrowBadRequest() {
        // insert initial author
        val initial = dsl.insertInto(AUTHORS)
            .set(AUTHORS.NAME, "Old Name3")
            .set(AUTHORS.BIRTH_DATE, LocalDate.of(1970,3,4).atStartOfDay().atOffset(java.time.ZoneOffset.ofHours(9)))
            .returning(AUTHORS.ID)
            .fetchOne()!!

        val authorId = initial.getValue(AUTHORS.ID) as Long

        val req = AuthorUpdateRequest(newName = " ", newBirthDate = null)

        org.junit.jupiter.api.assertThrows<com.hata.bookManagement.exception.BadRequestException> {
            service.updateAuthor(authorId, req)
        }
    }

    @Test
    fun updateAuthor_futureNewBirthDate_shouldThrowBadRequest() {
        // insert initial author
        val initial = dsl.insertInto(AUTHORS)
            .set(AUTHORS.NAME, "Old Name4")
            .set(AUTHORS.BIRTH_DATE, LocalDate.of(1970,3,4).atStartOfDay().atOffset(java.time.ZoneOffset.ofHours(9)))
            .returning(AUTHORS.ID)
            .fetchOne()!!

        val authorId = initial.getValue(AUTHORS.ID) as Long

        val tomorrow = LocalDate.now().plusDays(1)
        val req = AuthorUpdateRequest(newName = null, newBirthDate = tomorrow)

        org.junit.jupiter.api.assertThrows<com.hata.bookManagement.exception.BadRequestException> {
            service.updateAuthor(authorId, req)
        }
    }

}