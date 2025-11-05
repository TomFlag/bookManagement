package com.hata.bookManagement.service.author

import com.hata.bookManagement.dto.author.AuthorRequest
import com.hata.bookManagement.dto.author.AuthorUpdateRequest
import com.hata.jooq.enums.PublicationStatus
import com.hata.jooq.tables.Authors.AUTHORS
import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import org.junit.jupiter.api.AfterAll
import java.math.BigDecimal
import java.time.LocalDate
import javax.sql.DataSource

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

    // Container lifecycle is managed by TestDb (shared per-JVM). Do not stop it here.

    @BeforeEach
    fun cleanup() {
        // Clean tables to ensure test isolation. Use TRUNCATE CASCADE to avoid trigger checks
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

        val req = AuthorUpdateRequest(
            name = "Old Name",
            birthDate = LocalDate.of(1970,3,4),
            newName = "New Name",
            newBirthDate = null
        )

        val resp = service.updateAuthor(req)

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
        val ex = org.junit.jupiter.api.assertThrows<org.springframework.web.server.ResponseStatusException> {
            service.createAuthor(req)
        }
    assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.statusCode)
    }

    @Test
    fun createAuthorBlankName_shouldThrowIllegalArgument() {
        val req = AuthorRequest(name = " ", birthDate = LocalDate.of(1990, 1, 1))

        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            service.createAuthor(req)
        }
    }

}