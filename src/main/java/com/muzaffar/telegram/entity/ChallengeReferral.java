package com.muzaffar.telegram.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "challenge_referrals",
       indexes = {
           @Index(name = "idx_challenge_referrer", columnList = "challenge_id, referrerChatId"),
           @Index(name = "idx_challenge_referred", columnList = "challenge_id, referredChatId"),
           @Index(name = "idx_referrer_valid", columnList = "referrerChatId, isValid")
       },
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_challenge_referral", 
                            columnNames = {"challenge_id", "referrerChatId", "referredChatId"})
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeReferral {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenge_id", nullable = false)
    private Challenge challenge;
    
    @Column(nullable = false)
    private Long referrerChatId;
    
    @Column(nullable = false)
    private Long referredChatId;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean isValid = false;
    
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
