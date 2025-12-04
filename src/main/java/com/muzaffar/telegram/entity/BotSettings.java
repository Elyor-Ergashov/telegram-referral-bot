package com.muzaffar.telegram.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "bot_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotSettings {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int requiredReferralCount;

    @Column(nullable = false)
    private String privateChannelLink;
    
    @Column(columnDefinition = "TEXT")
    private String welcomeMessageText;
    
    @Column(columnDefinition = "TEXT")
    private String statsHeaderText;
    
    private String statsHeaderImagePath;
    
    private String description;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
