package com.muzaffar.telegram.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "challenge_channels", 
       indexes = {
           @Index(name = "idx_challenge_id", columnList = "challenge_id")
       },
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_challenge_channel", 
                            columnNames = {"challenge_id", "channelId"})
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeChannel {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenge_id", nullable = false)
    private Challenge challenge;
    
    @Column(nullable = false)
    private String channelId;
    
    @Column(nullable = false)
    private String channelName;
    
    @Column(nullable = false)
    private String channelUsername;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;
    
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
