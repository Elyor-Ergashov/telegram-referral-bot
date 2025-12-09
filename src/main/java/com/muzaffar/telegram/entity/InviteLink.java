package com.muzaffar.telegram.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "invite_links",
       indexes = {
           @Index(name = "idx_invite_link_user", columnList = "challenge_id, userChatId"),
           @Index(name = "idx_invite_link_status", columnList = "status"),
           @Index(name = "idx_invite_link_code", columnList = "inviteLink")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteLink {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenge_id", nullable = false)
    private Challenge challenge;
    
    @Column(nullable = false)
    private Long userChatId;
    
    @Column(nullable = false, length = 500)
    private String inviteLink;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private InviteLinkStatus status = InviteLinkStatus.UNUSED;
    
    private LocalDateTime expireDate;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime usedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    public enum InviteLinkStatus {
        UNUSED,
        USED,
        EXPIRED
    }
}
