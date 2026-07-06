package com.acorn.abc.payment.service;

import com.acorn.abc.book.domain.Book;
import com.acorn.abc.book.repository.BookRepository;
import com.acorn.abc.common.exception.BusinessException;
import com.acorn.abc.common.exception.ErrorCode;
import com.acorn.abc.common.response.PageResponse;
import com.acorn.abc.member.domain.Member;
import com.acorn.abc.member.repository.MemberRepository;
import com.acorn.abc.member.repository.PointHistoryRepository;
import com.acorn.abc.notification.domain.Notification;
import com.acorn.abc.notification.repository.NotificationRepository;
import com.acorn.abc.payment.domain.Coupon;
import com.acorn.abc.payment.domain.CouponUsage;
import com.acorn.abc.payment.domain.MemberCoupon;
import com.acorn.abc.payment.domain.Payment;
import com.acorn.abc.payment.dto.MyPaymentCriteria;
import com.acorn.abc.payment.dto.MyPaymentSummaryResponse;
import com.acorn.abc.payment.dto.PaidRentalRequest;
import com.acorn.abc.payment.dto.PaymentCreateResponse;
import com.acorn.abc.payment.repository.CouponUsageRepository;
import com.acorn.abc.payment.repository.MemberCouponRepository;
import com.acorn.abc.payment.repository.PaymentRepository;
import com.acorn.abc.member.domain.PointHistory;
import com.acorn.abc.rental.domain.BookRental;
import com.acorn.abc.rental.repository.BookRentalRepository;
import com.acorn.abc.rental.service.RentalService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;
    private final BookRentalRepository bookRentalRepository;
    private final PaymentRepository paymentRepository;
    private final MemberCouponRepository memberCouponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final NotificationRepository notificationRepository;
    private final RentalService rentalService;

    @Transactional
    public PaymentCreateResponse createPaidRentalAndPayment(Long memberId, PaidRentalRequest request) {
        // 1. Book 조회 + AVAILABLE/PAID 검증
        Book book = bookRepository.findById(request.bookId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOK_NOT_FOUND));
        if (!"PAID".equals(book.getRentalType())) {
            throw new BusinessException(ErrorCode.BOOK_RENTAL_TYPE_MISMATCH);
        }
        if (!"AVAILABLE".equals(book.getStatus())) {
            throw new BusinessException(ErrorCode.BOOK_NOT_FOUND);
        }

        // 2. READY/READING 중복 대여 검증
        rentalService.validateNoActiveRental(memberId, request.bookId());

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // 3. 쿠폰 검증 + 할인액 계산
        MemberCoupon memberCoupon = null;
        int couponDiscount = 0;
        if (request.memberCouponId() != null) {
            memberCoupon = memberCouponRepository.findByMemberCouponIdAndMember_MemberId(request.memberCouponId(), memberId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_COUPON_NOT_USABLE));
            couponDiscount = calcCouponDiscount(memberCoupon.getCoupon(), book.getRentalPrice());
        }

        // 4. 포인트 검증 (잔액 부족 여부는 8번의 Member.usePoint()에서 최종 검증)
        int pointUsed = request.pointAmount() == null ? 0 : request.pointAmount();
        int remaining = book.getRentalPrice() - couponDiscount;
        if (pointUsed > remaining) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        LocalDateTime now = LocalDateTime.now();
        int amount = remaining - pointUsed;

        // 5. BookRental 저장
        BookRental rental = bookRentalRepository.save(
                BookRental.create(member, book, rentalService.calcRentalEndAt(book)));

        // 6. Payment 저장
        Payment payment = paymentRepository.save(
                Payment.create(member, rental, book.getRentalPrice(), amount, now));

        // 7. CouponUsage 저장 + MemberCoupon.use()
        if (memberCoupon != null) {
            memberCoupon.use(now);
            couponUsageRepository.save(CouponUsage.create(memberCoupon, rental, payment, couponDiscount, now));
        }

        // 8. PointHistory 저장 + Member.usePoint()
        if (pointUsed > 0) {
            member.usePoint(pointUsed);
            pointHistoryRepository.save(PointHistory.createPaymentUse(member, payment, pointUsed, now));
        }

        // 9. Book.increaseRentalCount()
        book.increaseRentalCount();

        // 10. Notification 저장 (알림 유형 값은 notification.notification_type CHECK 제약의 기존 대소문자 표기를 그대로 따른다)
        notificationRepository.save(Notification.create(member, "RENTAL", "유료 대여가 시작되었습니다.", "RENTAL", rental.getRentalId(), now));
        notificationRepository.save(Notification.create(member, "payment", "결제가 완료되었습니다.", "PAYMENT", payment.getPaymentId(), now));

        return new PaymentCreateResponse(
                rental.getRentalId(),
                payment.getPaymentId(),
                book.getRentalPrice(),
                couponDiscount,
                pointUsed,
                amount,
                payment.getPaymentMethod(),
                payment.getPaymentStatus()
        );
    }

    private int calcCouponDiscount(Coupon coupon, int originalAmount) {
        if ("PERCENT".equals(coupon.getBenefitUnit())) {
            int discount = (int) Math.floor(originalAmount * coupon.getBenefitValue() / 100.0);
            return Math.min(discount, originalAmount);
        }
        return Math.min(coupon.getBenefitValue(), originalAmount);
    }

    public PageResponse<MyPaymentSummaryResponse> getMyPayments(Long memberId, MyPaymentCriteria criteria) {
        LocalDateTime from = criteria.from() == null ? null : criteria.from().atStartOfDay();
        LocalDateTime to = criteria.to() == null ? null : criteria.to().plusDays(1).atStartOfDay();

        Page<Payment> page = paymentRepository.findMyPayments(
                memberId, from, to, PageRequest.of(criteria.page(), criteria.size()));

        List<MyPaymentSummaryResponse> content = page.getContent().stream()
                .map(this::toSummary)
                .toList();

        return PageResponse.of(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    private MyPaymentSummaryResponse toSummary(Payment payment) {
        BookRental rental = payment.getRental();
        Book book = rental.getBook();

        int couponDiscountAmount = couponUsageRepository.findByPayment_PaymentId(payment.getPaymentId())
                .map(CouponUsage::getDiscountAmount)
                .orElse(0);
        int pointUsedAmount = pointHistoryRepository.findFirstByPayment_PaymentId(payment.getPaymentId())
                .map(history -> -history.getPointAmount())
                .orElse(0);

        return new MyPaymentSummaryResponse(
                payment.getPaymentId(),
                rental.getRentalId(),
                book.getBookId(),
                book.getTitle(),
                book.getCoverImageUrl(),
                payment.getOriginalAmount(),
                couponDiscountAmount,
                pointUsedAmount,
                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getPaymentStatus(),
                payment.getPaidAt()
        );
    }
}
