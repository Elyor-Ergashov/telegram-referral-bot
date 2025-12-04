package com.muzaffar.telegram.entity;

import com.muzaffar.telegram.enums.MessageKey;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity for storing message templates with text and optional images
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "message_templates")
public class MessageTemplate {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
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
