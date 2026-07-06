package com.acorn.abc.payment.service;

import com.acorn.abc.book.domain.Book;
import com.acorn.abc.book.repository.BookRepository;
import com.acorn.abc.common.exception.BusinessException;
import com.acorn.abc.common.exception.ErrorCode;
import com.acorn.abc.common.response.PageResponse;
import com.acorn.abc.member.domain.Member;
import com.acorn.abc.member.domain.PointHistory;
import com.acorn.abc.member.repository.MemberRepository;
import com.acorn.abc.member.repository.PointHistoryRepository;
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
import com.acorn.abc.rental.domain.BookRental;
import com.acorn.abc.rental.repository.BookRentalRepository;
import com.acorn.abc.rental.service.RentalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private BookRepository bookRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private BookRentalRepository bookRentalRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private MemberCouponRepository memberCouponRepository;
    @Mock
    private CouponUsageRepository couponUsageRepository;
    @Mock
    private PointHistoryRepository pointHistoryRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private RentalService rentalService;

    @InjectMocks
    private PaymentService paymentService;

    private Book book;
    private Member member;

    @BeforeEach
    void setUp() {
        book = mock(Book.class);
        member = mock(Member.class);
    }

    private PaidRentalRequest request(Long memberCouponId, Integer pointAmount) {
        return new PaidRentalRequest(1L, memberCouponId, pointAmount, "dummy-token");
    }

    @Test
    void createPaidRentalAndPayment_쿠폰과_포인트를_함께_사용하면_금액이_정확히_계산된다() {
        given(book.getRentalType()).willReturn("PAID");
        given(book.getStatus()).willReturn("AVAILABLE");
        given(book.getRentalPrice()).willReturn(12901);
        given(bookRepository.findById(1L)).willReturn(Optional.of(book));
        given(memberRepository.findById(10L)).willReturn(Optional.of(member));

        Coupon coupon = mock(Coupon.class);
        given(coupon.getBenefitUnit()).willReturn("PERCENT");
        given(coupon.getBenefitValue()).willReturn(5);
        MemberCoupon memberCoupon = mock(MemberCoupon.class);
        given(memberCoupon.getCoupon()).willReturn(coupon);
        given(memberCouponRepository.findByMemberCouponIdAndMember_MemberId(50L, 10L))
                .willReturn(Optional.of(memberCoupon));

        LocalDateTime rentalEndAt = LocalDateTime.now().plusDays(30);
        given(rentalService.calcRentalEndAt(book)).willReturn(rentalEndAt);
        BookRental savedRental = mock(BookRental.class);
        given(savedRental.getRentalId()).willReturn(200L);
        given(bookRentalRepository.save(any(BookRental.class))).willReturn(savedRental);

        Payment savedPayment = mock(Payment.class);
        given(savedPayment.getPaymentId()).willReturn(300L);
        given(savedPayment.getPaymentMethod()).willReturn("CARD");
        given(savedPayment.getPaymentStatus()).willReturn("PAID");
        given(paymentRepository.save(any(Payment.class))).willReturn(savedPayment);

        PaymentCreateResponse response = paymentService.createPaidRentalAndPayment(10L, request(50L, 1000));

        // 5% 할인 = 12901*0.05 = 645.05 -> 버림 645, 잔여 12256 - 포인트 1000 = 11256
        assertThat(response.rentalId()).isEqualTo(200L);
        assertThat(response.paymentId()).isEqualTo(300L);
        assertThat(response.originalAmount()).isEqualTo(12901);
        assertThat(response.couponDiscountAmount()).isEqualTo(645);
        assertThat(response.pointUsedAmount()).isEqualTo(1000);
        assertThat(response.amount()).isEqualTo(11256);
        assertThat(response.paymentMethod()).isEqualTo("CARD");
        assertThat(response.paymentStatus()).isEqualTo("PAID");

        verify(memberCoupon).use(any(LocalDateTime.class));
        verify(couponUsageRepository).save(any(CouponUsage.class));
        verify(member).usePoint(1000);
        verify(pointHistoryRepository).save(any(PointHistory.class));
        verify(book).increaseRentalCount();
        verify(notificationRepository, times(2)).save(any());
    }

    @Test
    void createPaidRentalAndPayment_무료도서면_BOOK_RENTAL_TYPE_MISMATCH() {
        given(book.getRentalType()).willReturn("FREE");
        given(bookRepository.findById(1L)).willReturn(Optional.of(book));

        assertThatThrownBy(() -> paymentService.createPaidRentalAndPayment(10L, request(null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOOK_RENTAL_TYPE_MISMATCH);
        verify(bookRentalRepository, never()).save(any());
    }

    @Test
    void createPaidRentalAndPayment_비공개도서면_BOOK_NOT_FOUND() {
        given(book.getRentalType()).willReturn("PAID");
        given(book.getStatus()).willReturn("HIDDEN");
        given(bookRepository.findById(1L)).willReturn(Optional.of(book));

        assertThatThrownBy(() -> paymentService.createPaidRentalAndPayment(10L, request(null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOOK_NOT_FOUND);
    }

    @Test
    void createPaidRentalAndPayment_진행중인_대여가_있으면_예외가_전파된다() {
        given(book.getRentalType()).willReturn("PAID");
        given(book.getStatus()).willReturn("AVAILABLE");
        given(bookRepository.findById(1L)).willReturn(Optional.of(book));
        willThrow(new BusinessException(ErrorCode.RENTAL_ALREADY_EXISTS))
                .given(rentalService).validateNoActiveRental(anyLong(), anyLong());

        assertThatThrownBy(() -> paymentService.createPaidRentalAndPayment(10L, request(null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RENTAL_ALREADY_EXISTS);
        verify(memberRepository, never()).findById(any());
    }

    @Test
    void createPaidRentalAndPayment_쿠폰이_없으면_MEMBER_COUPON_NOT_USABLE() {
        given(book.getRentalType()).willReturn("PAID");
        given(book.getStatus()).willReturn("AVAILABLE");
        given(bookRepository.findById(1L)).willReturn(Optional.of(book));
        given(memberRepository.findById(10L)).willReturn(Optional.of(member));
        given(memberCouponRepository.findByMemberCouponIdAndMember_MemberId(50L, 10L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createPaidRentalAndPayment(10L, request(50L, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_COUPON_NOT_USABLE);
        verify(bookRentalRepository, never()).save(any());
    }

    @Test
    void createPaidRentalAndPayment_포인트가_잔여금액을_초과하면_VALIDATION_ERROR() {
        given(book.getRentalType()).willReturn("PAID");
        given(book.getStatus()).willReturn("AVAILABLE");
        given(book.getRentalPrice()).willReturn(10000);
        given(bookRepository.findById(1L)).willReturn(Optional.of(book));
        given(memberRepository.findById(10L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> paymentService.createPaidRentalAndPayment(10L, request(null, 10001)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(bookRentalRepository, never()).save(any());
    }

    @Test
    void createPaidRentalAndPayment_포인트_잔액이_부족하면_예외가_전파된다() {
        given(book.getRentalType()).willReturn("PAID");
        given(book.getStatus()).willReturn("AVAILABLE");
        given(book.getRentalPrice()).willReturn(10000);
        given(bookRepository.findById(1L)).willReturn(Optional.of(book));
        given(memberRepository.findById(10L)).willReturn(Optional.of(member));

        given(rentalService.calcRentalEndAt(book)).willReturn(LocalDateTime.now().plusDays(30));
        BookRental savedRental = mock(BookRental.class);
        given(bookRentalRepository.save(any(BookRental.class))).willReturn(savedRental);
        Payment savedPayment = mock(Payment.class);
        given(paymentRepository.save(any(Payment.class))).willReturn(savedPayment);

        willThrow(new BusinessException(ErrorCode.POINT_BALANCE_INSUFFICIENT))
                .given(member).usePoint(anyInt());

        assertThatThrownBy(() -> paymentService.createPaidRentalAndPayment(10L, request(null, 500)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POINT_BALANCE_INSUFFICIENT);
        verify(pointHistoryRepository, never()).save(any());
    }

    @Test
    void getMyPayments_쿠폰과_포인트_사용내역을_함께_반환한다() {
        MyPaymentCriteria criteria = MyPaymentCriteria.of(null, null, null, null);

        Payment payment = mock(Payment.class);
        given(payment.getPaymentId()).willReturn(300L);
        BookRental rental = mock(BookRental.class);
        given(rental.getRentalId()).willReturn(200L);
        given(rental.getBook()).willReturn(book);
        given(payment.getRental()).willReturn(rental);
        given(book.getBookId()).willReturn(1L);

        given(paymentRepository.findMyPayments(10L, null, null, PageRequest.of(0, 10)))
                .willReturn(new PageImpl<>(List.of(payment), PageRequest.of(0, 10), 1));

        CouponUsage couponUsage = mock(CouponUsage.class);
        given(couponUsage.getDiscountAmount()).willReturn(645);
        given(couponUsageRepository.findByPayment_PaymentId(300L)).willReturn(Optional.of(couponUsage));

        PointHistory pointHistory = mock(PointHistory.class);
        given(pointHistory.getPointAmount()).willReturn(-1000);
        given(pointHistoryRepository.findFirstByPayment_PaymentId(300L)).willReturn(Optional.of(pointHistory));

        PageResponse<MyPaymentSummaryResponse> result = paymentService.getMyPayments(10L, criteria);

        MyPaymentSummaryResponse summary = result.content().get(0);
        assertThat(summary.couponDiscountAmount()).isEqualTo(645);
        assertThat(summary.pointUsedAmount()).isEqualTo(1000);
    }

    @Test
    void getMyPayments_쿠폰과_포인트를_사용하지_않았으면_0을_반환한다() {
        MyPaymentCriteria criteria = MyPaymentCriteria.of(null, null, null, null);

        Payment payment = mock(Payment.class);
        given(payment.getPaymentId()).willReturn(300L);
        BookRental rental = mock(BookRental.class);
        given(rental.getBook()).willReturn(book);
        given(payment.getRental()).willReturn(rental);
        given(book.getBookId()).willReturn(1L);

        given(paymentRepository.findMyPayments(10L, null, null, PageRequest.of(0, 10)))
                .willReturn(new PageImpl<>(List.of(payment), PageRequest.of(0, 10), 1));
        given(couponUsageRepository.findByPayment_PaymentId(300L)).willReturn(Optional.empty());
        given(pointHistoryRepository.findFirstByPayment_PaymentId(300L)).willReturn(Optional.empty());

        PageResponse<MyPaymentSummaryResponse> result = paymentService.getMyPayments(10L, criteria);

        MyPaymentSummaryResponse summary = result.content().get(0);
        assertThat(summary.couponDiscountAmount()).isZero();
        assertThat(summary.pointUsedAmount()).isZero();
    }
}
