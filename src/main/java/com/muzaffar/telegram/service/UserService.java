package com.muzaffar.telegram.service;

import com.muzaffar.telegram.entity.User;
import com.muzaffar.telegram.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository userRepository;
    
    /**
     * Find user by chat ID
     */
    public Optional<User> findByChatId(Long chatId) {
        return userRepository.findByChatId(chatId);
    }
    
    /**
     * Find user by referral code
     */
    public Optional<User> findByReferralCode(String referralCode) {
        return userRepository.findByReferralCode(referralCode);
    }
    
    /**
     * Create or get existing user
     */
    @Transactional
    public User getOrCreateUser(Long chatId, String username, String firstName, String lastName) {
        return userRepository.findByChatId(chatId)
            .orElseGet(() -> createNewUser(chatId, username, firstName, lastName));
    }
    
    /**
     * Create a new user with unique referral code
     */
    private User createNewUser(Long chatId, String username, String firstName, String lastName) {
        User user = User.builder()
            .chatId(chatId)
            .username(username)
            .firstName(firstName)
            .lastName(lastName)
            .referralCode(generateUniqueReferralCode())
            .referralCount(0)
            .hasReceivedInviteLink(false)
            .build();
        
        User savedUser = userRepository.save(user);
        log.info("Created new user: chatId={}, username={}, referralCode={}", 
            chatId, username, savedUser.getReferralCode());
        
        return savedUser;
    }
    
    /**
     * Generate unique referral code
     */
    private String generateUniqueReferralCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        } while (userRepository.findByReferralCode(code).isPresent());
        
        return code;
    }
    
    /**
     * Process referral - link new user to inviter
     * Returns true if referral was successfully processed
     */
    @Transactional
    public boolean processReferral(User newUser, String referralCode) {
        if (newUser.getInvitedByUserId() != null) {
            log.warn("User {} already has an inviter", newUser.getChatId());
            return false;
        }
        
        Optional<User> inviterOpt = userRepository.findByReferralCode(referralCode);
        if (inviterOpt.isEmpty()) {
            log.warn("Inviter not found for referral code: {}", referralCode);
            return false;
        }
        
        User inviter = inviterOpt.get();
        
        if (inviter.getChatId().equals(newUser.getChatId())) {
            log.warn("User {} tried to use their own referral code", newUser.getChatId());
            return false;
        }
        
        // Link new user to inviter
        newUser.setInvitedByUserId(inviter.getId());
        userRepository.save(newUser);
        
        // Increment inviter's direct referral count
        inviter.setReferralCount(inviter.getReferralCount() + 1);
        userRepository.save(inviter);
        
        // If inviter was also invited by someone, increment that person's indirect referral count
        if (inviter.getInvitedByUserId() != null) {
            userRepository.findById(inviter.getInvitedByUserId()).ifPresent(grandInviter -> {
                grandInviter.setIndirectReferralCount(grandInviter.getIndirectReferralCount() + 1);
                userRepository.save(grandInviter);
                log.info("Indirect referral processed: grandInviter={} (indirect count={})", 
                    grandInviter.getChatId(), grandInviter.getIndirectReferralCount());
            });
        }
        
        log.info("Referral processed: inviter={} (direct count={}), newUser={}", 
            inviter.getChatId(), inviter.getReferralCount(), newUser.getChatId());
        
        return true;
    }
    
    /**
     * Mark user as having received invite link
     */
    @Transactional
    public void markInviteLinkReceived(User user) {
        user.setHasReceivedInviteLink(true);
        userRepository.save(user);
        log.info("User {} marked as received invite link", user.getChatId());
    }
    
    /**
     * Get referral count for user
     */
    public int getReferralCount(User user) {
        return user.getReferralCount();
    }
    
    /**
     * Check if user should receive invite link
     */
    public boolean shouldReceiveInviteLink(User user, int requiredCount) {
        return user.getReferralCount() >= requiredCount && !user.getHasReceivedInviteLink();
    }
    
    /**
     * Get all users for broadcast
     */
    public List<User> getAllUsers() {
        return userRepository.findAllUsers();
    }
    
    /**
     * Get total user count
     */
    public long getTotalUserCount() {
        return userRepository.count();
    }
    
    /**
     * Get all users invited by a specific user
     */
    public List<User> getUsersInvitedBy(Long userId) {
        return userRepository.findByInvitedByUserId(userId);
    }
}
