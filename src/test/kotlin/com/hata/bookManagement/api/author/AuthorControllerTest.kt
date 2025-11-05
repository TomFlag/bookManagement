package com.hata.bookManagement.api.author

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.databind.SerializationFeature
import com.hata.bookManagement.dto.book.BookResponse
import com.hata.bookManagement.service.author.AuthorService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal

@ExtendWith(MockitoExtension::class)
class AuthorControllerTest {

    private lateinit var mockMvc: MockMvc

    @Mock
    private lateinit var service: AuthorService

    private val mapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    @BeforeEach
    fun setup() {
        val controller = AuthorController(service)
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(MappingJackson2HttpMessageConverter(mapper))
            .build()
    }

    @Test
    fun getBooksByAuthor() {
        // Arrange: prepare a sample BookResponse list returned by the service
        val books = listOf(
            BookResponse(
                id = 1L,
                title = "Kotlin in Action",
                authorIds = listOf(1L),
                price = BigDecimal("2500.00"),
                status = "AVAILABLE"
            )
        )

        Mockito.`when`(service.getBooksByAuthor(1L)).thenReturn(books)

        val expectedJson = mapper.writeValueAsString(books)

        // Act & Assert: call the controller endpoint and verify HTTP 200 and JSON body
        mockMvc.perform(get("/api/authors/1/books"))
            .andExpect(status().isOk)
            .andExpect(content().json(expectedJson))
    }

    @Test
    fun createAuthor() {
        // Arrange
        val req = com.hata.bookManagement.dto.author.AuthorRequest(
            name = "Haruki Murakami",
            birthDate = java.time.LocalDate.of(1949, 1, 1)
        )

        val resp = com.hata.bookManagement.dto.author.AuthorResponse(
            id = 100L,
            name = req.name,
            birthDate = req.birthDate
        )

        Mockito.doReturn(resp).`when`(service).createAuthor(req)

        val reqJson = mapper.writeValueAsString(req)
        val expectedJson = mapper.writeValueAsString(resp)

        // Act & Assert
        mockMvc.perform(post("/api/authors")
            .contentType(MediaType.APPLICATION_JSON)
            .content(reqJson))
            .andExpect(status().isCreated)
            .andExpect(content().json(expectedJson))
    }

    @Test
    fun updateAuthor() {
        // Arrange
        val req = com.hata.bookManagement.dto.author.AuthorUpdateRequest(
            newName = "H. Murakami",
            newBirthDate = null
        )

        val originalBirth = java.time.LocalDate.of(1949, 1, 1)

        val resp = com.hata.bookManagement.dto.author.AuthorResponse(
            id = 100L,
            name = req.newName ?: "Haruki Murakami",
            birthDate = originalBirth
        )

        Mockito.doReturn(resp).`when`(service).updateAuthor(100L, req)

        val reqJson = mapper.writeValueAsString(req)
        val expectedJson = mapper.writeValueAsString(resp)

        // Act & Assert
        mockMvc.perform(put("/api/authors/100")
            .contentType(MediaType.APPLICATION_JSON)
            .content(reqJson))
            .andExpect(status().isOk)
            .andExpect(content().json(expectedJson))
    }

}