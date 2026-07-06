package com.acorn.abc.member.domain;

import com.acorn.abc.common.domain.BaseEntity;
import com.acorn.abc.common.exception.BusinessException;
import com.acorn.abc.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "login_id", nullable = false, length = 50)
    private String loginId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id", nullable = false)
    private MemberGrade grade;

    @Column(name = "grade_expires_at")
    private LocalDateTime gradeExpiresAt;

    @Column(name = "grade_payment_started_at")
    private LocalDateTime gradePaymentStartedAt;

    @Column(name = "point_balance", nullable = false)
    private int pointBalance;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    @Column(name = "anonymized_at")
    private LocalDateTime anonymizedAt;

    @Column(name = "withdrawal_reason", length = 255)
    private String withdrawalReason;

    // TODO: 기본 등급 정책 확정 전까지 grade_id=1 사용
    public static Member create(String loginId, String passwordHash, String name, String email,
                                String phone, LocalDate birthDate, String gender, MemberGrade grade) {
        Member member = new Member();
        member.loginId = loginId;
        member.passwordHash = passwordHash;
        member.name = name;
        member.email = email;
        member.phone = phone;
        member.birthDate = birthDate;
        member.gender = gender;
        member.grade = grade;
        member.role = "USER";
        member.status = "JOINED";
        member.pointBalance = 0;
        return member;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void usePoint(int amount) {
        if (amount > this.pointBalance) {
            throw new BusinessException(ErrorCode.POINT_BALANCE_INSUFFICIENT);
        }
        this.pointBalance -= amount;
    }
}
