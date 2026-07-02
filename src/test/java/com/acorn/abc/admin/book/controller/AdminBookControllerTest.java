package com.acorn.abc.admin.book.controller;

import com.acorn.abc.admin.book.dto.BookIsbnTempApproveResponse;
import com.acorn.abc.admin.book.dto.BookIsbnTempFetchResponse;
import com.acorn.abc.admin.book.dto.ExternalBookDuplicateInfo;
import com.acorn.abc.admin.book.dto.ExternalBookLookupResponse;
import com.acorn.abc.admin.book.dto.ExternalBookProviderStatus;
import com.acorn.abc.batch.domain.BookIsbnTemp;
import com.acorn.abc.batch.service.BookApprovalService;
import com.acorn.abc.batch.service.BookIsbnTempService;
import com.acorn.abc.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminBookControllerTest {

    private final BookIsbnTempService bookIsbnTempService = mock(BookIsbnTempService.class);
    private final BookApprovalService bookApprovalService = mock(BookApprovalService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AdminBookMetadataController(bookIsbnTempService),
                        new AdminBookIsbnTempController(bookIsbnTempService, bookApprovalService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void lookup_returnsMergedMetadata() throws Exception {
        ExternalBookLookupResponse response = new ExternalBookLookupResponse(
                "9788966260959", "클린코드", "마틴", "인사이트", "http://cover", "desc", "2013-12-24", "2724",
                "국내도서 > 컴퓨터/모바일 > 프로그래밍 > 개발방법론",
                new ExternalBookProviderStatus("SUCCESS_HAS_DATA", "SUCCESS_HAS_DATA", "SUCCESS_HAS_DATA"),
                new ExternalBookDuplicateInfo("NONE", null, List.of()), List.of());
        when(bookIsbnTempService.lookup("9788966260959")).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/books/external/lookup").param("isbn13", "9788966260959"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isbn13").value("9788966260959"))
                .andExpect(jsonPath("$.data.providers.naver").value("SUCCESS_HAS_DATA"));
    }

    @Test
    void fetch_upsertsTemp() throws Exception {
        when(bookIsbnTempService.fetch("9788966260959")).thenReturn(new BookIsbnTempFetchResponse(
                35L, "9788966260959", (byte) 1, "2724", "국내도서 > 컴퓨터/모바일 > 프로그래밍 > 개발방법론",
                new ExternalBookDuplicateInfo("NONE", null, List.of())));

        mockMvc.perform(post("/api/v1/admin/book-isbn-temps/fetch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isbn13\":\"9788966260959\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tempId").value(35))
                .andExpect(jsonPath("$.data.statusCd").value(1));
    }

    @Test
    void fetch_rejectsBlankIsbn() throws Exception {
        mockMvc.perform(post("/api/v1/admin/book-isbn-temps/fetch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isbn13\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void list_returnsPageResponse() throws Exception {
        BookIsbnTemp temp = BookIsbnTemp.create("9788966260959");
        temp.applyMetadata("클린코드", "인사이트", "마틴", null, null, "2013", null, null, (byte) 1, (byte) 1);
        temp.markReady();
        Page<BookIsbnTemp> page = new PageImpl<>(List.of(temp), PageRequest.of(0, 20), 1);
        when(bookIsbnTempService.list(anyByte(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/book-isbn-temps").param("status", "READY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].isbn13").value("9788966260959"));
    }

    @Test
    void approve_returnsCreated() throws Exception {
        when(bookApprovalService.approve(eq(35L), any()))
                .thenReturn(new BookIsbnTempApproveResponse(12L, 35L, "AVAILABLE"));

        String body = """
                {
                  "title": "클린코드",
                  "publisherName": "인사이트",
                  "authors": ["로버트 마틴"],
                  "categoryIds": [1],
                  "rentalType": "PAID",
                  "rentalPrice": 5000,
                  "status": "AVAILABLE",
                  "detail": {"description": "설명"},
                  "pages": [{"pageNo": 1, "pageContent": "본문"}]
                }
                """;

        mockMvc.perform(post("/api/v1/admin/book-isbn-temps/35/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("CREATED"))
                .andExpect(jsonPath("$.data.bookId").value(12));
    }

    @Test
    void approve_rejectsInvalidRequest() throws Exception {
        String body = """
                {
                  "title": "",
                  "publisherName": "인사이트",
                  "authors": [],
                  "categoryIds": [],
                  "rentalType": "PAID",
                  "rentalPrice": 5000,
                  "status": "AVAILABLE"
                }
                """;

        mockMvc.perform(post("/api/v1/admin/book-isbn-temps/35/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
