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
 * 네이버 책 검색 API 단건 조회.
 * GET /v1/search/book.json?query={isbn13}&display=1
 * 헤더: X-Naver-Client-Id / X-Naver-Client-Secret
 */
@Slf4j
@Component
public class NaverBookClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient naverRestClient;
    private final BookApiProperties properties;

    public NaverBookClient(@Qualifier("naverRestClient") RestClient naverRestClient,
                           BookApiProperties properties) {
        this.naverRestClient = naverRestClient;
        this.properties = properties;
    }

    public ProviderBookData fetch(String isbn13) {
        if (!properties.isEnabled() || properties.getNaver().getClients().isEmpty()) {
            return ProviderBookData.notTried();
        }
        BookApiProperties.NaverClient client = properties.getNaver().getClients().get(0);
        if (client.getId() == null || client.getId().isBlank()
                || client.getSecret() == null || client.getSecret().isBlank()) {
            return ProviderBookData.notTried();
        }

        try {
            String body = naverRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v1/search/book.json")
                            .queryParam("query", isbn13)
                            .queryParam("display", 1)
                            .build())
                    .header("X-Naver-Client-Id", client.getId())
                    .header("X-Naver-Client-Secret", client.getSecret())
                    .retrieve()
                    .body(String.class);

            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode items = root.path("items");
            if (!items.isArray() || items.isEmpty()) {
                return ProviderBookData.noData();
            }
            JsonNode item = items.get(0);
            return new ProviderBookData(
                    ProviderStatus.SUCCESS_HAS_DATA,
                    textOrNull(item, "title"),
                    textOrNull(item, "author"),
                    textOrNull(item, "publisher"),
                    textOrNull(item, "image"),
                    textOrNull(item, "description"),
                    textOrNull(item, "pubdate"),
                    null,
                    null
            );
        } catch (Exception e) {
            log.warn("네이버 도서 API 호출 실패 isbn13={}, message={}", isbn13, e.getMessage());
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
