package com.muzaffar.telegram.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "challenge_users",
       indexes = {
           @Index(name = "idx_challenge_user", columnList = "challenge_id, userChatId"),
           @Index(name = "idx_user_chat_id", columnList = "userChatId"),
           @Index(name = "idx_referral_code", columnList = "referralCode")
       },
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_challenge_user_chat", 
                            columnNames = {"challenge_id", "userChatId"}),
           @UniqueConstraint(name = "uk_referral_code", 
                            columnNames = {"referralCode"})
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeUser {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenge_id", nullable = false)
    private Challenge challenge;
    
    @Column(nullable = false)
    private Long userChatId;
    
    private String username;
    
    private String firstName;
    
    private String lastName;
    
    @Column(nullable = false, unique = true, length = 50)
    private String referralCode;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer referralCount = 0;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean hasVerifiedChannels = false;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean isCompleted = false;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean hasReceivedReward = false;
    
    private LocalDateTime completedAt;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime joinedAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        joinedAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
