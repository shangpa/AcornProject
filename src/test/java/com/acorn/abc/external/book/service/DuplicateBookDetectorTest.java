package com.acorn.abc.external.book.service;

import com.acorn.abc.admin.book.dto.ExternalBookDuplicateInfo;
import com.acorn.abc.book.domain.Book;
import com.acorn.abc.book.repository.BookRepository;
import com.acorn.abc.external.book.dto.DuplicateStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuplicateBookDetectorTest {

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private DuplicateBookDetector detector;

    @Test
    void detect_returnsBookExistsWhenSameIsbn() {
        Book book = org.mockito.Mockito.mock(Book.class);
        when(book.getBookId()).thenReturn(12L);
        when(book.getTitle()).thenReturn("클린코드");
        when(book.getIsbn()).thenReturn("9788966260959");
        when(book.getStatus()).thenReturn("AVAILABLE");
        when(bookRepository.findByIsbn("9788966260959")).thenReturn(Optional.of(book));

        ExternalBookDuplicateInfo info = detector.detect("9788966260959", "클린코드", "마틴", "인사이트");

        assertThat(info.status()).isEqualTo(DuplicateStatus.BOOK_EXISTS.name());
        assertThat(info.existingBookId()).isEqualTo(12L);
        assertThat(info.candidates()).hasSize(1);
    }

    @Test
    void detect_returnsPossibleDuplicateWhenSimilarTitle() {
        Book candidate = org.mockito.Mockito.mock(Book.class);
        when(candidate.getBookId()).thenReturn(7L);
        when(candidate.getTitle()).thenReturn("클린코드 개정판");
        when(candidate.getIsbn()).thenReturn("9999999999999");
        when(candidate.getStatus()).thenReturn("AVAILABLE");
        when(bookRepository.findByIsbn(anyString())).thenReturn(Optional.empty());
        when(bookRepository.findPossibleDuplicates(anyString(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(candidate));

        ExternalBookDuplicateInfo info = detector.detect("9788966260959", "클린코드", "마틴", "인사이트");

        assertThat(info.status()).isEqualTo(DuplicateStatus.POSSIBLE_DUPLICATE.name());
        assertThat(info.existingBookId()).isNull();
        assertThat(info.candidates()).hasSize(1);
    }

    @Test
    void detect_returnsNoneWhenNoMatch() {
        when(bookRepository.findByIsbn(anyString())).thenReturn(Optional.empty());
        when(bookRepository.findPossibleDuplicates(anyString(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        ExternalBookDuplicateInfo info = detector.detect("9788966260959", "클린코드", "마틴", "인사이트");

        assertThat(info.status()).isEqualTo(DuplicateStatus.NONE.name());
    }

    @Test
    void detect_skipsPossibleLookupWhenTitleBlank() {
        lenient().when(bookRepository.findByIsbn(anyString())).thenReturn(Optional.empty());

        ExternalBookDuplicateInfo info = detector.detect("9788966260959", null, "마틴", "인사이트");

        assertThat(info.status()).isEqualTo(DuplicateStatus.NONE.name());
        verify(bookRepository, never()).findPossibleDuplicates(any(), any(), any(), any(), any(), any(), any());
    }
}
