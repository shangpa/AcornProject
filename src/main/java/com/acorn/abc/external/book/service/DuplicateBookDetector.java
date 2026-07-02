package com.acorn.abc.external.book.service;

import com.acorn.abc.admin.book.dto.ExternalBookDuplicateInfo;
import com.acorn.abc.book.domain.Book;
import com.acorn.abc.book.repository.BookRepository;
import com.acorn.abc.external.book.dto.DuplicateStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 내부 도서 중복 판정.
 * 같은 ISBN은 BOOK_EXISTS(hard block 정보), 제목/저자/출판사 유사는 POSSIBLE_DUPLICATE(warning).
 */
@Service
@RequiredArgsConstructor
public class DuplicateBookDetector {

    private static final int MAX_CANDIDATES = 5;

    private final BookRepository bookRepository;

    @Transactional(readOnly = true)
    public ExternalBookDuplicateInfo detect(String isbn13, String title, String author, String publisher) {
        return bookRepository.findByIsbn(isbn13)
                .map(book -> new ExternalBookDuplicateInfo(
                        DuplicateStatus.BOOK_EXISTS.name(),
                        book.getBookId(),
                        List.of(toCandidate(book))))
                .orElseGet(() -> detectPossible(isbn13, title, author, publisher));
    }

    private ExternalBookDuplicateInfo detectPossible(String isbn13, String title, String author, String publisher) {
        if (title == null || title.isBlank()) {
            return new ExternalBookDuplicateInfo(DuplicateStatus.NONE.name(), null, List.of());
        }
        List<Book> candidates = bookRepository.findPossibleDuplicates(
                isbn13,
                "%" + title + "%",
                publisher,
                publisher == null ? null : "%" + publisher + "%",
                author,
                author == null ? null : "%" + author + "%",
                PageRequest.of(0, MAX_CANDIDATES)
        );
        if (candidates.isEmpty()) {
            return new ExternalBookDuplicateInfo(DuplicateStatus.NONE.name(), null, List.of());
        }
        return new ExternalBookDuplicateInfo(
                DuplicateStatus.POSSIBLE_DUPLICATE.name(),
                null,
                candidates.stream().map(this::toCandidate).toList()
        );
    }

    private ExternalBookDuplicateInfo.Candidate toCandidate(Book book) {
        return new ExternalBookDuplicateInfo.Candidate(
                book.getBookId(),
                book.getTitle(),
                book.getIsbn(),
                book.getStatus()
        );
    }
}
