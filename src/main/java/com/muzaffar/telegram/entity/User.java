package com.muzaffar.telegram.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_chat_id", columnList = "chatId"),
    @Index(name = "idx_referral_code", columnList = "referralCode")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private Long chatId;
    
    private String username;
    
    private String firstName;
    
    private String lastName;
    
    @Column(nullable = false, unique = true, length = 50)
    private String referralCode;
    
    @Column(name = "invited_by_user_id")
    private Long invitedByUserId;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer referralCount = 0;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer indirectReferralCount = 0;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean hasReceivedInviteLink = false;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean hasVerifiedChannels = false;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
