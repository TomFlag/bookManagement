package com.hata.bookManagement.api.book

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hata.bookManagement.dto.book.BookRequest
import com.hata.bookManagement.dto.book.BookResponse
import com.hata.bookManagement.dto.book.BookUpdateRequest
import com.hata.bookManagement.service.book.BookService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal

@ExtendWith(MockitoExtension::class)
class BookControllerTest {

    private lateinit var mockMvc: MockMvc

    @Mock
    private lateinit var service: BookService

    private val mapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    @BeforeEach
    fun setup() {
        val controller = BookController(service)
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(MappingJackson2HttpMessageConverter(mapper))
            .build()
    }

    @Test
    fun createBook() {
        val req = BookRequest(
            title = "Kafka on the Shore",
            authorIds = listOf(1L, 2L),
            price = BigDecimal("1800.00"),
            status = "AVAILABLE"
        )

        val resp = BookResponse(
            id = 200L,
            title = req.title,
            authorIds = req.authorIds,
            price = req.price ?: BigDecimal.ZERO,
            status = req.status ?: "UNKNOWN"
        )

        Mockito.doReturn(resp).`when`(service).createBook(req)

        val reqJson = mapper.writeValueAsString(req)
        val expectedJson = mapper.writeValueAsString(resp)

        mockMvc.perform(
            post("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reqJson)
        )
            .andExpect(status().isCreated)
            .andExpect(content().json(expectedJson))
    }

    @Test
    fun updateBook() {
        val updateReq = BookUpdateRequest(
            title = "Kafka on the Shore - Revised",
            authorIds = listOf(1L),
            price = BigDecimal("1900.00"),
            status = "AVAILABLE"
        )

        val resp = BookResponse(
            id = 200L,
            title = updateReq.title ?: "",
            authorIds = updateReq.authorIds ?: emptyList(),
            price = updateReq.price ?: BigDecimal.ZERO,
            status = updateReq.status ?: "UNKNOWN"
        )

        Mockito.doReturn(resp).`when`(service).updateBook(200L, updateReq)

        val reqJson = mapper.writeValueAsString(updateReq)
        val expectedJson = mapper.writeValueAsString(resp)

        mockMvc.perform(
            put("/api/books/200")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reqJson)
        )
            .andExpect(status().isOk)
            .andExpect(content().json(expectedJson))
    }

}