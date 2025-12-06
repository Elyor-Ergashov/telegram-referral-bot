package com.muzaffar.telegram.repository;

import com.muzaffar.telegram.entity.Challenge;
import com.muzaffar.telegram.entity.MessageTemplate;
import com.muzaffar.telegram.enums.MessageKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for MessageTemplate entity
 */
@Repository
public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, Long> {
    
    /**
     * Find message template by challenge and key
     */
    Optional<MessageTemplate> findByChallengeAndMessageKey(Challenge challenge, MessageKey messageKey);
    
    /**
     * Check if message template exists by challenge and key
     */
    boolean existsByChallengeAndMessageKey(Challenge challenge, MessageKey messageKey);
    
    /**
     * Find all message templates for a challenge
     */
    List<MessageTemplate> findByChallenge(Challenge challenge);
    
    /**
     * Delete all message templates for a challenge
     */
    void deleteByChallenge(Challenge challenge);
}
