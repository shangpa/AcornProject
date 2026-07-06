package com.acorn.abc.notification.domain;

import com.acorn.abc.member.domain.Member;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(name = "notification_type", nullable = false, length = 30)
    private String notificationType;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "target_type", length = 30)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "read_yn", nullable = false)
    private Boolean readYn;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    public static Notification create(Member member, String notificationType, String title, String targetType, Long targetId, LocalDateTime createdAt) {
        Notification notification = new Notification();
        notification.member = member;
        notification.notificationType = notificationType;
        notification.title = title;
        notification.targetType = targetType;
        notification.targetId = targetId;
        notification.readYn = false;
        notification.createdAt = createdAt;
        return notification;
    }
}
