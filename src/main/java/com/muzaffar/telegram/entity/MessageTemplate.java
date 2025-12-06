package com.muzaffar.telegram.entity;

import com.muzaffar.telegram.enums.MessageKey;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity for storing message templates with text and optional images
 * Each template belongs to a specific challenge
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "message_templates", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"challenge_id", "message_key"}))
public class MessageTemplate {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenge_id", nullable = false)
    private Challenge challenge;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "message_key", nullable = false)
    private MessageKey messageKey;
    
    @Column(columnDefinition = "TEXT")
    private String text;
    
    @Column(length = 500)
    private String imagePath;
    
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
