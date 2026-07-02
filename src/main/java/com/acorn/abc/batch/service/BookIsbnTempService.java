package com.acorn.abc.batch.service;

import com.acorn.abc.admin.book.dto.BookIsbnTempFetchResponse;
import com.acorn.abc.admin.book.dto.ExternalBookDuplicateInfo;
import com.acorn.abc.admin.book.dto.ExternalBookLookupResponse;
import com.acorn.abc.admin.book.dto.ExternalBookProviderStatus;
import com.acorn.abc.batch.domain.BookIsbnTemp;
import com.acorn.abc.batch.repository.BookIsbnTempRepository;
import com.acorn.abc.external.book.dto.BookMetadataResult;
import com.acorn.abc.external.book.dto.DuplicateStatus;
import com.acorn.abc.external.book.service.BookMetadataAggregateService;
import com.acorn.abc.external.book.service.DuplicateBookDetector;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 ISBN13 단건 preview / temp 저장(upsert) / 후보 목록 조회.
 */
@Service
@RequiredArgsConstructor
public class BookIsbnTempService {

    private final BookMetadataAggregateService aggregateService;
    private final DuplicateBookDetector duplicateBookDetector;
    private final BookIsbnTempRepository bookIsbnTempRepository;

    /**
     * preview: 외부 API 병합 + 중복 조회. DB 저장 없음.
     */
    @Transactional(readOnly = true)
    public ExternalBookLookupResponse lookup(String rawIsbn13) {
        BookMetadataResult meta = aggregateService.aggregate(rawIsbn13);
        ExternalBookDuplicateInfo duplicate = duplicateBookDetector.detect(
                meta.isbn13(), meta.title(), meta.author(), meta.publisher());

        List<String> warnings = new ArrayList<>(meta.warnings());
        if (DuplicateStatus.BOOK_EXISTS.name().equals(duplicate.status())) {
            warnings.add("이미 같은 ISBN으로 등록된 도서입니다.");
        }

        return new ExternalBookLookupResponse(
                meta.isbn13(),
                meta.title(),
                meta.author(),
                meta.publisher(),
                meta.coverImageUrl(),
                meta.description(),
                meta.pubDate(),
                meta.aladinCategoryId(),
                meta.aladinCategoryName(),
                new ExternalBookProviderStatus(
                        meta.infonaru().name(), meta.naver().name(), meta.aladin().name()),
                duplicate,
                warnings
        );
    }

    /**
     * fetch: 외부 API 병합 후 book_isbn_temp에 insert 또는 update(upsert).
     */
    @Transactional
    public BookIsbnTempFetchResponse fetch(String rawIsbn13) {
        BookMetadataResult meta = aggregateService.aggregate(rawIsbn13);

        BookIsbnTemp temp = bookIsbnTempRepository.findByIsbn13(meta.isbn13())
                .orElseGet(() -> BookIsbnTemp.create(meta.isbn13()));

        temp.applyMetadata(
                meta.title(), meta.publisher(), meta.author(),
                meta.coverImageUrl(), meta.description(), meta.pubDate(),
                meta.aladinCategoryId(), meta.aladinCategoryName(),
                meta.naver().code(), meta.aladin().code());

        if (meta.title() != null && !meta.title().isBlank()) {
            temp.markReady();
        } else {
            temp.markPending();
        }

        BookIsbnTemp saved = bookIsbnTempRepository.save(temp);

        ExternalBookDuplicateInfo duplicate = duplicateBookDetector.detect(
                meta.isbn13(), meta.title(), meta.author(), meta.publisher());

        return new BookIsbnTempFetchResponse(
                saved.getId(), saved.getIsbn13(), saved.getStatusCd(),
                saved.getAladinCategoryId(), saved.getAladinCategoryName(), duplicate);
    }

    @Transactional(readOnly = true)
    public Page<BookIsbnTemp> list(byte statusCd, Pageable pageable) {
        return bookIsbnTempRepository.findByStatusCd(statusCd, pageable);
    }
}
