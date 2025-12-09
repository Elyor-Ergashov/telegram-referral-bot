package com.muzaffar.telegram.service;

import com.muzaffar.telegram.entity.Challenge;
import com.muzaffar.telegram.entity.InviteLink;
import com.muzaffar.telegram.entity.InviteLink.InviteLinkStatus;
import com.muzaffar.telegram.repository.ChallengeRepository;
import com.muzaffar.telegram.repository.InviteLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.groupadministration.CreateChatInviteLink;
import org.telegram.telegrambots.meta.api.objects.ChatInviteLink;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InviteLinkService {
    
    private final InviteLinkRepository inviteLinkRepository;
    private final ChallengeRepository challengeRepository;
    
    // Default expiration time in hours (optional, can be null for no expiration)
    private static final Integer DEFAULT_EXPIRE_HOURS = 24;
    
    /**
     * Create a one-time invite link for a user in a challenge.
     * Uses Telegram's createChatInviteLink API with member_limit = 1.
     * 
     * @param bot The Telegram bot instance to execute API calls
     * @param challengeId The challenge ID
     * @param userChatId The user's chat ID who will receive the link
     * @return The generated invite link string, or null if failed
     */
    @Transactional
    public String createOneTimeInviteLink(TelegramLongPollingBot bot, Long challengeId, Long userChatId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        
        // Check if challenge has a private channel ID configured
        String privateChannelId = challenge.getPrivateChannelId();
        if (privateChannelId == null || privateChannelId.isEmpty()) {
            log.warn("Challenge {} does not have privateChannelId configured. " +
                    "Falling back to static privateChannelLink.", challengeId);
            // Fall back to static link if no channel ID configured
            return challenge.getPrivateChannelLink();
        }
        
        try {
            // Create the invite link using Telegram API
            CreateChatInviteLink createLink = new CreateChatInviteLink();
            createLink.setChatId(privateChannelId);
            createLink.setMemberLimit(1);  // Single-use link (cannot be used with createsJoinRequest)
            
            // Optional: set expiration date
            if (DEFAULT_EXPIRE_HOURS != null) {
                int expireDate = (int) (System.currentTimeMillis() / 1000 + DEFAULT_EXPIRE_HOURS * 3600);
                createLink.setExpireDate(expireDate);
            }
            
            // Execute the API call
            ChatInviteLink inviteLink = bot.execute(createLink);
            String linkUrl = inviteLink.getInviteLink();
            
            // Calculate expiration LocalDateTime
            LocalDateTime expireDateTime = null;
            if (DEFAULT_EXPIRE_HOURS != null) {
                expireDateTime = LocalDateTime.now().plusHours(DEFAULT_EXPIRE_HOURS);
            }
            
            // Store the link in database
            InviteLink linkEntity = InviteLink.builder()
                    .challenge(challenge)
                    .userChatId(userChatId)
                    .inviteLink(linkUrl)
                    .status(InviteLinkStatus.UNUSED)
                    .expireDate(expireDateTime)
                    .build();
            
            inviteLinkRepository.save(linkEntity);
            
            log.info("Created one-time invite link for user {} in challenge {}: {}", 
                    userChatId, challengeId, linkUrl);
            
            return linkUrl;
            
        } catch (TelegramApiException e) {
            log.error("Failed to create invite link for user {} in challenge {}: {}", 
                    userChatId, challengeId, e.getMessage(), e);
            
            // Fall back to static link on error
            String fallbackLink = challenge.getPrivateChannelLink();
            if (fallbackLink != null && !fallbackLink.isEmpty()) {
                log.warn("Falling back to static privateChannelLink for user {}", userChatId);
                return fallbackLink;
            }
            
            return null;
        }
    }
    
    /**
     * Mark an invite link as used when a user joins the channel.
     * 
     * @param inviteLinkUrl The invite link URL that was used
     */
    @Transactional
    public void markLinkAsUsed(String inviteLinkUrl) {
        Optional<InviteLink> linkOpt = inviteLinkRepository.findByInviteLink(inviteLinkUrl);
        
        if (linkOpt.isPresent()) {
            InviteLink link = linkOpt.get();
            link.setStatus(InviteLinkStatus.USED);
            link.setUsedAt(LocalDateTime.now());
            inviteLinkRepository.save(link);
            
            log.info("Marked invite link as used: {} for user {}", 
                    inviteLinkUrl, link.getUserChatId());
        } else {
            log.debug("Invite link not found in database (may be a static link): {}", inviteLinkUrl);
        }
    }
    
    /**
     * Mark an invite link as used by user chat ID.
     * This is useful when we know which user joined but not the exact link.
     * 
     * @param challengeId The challenge ID
     * @param userChatId The user's chat ID
     */
    @Transactional
    public void markLinkAsUsedByUser(Long challengeId, Long userChatId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        
        Optional<InviteLink> linkOpt = inviteLinkRepository
                .findByChallengeAndUserChatIdAndStatus(challenge, userChatId, InviteLinkStatus.UNUSED);
        
        if (linkOpt.isPresent()) {
            InviteLink link = linkOpt.get();
            link.setStatus(InviteLinkStatus.USED);
            link.setUsedAt(LocalDateTime.now());
            inviteLinkRepository.save(link);
            
            log.info("Marked invite link as used for user {} in challenge {}", 
                    userChatId, challengeId);
        }
    }
    
    /**
     * Check if a user already has an unused invite link for a challenge.
     * 
     * @param challengeId The challenge ID
     * @param userChatId The user's chat ID
     * @return true if an unused link exists
     */
    public boolean hasUnusedLink(Long challengeId, Long userChatId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        
        return inviteLinkRepository.existsByChallengeAndUserChatIdAndStatus(
                challenge, userChatId, InviteLinkStatus.UNUSED);
    }
    
    /**
     * Get the unused invite link for a user if it exists.
     * 
     * @param challengeId The challenge ID
     * @param userChatId The user's chat ID
     * @return The unused invite link, or empty if none exists
     */
    public Optional<String> getUnusedLink(Long challengeId, Long userChatId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        
        return inviteLinkRepository
                .findByChallengeAndUserChatIdAndStatus(challenge, userChatId, InviteLinkStatus.UNUSED)
                .map(InviteLink::getInviteLink);
    }
    
    /**
     * Get or create a one-time invite link for a user.
     * If an unused link already exists, return it. Otherwise, create a new one.
     * 
     * @param bot The Telegram bot instance
     * @param challengeId The challenge ID
     * @param userChatId The user's chat ID
     * @return The invite link string
     */
    @Transactional
    public String getOrCreateInviteLink(TelegramLongPollingBot bot, Long challengeId, Long userChatId) {
        // First, check if user already has an unused link
        Optional<String> existingLink = getUnusedLink(challengeId, userChatId);
        if (existingLink.isPresent()) {
            log.debug("Returning existing unused invite link for user {} in challenge {}", 
                    userChatId, challengeId);
            return existingLink.get();
        }
        
        // Create a new link
        return createOneTimeInviteLink(bot, challengeId, userChatId);
    }
}
