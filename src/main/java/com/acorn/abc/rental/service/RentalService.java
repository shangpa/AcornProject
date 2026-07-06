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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RentalService {

    private static final Set<String> VALID_RENTAL_STATUSES = Set.of("READY", "READING", "OWNED");
    private static final Set<String> ACTIVE_RENTAL_STATUSES = Set.of("READY", "READING");

    private final BookRentalRepository bookRentalRepository;
    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;
    private final ReadingProgressRepository readingProgressRepository;
    private final NotificationRepository notificationRepository;

    // 뷰어/북마크 등 다른 도메인이 대여 소유권 확인이 필요할 때 이 메서드를 거치도록 한다
    public BookRental getOwnedRentalOrThrow(Long rentalId, Long memberId) {
        BookRental rental = bookRentalRepository.findById(rentalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RENTAL_NOT_FOUND));
        if (!rental.getMember().getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
        }
        // OWNED(소장)는 만료 없음(화면흐름도 02/04 문서 기준). READY/READING만 만료 검증.
        if (!"OWNED".equals(rental.getRentalStatus()) && rental.getRentalEndAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.RENTAL_EXPIRED);
        }
        return rental;
    }

    @Transactional
    public RentalResponse createFreeRental(Long memberId, Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOK_NOT_FOUND));
        if (!"FREE".equals(book.getRentalType())) {
            throw new BusinessException(ErrorCode.BOOK_RENTAL_TYPE_MISMATCH);
        }
        if (!"AVAILABLE".equals(book.getStatus())) {
            throw new BusinessException(ErrorCode.BOOK_NOT_FOUND);
        }
        validateNoActiveRental(memberId, bookId);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        BookRental rental = bookRentalRepository.save(BookRental.create(member, book, calcRentalEndAt(book)));
        book.increaseRentalCount();

        notificationRepository.save(Notification.create(
                member, "RENTAL", "무료 대여가 시작되었습니다.", "RENTAL", rental.getRentalId(), LocalDateTime.now()));

        return new RentalResponse(rental.getRentalId(), book.getBookId(), rental.getRentalStatus(), rental.getRentalEndAt());
    }

    // 무료/유료 대여 생성 전, 동일 도서의 진행 중(READY/READING) 대여가 있는지 검증한다. PaymentService에서도 재사용.
    public void validateNoActiveRental(Long memberId, Long bookId) {
        bookRentalRepository.findFirstByMember_MemberIdAndBook_BookIdOrderByCreatedAtDescRentalIdDesc(memberId, bookId)
                .filter(r -> ACTIVE_RENTAL_STATUSES.contains(r.getRentalStatus()))
                .ifPresent(r -> {
                    throw new BusinessException(ErrorCode.RENTAL_ALREADY_EXISTS);
                });
    }

    public LocalDateTime calcRentalEndAt(Book book) {
        return LocalDateTime.now().plusDays(book.getDefaultRentalDays());
    }

    public PageResponse<MyRentalSummaryResponse> getMyRentals(Long memberId, MyRentalCriteria criteria) {
        String status = criteria.status();
        if (status != null && !VALID_RENTAL_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        Page<BookRental> page = bookRentalRepository.findMyRentals(
                memberId, status, PageRequest.of(criteria.page(), criteria.size()));

        List<MyRentalSummaryResponse> content = page.getContent().stream()
                .map(this::toSummary)
                .toList();

        return PageResponse.of(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    private MyRentalSummaryResponse toSummary(BookRental rental) {
        Book book = rental.getBook();
        ReadingProgress progress = readingProgressRepository.findByRental_RentalId(rental.getRentalId()).orElse(null);
        int currentPage = progress == null ? 0 : progress.getCurrentPage();

        return new MyRentalSummaryResponse(
                rental.getRentalId(),
                book.getBookId(),
                book.getTitle(),
                book.getCoverImageUrl(),
                rental.getRentalStatus(),
                rental.getRentalEndAt(),
                currentPage,
                book.getTotalPages(),
                progressRate(currentPage, book.getTotalPages())
        );
    }

    private double progressRate(int currentPage, int totalPages) {
        if (totalPages <= 0) {
            return 0;
        }
        return Math.min(100.0, currentPage * 100.0 / totalPages);
    }
}
