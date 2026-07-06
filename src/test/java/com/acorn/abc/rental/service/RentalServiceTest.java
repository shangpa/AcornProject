package com.acorn.abc.rental.service;

import com.acorn.abc.book.domain.Book;
import com.acorn.abc.book.repository.BookRepository;
import com.acorn.abc.common.exception.BusinessException;
import com.acorn.abc.common.exception.ErrorCode;
import com.acorn.abc.common.response.PageResponse;
import com.acorn.abc.member.domain.Member;
import com.acorn.abc.member.repository.MemberRepository;
import com.acorn.abc.notification.domain.Notification;
import com.acorn.abc.notification.repository.NotificationRepository;
import com.acorn.abc.rental.domain.BookRental;
import com.acorn.abc.rental.domain.ReadingProgress;
import com.acorn.abc.rental.dto.MyRentalCriteria;
import com.acorn.abc.rental.dto.MyRentalSummaryResponse;
import com.acorn.abc.rental.dto.RentalResponse;
import com.acorn.abc.rental.repository.BookRentalRepository;
import com.acorn.abc.rental.repository.ReadingProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RentalServiceTest {

    @Mock
    private BookRentalRepository bookRentalRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ReadingProgressRepository readingProgressRepository;
    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private RentalService rentalService;

    private Book book;
    private Member member;

    @BeforeEach
    void setUp() {
        book = mock(Book.class);
        member = mock(Member.class);
    }

    @Test
    void createFreeRental_성공하면_대여를_생성하고_알림을_저장한다() {
        given(book.getBookId()).willReturn(1L);
        given(book.getRentalType()).willReturn("FREE");
        given(book.getStatus()).willReturn("AVAILABLE");
        given(book.getDefaultRentalDays()).willReturn(7);
        given(bookRepository.findById(1L)).willReturn(Optional.of(book));
        given(bookRentalRepository.findFirstByMember_MemberIdAndBook_BookIdOrderByCreatedAtDescRentalIdDesc(10L, 1L))
                .willReturn(Optional.empty());
        given(memberRepository.findById(10L)).willReturn(Optional.of(member));

        LocalDateTime rentalEndAt = LocalDateTime.now().plusDays(7);
        BookRental savedRental = mock(BookRental.class);
        given(savedRental.getRentalId()).willReturn(100L);
        given(savedRental.getRentalStatus()).willReturn("READY");
        given(savedRental.getRentalEndAt()).willReturn(rentalEndAt);
        given(bookRentalRepository.save(any(BookRental.class))).willReturn(savedRental);

        RentalResponse response = rentalService.createFreeRental(10L, 1L);

        assertThat(response.rentalId()).isEqualTo(100L);
        assertThat(response.bookId()).isEqualTo(1L);
        assertThat(response.rentalStatus()).isEqualTo("READY");
        assertThat(response.rentalEndAt()).isEqualTo(rentalEndAt);
        verify(book).increaseRentalCount();
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void createFreeRental_유료도서면_BOOK_RENTAL_TYPE_MISMATCH() {
        given(book.getRentalType()).willReturn("PAID");
        given(bookRepository.findById(1L)).willReturn(Optional.of(book));

        assertThatThrownBy(() -> rentalService.createFreeRental(10L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOOK_RENTAL_TYPE_MISMATCH);
        verify(bookRentalRepository, never()).save(any());
    }

    @Test
    void createFreeRental_비공개도서면_BOOK_NOT_FOUND() {
        given(book.getRentalType()).willReturn("FREE");
        given(book.getStatus()).willReturn("HIDDEN");
        given(bookRepository.findById(1L)).willReturn(Optional.of(book));

        assertThatThrownBy(() -> rentalService.createFreeRental(10L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOOK_NOT_FOUND);
        verify(bookRentalRepository, never()).save(any());
    }

    @Test
    void createFreeRental_진행중인_대여가_있으면_RENTAL_ALREADY_EXISTS() {
        given(book.getRentalType()).willReturn("FREE");
        given(book.getStatus()).willReturn("AVAILABLE");
        given(bookRepository.findById(1L)).willReturn(Optional.of(book));

        BookRental existingRental = mock(BookRental.class);
        given(existingRental.getRentalStatus()).willReturn("READING");
        given(bookRentalRepository.findFirstByMember_MemberIdAndBook_BookIdOrderByCreatedAtDescRentalIdDesc(10L, 1L))
                .willReturn(Optional.of(existingRental));

        assertThatThrownBy(() -> rentalService.createFreeRental(10L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RENTAL_ALREADY_EXISTS);
        verify(bookRentalRepository, never()).save(any());
    }

    @Test
    void getMyRentals_잘못된_status면_VALIDATION_ERROR() {
        MyRentalCriteria criteria = MyRentalCriteria.of("INVALID", 0, 10);

        assertThatThrownBy(() -> rentalService.getMyRentals(10L, criteria))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(bookRentalRepository, never()).findMyRentals(any(), any(), any());
    }

    @Test
    void getMyRentals_정상조회시_진행률을_계산한다() {
        MyRentalCriteria criteria = MyRentalCriteria.of("READY", null, null);

        BookRental rental = mock(BookRental.class);
        given(rental.getRentalId()).willReturn(5L);
        given(rental.getBook()).willReturn(book);
        given(rental.getRentalStatus()).willReturn("READY");
        given(book.getBookId()).willReturn(1L);
        given(book.getTitle()).willReturn("title");
        given(book.getCoverImageUrl()).willReturn("cover.jpg");
        given(book.getTotalPages()).willReturn(100);

        given(bookRentalRepository.findMyRentals(10L, "READY", PageRequest.of(0, 10)))
                .willReturn(new PageImpl<>(List.of(rental), PageRequest.of(0, 10), 1));
        given(readingProgressRepository.findByRental_RentalId(5L)).willReturn(Optional.empty());

        PageResponse<MyRentalSummaryResponse> result = rentalService.getMyRentals(10L, criteria);

        assertThat(result.content()).hasSize(1);
        MyRentalSummaryResponse summary = result.content().get(0);
        assertThat(summary.rentalId()).isEqualTo(5L);
        assertThat(summary.currentPage()).isZero();
        assertThat(summary.progressRate()).isZero();
    }
}
