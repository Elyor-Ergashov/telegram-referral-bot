package com.muzaffar.telegram.repository;

import com.muzaffar.telegram.entity.MessageTemplate;
import com.muzaffar.telegram.enums.MessageKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for MessageTemplate entity
 */
@Repository
public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, Long> {
    
    /**
     * Find message template by key
     */
    Optional<MessageTemplate> findByMessageKey(MessageKey messageKey);
    
    /**
     * Check if message template exists by key
     */
    boolean existsByMessageKey(MessageKey messageKey);
}
