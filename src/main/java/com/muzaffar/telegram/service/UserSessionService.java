package com.muzaffar.telegram.service;

import com.muzaffar.telegram.entity.UserSessionState;
import com.muzaffar.telegram.repository.UserSessionStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSessionService {
    
    private final UserSessionStateRepository sessionRepository;
    
    /**
     * Get or create session state for a chat
     */
    @Transactional
    public UserSessionState getOrCreateSession(Long chatId) {
        return sessionRepository.findByChatId(chatId)
                .orElseGet(() -> {
                    UserSessionState session = UserSessionState.builder()
                            .chatId(chatId)
                            .hasSeenIntro(false)
                            .build();
                    return sessionRepository.save(session);
                });
    }
    
    /**
     * Set pending referral code for user
     */
    @Transactional
    public void setPendingReferral(Long chatId, String code) {
        UserSessionState session = getOrCreateSession(chatId);
        session.setPendingReferralCode(code);
        sessionRepository.save(session);
        log.debug("Set pending referral code {} for chatId {}", code, chatId);
    }
    
    /**
     * Get pending referral code for user
     */
    @Transactional(readOnly = true)
    public Optional<String> getPendingReferral(Long chatId) {
        return sessionRepository.findByChatId(chatId)
                .map(UserSessionState::getPendingReferralCode)
                .filter(code -> code != null && !code.isEmpty());
    }
    
    /**
     * Clear pending referral code for user
     */
    @Transactional
    public void clearPendingReferral(Long chatId) {
        sessionRepository.findByChatId(chatId).ifPresent(session -> {
            session.setPendingReferralCode(null);
            sessionRepository.save(session);
            log.debug("Cleared pending referral for chatId {}", chatId);
        });
    }
    
    /**
     * Set whether user has seen intro
     */
    @Transactional
    public void setHasSeenIntro(Long chatId, boolean hasSeen) {
        UserSessionState session = getOrCreateSession(chatId);
        session.setHasSeenIntro(hasSeen);
        sessionRepository.save(session);
        log.debug("Set hasSeenIntro={} for chatId {}", hasSeen, chatId);
    }
    
    /**
     * Check if user has seen intro
     */
    @Transactional(readOnly = true)
    public boolean hasSeenIntro(Long chatId) {
        return sessionRepository.findByChatId(chatId)
                .map(UserSessionState::getHasSeenIntro)
                .orElse(false);
    }
    
    /**
     * Set challenge context for user
     */
    @Transactional
    public void setChallengeContext(Long chatId, Long challengeId) {
        UserSessionState session = getOrCreateSession(chatId);
        session.setCurrentChallengeId(challengeId);
        sessionRepository.save(session);
        log.debug("Set challenge context {} for chatId {}", challengeId, chatId);
    }
    
    /**
     * Get challenge context for user
     */
    @Transactional(readOnly = true)
    public Optional<Long> getChallengeContext(Long chatId) {
        return sessionRepository.findByChatId(chatId)
                .map(UserSessionState::getCurrentChallengeId);
    }
    
    /**
     * Clean up expired sessions
     */
    @Transactional
    public void cleanupExpiredSessions() {
        sessionRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        log.info("Cleaned up expired user sessions");
    }
}
