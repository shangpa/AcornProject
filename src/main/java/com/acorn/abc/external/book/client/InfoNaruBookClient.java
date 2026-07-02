package com.acorn.abc.external.book.client;

import com.acorn.abc.external.book.config.BookApiProperties;
import com.acorn.abc.external.book.dto.ProviderBookData;
import com.acorn.abc.external.book.dto.ProviderStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 정보나루 도서 상세 조회 API 단건 조회.
 * GET /api/srchDtlList?authKey=&isbn13={isbn13}&loaninfoYN=Y&format=json
 * 응답: response.detail[0].book.{bookname,authors,publisher,isbn13,bookImageURL,description,class_nm}
 * 무키 상태에서도 0건 응답이 가능하므로 데이터 없음/실패를 구분한다.
 */
@Slf4j
@Component
public class InfoNaruBookClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient infoNaruRestClient;
    private final BookApiProperties properties;

    public InfoNaruBookClient(@Qualifier("infoNaruRestClient") RestClient infoNaruRestClient,
                              BookApiProperties properties) {
        this.infoNaruRestClient = infoNaruRestClient;
        this.properties = properties;
    }

    public ProviderBookData fetch(String isbn13) {
        if (!properties.isEnabled()) {
            return ProviderBookData.notTried();
        }
        String authKey = properties.getInfonaru().getKey();
        if (authKey == null || authKey.isBlank()) {
            return ProviderBookData.notTried();
        }

        try {
            String body = infoNaruRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/srchDtlList")
                            .queryParam("authKey", authKey)
                            .queryParam("isbn13", isbn13)
                            .queryParam("loaninfoYN", "Y")
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .body(String.class);

            if (body == null || body.isBlank()) {
                return ProviderBookData.noData();
            }

            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode detail = root.path("response").path("detail");
            if (!detail.isArray() || detail.isEmpty()) {
                return ProviderBookData.noData();
            }
            JsonNode book = detail.get(0).path("book");
            if (book.isMissingNode() || book.isNull()) {
                return ProviderBookData.noData();
            }
            return new ProviderBookData(
                    ProviderStatus.SUCCESS_HAS_DATA,
                    textOrNull(book, "bookname"),
                    textOrNull(book, "authors"),
                    textOrNull(book, "publisher"),
                    textOrNull(book, "bookImageURL"),
                    textOrNull(book, "description"),
                    textOrNull(book, "publication_year"),
                    null,
                    null
            );
        } catch (Exception e) {
            log.warn("정보나루 API 호출 실패 isbn13={}, message={}", isbn13, e.getMessage());
            return ProviderBookData.failedRetryable();
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }
}
