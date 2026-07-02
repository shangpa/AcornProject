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
 * 알라딘 상품 조회 API 단건 조회.
 * GET /ttb/api/ItemLookUp.aspx?ttbkey=&itemIdType=ISBN13&ItemId={isbn13}&Output=JS&Version=20131101&Cover=Big
 * JSON 응답이지만 Content-Type이 text/html이라 String으로 받아 ObjectMapper로 직접 파싱한다.
 * 응답에 errorCode가 있으면 오류로 처리한다.
 */
@Slf4j
@Component
public class AladinBookClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient aladinRestClient;
    private final BookApiProperties properties;

    public AladinBookClient(@Qualifier("aladinRestClient") RestClient aladinRestClient,
                            BookApiProperties properties) {
        this.aladinRestClient = aladinRestClient;
        this.properties = properties;
    }

    public ProviderBookData fetch(String isbn13) {
        if (!properties.isEnabled() || properties.getAladin().getKeys().isEmpty()) {
            return ProviderBookData.notTried();
        }
        String ttbKey = properties.getAladin().getKeys().get(0);
        if (ttbKey == null || ttbKey.isBlank()) {
            return ProviderBookData.notTried();
        }

        try {
            String body = aladinRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/ttb/api/ItemLookUp.aspx")
                            .queryParam("ttbkey", ttbKey)
                            .queryParam("itemIdType", "ISBN13")
                            .queryParam("ItemId", isbn13)
                            .queryParam("Output", "JS")
                            .queryParam("Version", "20131101")
                            .queryParam("Cover", "Big")
                            .build())
                    .retrieve()
                    .body(String.class);

            if (body == null || body.isBlank()) {
                return ProviderBookData.noData();
            }

            JsonNode root = OBJECT_MAPPER.readTree(body);
            if (root.has("errorCode")) {
                log.warn("알라딘 API 오류 isbn13={}, errorCode={}", isbn13, root.path("errorCode").asText());
                return ProviderBookData.failed();
            }

            JsonNode items = root.path("item");
            if (!items.isArray() || items.isEmpty()) {
                return ProviderBookData.noData();
            }
            JsonNode item = items.get(0);
            return new ProviderBookData(
                    ProviderStatus.SUCCESS_HAS_DATA,
                    textOrNull(item, "title"),
                    textOrNull(item, "author"),
                    textOrNull(item, "publisher"),
                    textOrNull(item, "cover"),
                    textOrNull(item, "description"),
                    textOrNull(item, "pubDate"),
                    textOrNull(item, "categoryId"),
                    textOrNull(item, "categoryName")
            );
        } catch (Exception e) {
            log.warn("알라딘 API 호출 실패 isbn13={}, message={}", isbn13, e.getMessage());
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
