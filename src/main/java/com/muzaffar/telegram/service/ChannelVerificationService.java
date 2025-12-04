package com.muzaffar.telegram.service;

import com.muzaffar.telegram.entity.Channel;
import com.muzaffar.telegram.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for handling channel membership verification.
 * Consolidates channel verification logic to eliminate duplication.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelVerificationService {
    
    /**
     * Check if a user is a member of a specific channel.
     * Tries both numeric ID and username format.
     * 
     * @param bot The bot instance to use
     * @param userId The user's chat ID
     * @param channelUsername The channel username (with or without @)
     * @return true if user is a member, false otherwise
     */
    public boolean isUserMemberOfChannel(TelegramLongPollingBot bot, Long userId, String channelUsername) {
        try {
            ChatMember chatMember = getChatMemberWithRetry(bot, channelUsername, userId);
            if (chatMember != null) {
                String status = chatMember.getStatus();
                boolean isMember = "member".equals(status) || 
                                  "administrator".equals(status) || 
                                  "creator".equals(status);
                log.debug("User {} membership in {}: {} (status: {})", userId, channelUsername, isMember, status);
                return isMember;
            }
        } catch (Exception e) {
            log.error("Error checking membership for user {} in channel {}", userId, channelUsername, e);
        }
        return false;
    }
    
    /**
     * Check if a user is a member of all required channels.
     * 
     * @param bot The bot instance to use
     * @param userId The user's chat ID
     * @param channels List of channels to check
     * @return true if user is a member of ALL channels, false otherwise
     */
    public boolean isUserMemberOfAllChannels(TelegramLongPollingBot bot, Long userId, List<Channel> channels) {
        if (channels.isEmpty()) {
            return true; // No channels to verify
        }
        
        for (Channel channel : channels) {
            if (!isUserMemberOfChannel(bot, userId, channel.getChannelUsername())) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Get detailed membership status for a user across multiple channels.
     * 
     * @param bot The bot instance to use
     * @param userId The user's chat ID
     * @param channels List of channels to check
     * @return Map of channel username to membership status
     */
    public Map<String, Boolean> getChannelMembershipStatus(TelegramLongPollingBot bot, Long userId, List<Channel> channels) {
        Map<String, Boolean> statusMap = new HashMap<>();
        
        for (Channel channel : channels) {
            boolean isMember = isUserMemberOfChannel(bot, userId, channel.getChannelUsername());
            statusMap.put(channel.getChannelUsername(), isMember);
        }
        
        return statusMap;
    }
    
    /**
     * Check membership for multiple users in a single channel.
     * 
     * @param bot The bot instance to use
     * @param users List of users to check
     * @param channel The channel to check membership in
     * @return Map of user ID to membership status
     */
    public Map<Long, Boolean> checkBulkMembership(TelegramLongPollingBot bot, List<User> users, Channel channel) {
        Map<Long, Boolean> membershipMap = new HashMap<>();
        
        for (User user : users) {
            boolean isMember = isUserMemberOfChannel(bot, user.getChatId(), channel.getChannelUsername());
            membershipMap.put(user.getChatId(), isMember);
        }
        
        return membershipMap;
    }
    
    /**
     * Try to get chat member with retry logic using different ID formats.
     * Telegram API accepts both numeric IDs and @username format.
     * 
     * @param bot The bot instance
     * @param channelId The channel ID or username
     * @param userId The user ID
     * @return ChatMember object or null if not found
     */
    private ChatMember getChatMemberWithRetry(TelegramLongPollingBot bot, String channelId, Long userId) {
        // Try with original format first
        ChatMember member = tryGetChatMember(bot, channelId, userId);
        if (member != null) {
            return member;
        }
        
        // If channel starts with @, try without it
        if (channelId.startsWith("@")) {
            String channelIdWithoutAt = channelId.substring(1);
            member = tryGetChatMember(bot, channelIdWithoutAt, userId);
            if (member != null) {
                return member;
            }
        } else {
            // Try with @ prefix
            String channelIdWithAt = "@" + channelId;
            member = tryGetChatMember(bot, channelIdWithAt, userId);
            if (member != null) {
                return member;
            }
        }
        
        return null;
    }
    
    /**
     * Attempt to get chat member information.
     * 
     * @param bot The bot instance
     * @param chatId The chat ID
     * @param userId The user ID
     * @return ChatMember object or null if request fails
     */
    private ChatMember tryGetChatMember(TelegramLongPollingBot bot, String chatId, Long userId) {
        try {
            GetChatMember getChatMember = new GetChatMember();
            getChatMember.setChatId(chatId);
            getChatMember.setUserId(userId);
            return bot.execute(getChatMember);
        } catch (TelegramApiException e) {
            log.debug("Failed to get chat member for chatId={}, userId={}: {}", chatId, userId, e.getMessage());
            return null;
        }
    }
}
