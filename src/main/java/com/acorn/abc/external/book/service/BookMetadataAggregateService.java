package com.acorn.abc.external.book.service;

import com.acorn.abc.common.exception.BusinessException;
import com.acorn.abc.common.exception.ErrorCode;
import com.acorn.abc.external.book.client.AladinBookClient;
import com.acorn.abc.external.book.client.InfoNaruBookClient;
import com.acorn.abc.external.book.client.NaverBookClient;
import com.acorn.abc.external.book.dto.BookMetadataResult;
import com.acorn.abc.external.book.dto.ProviderBookData;
import com.acorn.abc.external.book.dto.ProviderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 정보나루/네이버/알라딘 단건 조회 결과를 병합한다.
 * ISBN13 정규화, 필드 우선순위 적용, provider별 partial success/warning 수집을 담당한다.
 * DB 접근은 하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class BookMetadataAggregateService {

    private final InfoNaruBookClient infoNaruBookClient;
    private final NaverBookClient naverBookClient;
    private final AladinBookClient aladinBookClient;

    public BookMetadataResult aggregate(String rawIsbn13) {
        String isbn13 = normalizeIsbn13(rawIsbn13);

        ProviderBookData infonaru = infoNaruBookClient.fetch(isbn13);
        ProviderBookData naver = naverBookClient.fetch(isbn13);
        ProviderBookData aladin = aladinBookClient.fetch(isbn13);

        List<String> warnings = new ArrayList<>();
        collectWarning(warnings, "정보나루", infonaru.status());
        collectWarning(warnings, "네이버", naver.status());
        collectWarning(warnings, "알라딘", aladin.status());

        String title = firstNonBlank(infonaru.title(), aladin.title(), naver.title());
        String author = firstNonBlank(infonaru.author(), naver.author(), aladin.author());
        String publisher = firstNonBlank(infonaru.publisher(), aladin.publisher(), naver.publisher());
        String coverImageUrl = firstNonBlank(aladin.coverImageUrl(), infonaru.coverImageUrl(), naver.coverImageUrl());
        String description = longest(infonaru.description(), aladin.description(), naver.description());
        String pubDate = firstNonBlank(aladin.pubDate(), naver.pubDate(), infonaru.pubDate());
        String aladinCategoryId = aladin.categoryId();
        String aladinCategoryName = aladin.categoryName();

        if (title == null) {
            warnings.add("제목을 확보하지 못했습니다. 관리자 입력이 필요합니다.");
        }

        return new BookMetadataResult(
                isbn13, title, author, publisher, coverImageUrl, description, pubDate,
                aladinCategoryId, aladinCategoryName,
                infonaru.status(), naver.status(), aladin.status(), warnings
        );
    }

    /**
     * 하이픈/공백 제거 후 13자리 숫자만 허용한다. 아니면 400.
     */
    public String normalizeIsbn13(String rawIsbn13) {
        if (rawIsbn13 == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        String normalized = rawIsbn13.replaceAll("[\\s-]", "");
        if (!normalized.matches("\\d{13}")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return normalized;
    }

    private void collectWarning(List<String> warnings, String providerName, ProviderStatus status) {
        if (status == ProviderStatus.FAILED_RETRYABLE || status == ProviderStatus.FAILED) {
            warnings.add(providerName + " 조회에 실패했습니다.");
        } else if (status == ProviderStatus.SUCCESS_NO_DATA) {
            warnings.add(providerName + " 조회 결과가 없습니다.");
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String longest(String... values) {
        String result = null;
        for (String value : values) {
            if (value != null && !value.isBlank()
                    && (result == null || value.length() > result.length())) {
                result = value;
            }
        }
        return result;
    }
}
