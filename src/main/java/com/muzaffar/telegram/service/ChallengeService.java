package com.muzaffar.telegram.service;

import com.muzaffar.telegram.entity.Challenge;
import com.muzaffar.telegram.entity.ChallengeChannel;
import com.muzaffar.telegram.entity.ChallengeUser;
import com.muzaffar.telegram.repository.ChallengeChannelRepository;
import com.muzaffar.telegram.repository.ChallengeRepository;
import com.muzaffar.telegram.repository.ChallengeUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeService {
    
    private final ChallengeRepository challengeRepository;
    private final ChallengeChannelRepository challengeChannelRepository;
    private final ChallengeUserRepository challengeUserRepository;
    
    /**
     * Create a new challenge
     */
    @Transactional
    public Challenge createChallenge(String name, Integer requiredReferralCount, String privateChannelLink) {
        Challenge challenge = Challenge.builder()
                .name(name)
                .requiredReferralCount(requiredReferralCount)
                .privateChannelLink(privateChannelLink)
                .isActive(true)
                .build();
        
        Challenge saved = challengeRepository.save(challenge);
        log.info("Created new challenge: {} (ID: {})", name, saved.getId());
        return saved;
    }
    
    /**
     * Get challenge by ID
     */
    public Optional<Challenge> getChallengeById(Long challengeId) {
        return challengeRepository.findById(challengeId);
    }
    
    /**
     * Get all challenges
     */
    public List<Challenge> getAllChallenges() {
        return challengeRepository.findAllOrderByCreatedAtDesc();
    }
    
    /**
     * Get all active challenges
     */
    public List<Challenge> getActiveChallenges() {
        return challengeRepository.findAllActiveChallenges();
    }
    
    /**
     * Get the main challenge (for new users)
     */
    public Optional<Challenge> getDefaultChallenge() {
        return challengeRepository.findMainChallenge();
    }
    
    /**
     * Set a challenge as the main challenge (unsets all others)
     */
    @Transactional
    public void setMainChallenge(Long challengeId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        
        if (!challenge.getIsActive()) {
            throw new IllegalArgumentException("Cannot set inactive challenge as main");
        }
        
        // Unset all other challenges as main
        List<Challenge> allChallenges = challengeRepository.findAll();
        for (Challenge c : allChallenges) {
            if (c.getIsMain() && !c.getId().equals(challengeId)) {
                c.setIsMain(false);
                challengeRepository.save(c);
            }
        }
        
        // Set this challenge as main
        challenge.setIsMain(true);
        challengeRepository.save(challenge);
        log.info("Set challenge {} as main challenge", challengeId);
    }
    
    /**
     * Update challenge details
     */
    @Transactional
    public Challenge updateChallenge(Long challengeId, String name,
                                    Integer requiredReferralCount, String privateChannelLink) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        
        challenge.setName(name);
        challenge.setRequiredReferralCount(requiredReferralCount);
        challenge.setPrivateChannelLink(privateChannelLink);
        
        Challenge updated = challengeRepository.save(challenge);
        log.info("Updated challenge: {} (ID: {})", name, challengeId);
        return updated;
    }
    
    /**
     * Update private channel ID for generating invite links
     */
    @Transactional
    public Challenge updatePrivateChannelId(Long challengeId, String privateChannelId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        
        challenge.setPrivateChannelId(privateChannelId);
        
        Challenge updated = challengeRepository.save(challenge);
        log.info("Updated privateChannelId for challenge {}: {}", challengeId, privateChannelId);
        return updated;
    }
    
    /**
     * Activate/deactivate challenge
     */
    @Transactional
    public void toggleChallengeStatus(Long challengeId, boolean isActive) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        
        challenge.setIsActive(isActive);
        challengeRepository.save(challenge);
        log.info("Challenge {} is now {}", challengeId, isActive ? "active" : "inactive");
    }
    
    /**
     * Delete challenge (careful - this should be rare)
     */
    @Transactional
    public void deleteChallenge(Long challengeId) {
        if (!challengeRepository.existsById(challengeId)) {
            throw new IllegalArgumentException("Challenge not found: " + challengeId);
        }
        
        challengeRepository.deleteById(challengeId);
        log.warn("Deleted challenge: {}", challengeId);
    }
    
    /**
     * Add channel to challenge
     */
    @Transactional
    public ChallengeChannel addChannelToChallenge(Long challengeId, String channelId, 
                                                   String channelName, String channelUsername) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        
        // Check if channel already exists for this challenge
        if (challengeChannelRepository.existsByChallengeAndChannelId(challenge, channelId)) {
            throw new IllegalArgumentException("Channel already exists in this challenge");
        }
        
        ChallengeChannel challengeChannel = ChallengeChannel.builder()
                .challenge(challenge)
                .channelId(channelId)
                .channelName(channelName)
                .channelUsername(channelUsername)
                .isActive(true)
                .build();
        
        ChallengeChannel saved = challengeChannelRepository.save(challengeChannel);
        log.info("Added channel {} to challenge {}", channelName, challengeId);
        return saved;
    }
    
    /**
     * Get channels for a challenge
     */
    public List<ChallengeChannel> getChannelsForChallenge(Long challengeId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        return challengeChannelRepository.findByChallenge(challenge);
    }
    
    /**
     * Get active channels for a challenge
     */
    public List<ChallengeChannel> getActiveChannelsForChallenge(Long challengeId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        return challengeChannelRepository.findActiveChannelsByChallenge(challenge);
    }
    
    /**
     * Remove channel from challenge
     */
    @Transactional
    public boolean removeChannelFromChallenge(Long challengeId, String channelId) {
        Optional<ChallengeChannel> channelOpt = challengeChannelRepository
                .findByChallengeIdAndChannelId(challengeId, channelId);
        
        if (channelOpt.isPresent()) {
            challengeChannelRepository.delete(channelOpt.get());
            log.info("Removed channel {} from challenge {}", channelId, challengeId);
            return true;
        }
        return false;
    }
    
    /**
     * Get statistics for a challenge
     */
    public ChallengeStats getChallengeStats(Long challengeId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        
        long totalUsers = challengeUserRepository.countUsersByChallenge(challenge);
        long completedUsers = challengeUserRepository.countCompletedUsersByChallenge(challenge);
        long totalChannels = challengeChannelRepository.countActiveChannelsByChallenge(challenge);
        
        return new ChallengeStats(totalUsers, completedUsers, totalChannels);
    }
    
    /**
     * DTO for challenge statistics
     */
    public record ChallengeStats(long totalUsers, long completedUsers, long totalChannels) {}
}
