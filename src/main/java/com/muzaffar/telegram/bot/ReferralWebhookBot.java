package com.muzaffar.telegram.bot;

import com.muzaffar.telegram.config.AppConfig;
import com.muzaffar.telegram.config.BotConfig;
import com.muzaffar.telegram.entity.Channel;
import com.muzaffar.telegram.entity.User;
import com.muzaffar.telegram.service.BotSettingsService;
import com.muzaffar.telegram.service.ChannelService;
import com.muzaffar.telegram.service.MessageService;
import com.muzaffar.telegram.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

@Slf4j
@Component
public class ReferralWebhookBot extends TelegramWebhookBot {
    
    private final BotConfig botConfig;
    private final AppConfig appConfig;
    private final UserService userService;
    private final MessageService messageService;
    private final BotSettingsService settingsService;
    private final ChannelService channelService;

    public ReferralWebhookBot(BotConfig botConfig, AppConfig appConfig,
                             UserService userService, MessageService messageService,
                             BotSettingsService settingsService, ChannelService channelService) {
        super(botConfig.getBot().getToken());
        this.botConfig = botConfig;
        this.appConfig = appConfig;
        this.userService = userService;
        this.messageService = messageService;
        this.settingsService = settingsService;
        this.channelService = channelService;
    }
    
    @Override
    public String getBotUsername() {
        return botConfig.getBot().getUsername();
    }
    
    @Override
    public String getBotPath() {
        return botConfig.getWebhook().getPath();
    }
    
    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return null;
        }
        
        Message message = update.getMessage();
        Long chatId = message.getChatId();
        String text = message.getText();
        
        log.info("Received webhook message from chatId={}: {}", chatId, text);
        
        try {
            if (text.startsWith("/start")) {
                return handleStartCommand(message);
            } else if (text.equals("/stats")) {
                return handleStatsCommand(message);
            } else if (text.startsWith("/admin_broadcast")) {
                handleAdminBroadcast(message);
                return null;
            } else if (text.equals("/admin_stats")) {
                return handleAdminStats(message);
            } else {
                return messageService.createMessage(chatId, "Unknown command. Use /start or /stats");
            }
        } catch (Exception e) {
            log.error("Error processing webhook message from chatId=" + chatId, e);
            return messageService.createMessage(chatId, "An error occurred. Please try again later.");
        }
    }
    
    private SendMessage handleStartCommand(Message message) {
        Long chatId = message.getChatId();
        String username = message.getFrom().getUserName();
        String firstName = message.getFrom().getFirstName();
        String lastName = message.getFrom().getLastName();
        String text = message.getText();
        
        String referralCode = null;
        if (text.length() > 7) {
            referralCode = text.substring(7).trim();
        }
        
        User user = userService.getOrCreateUser(chatId, username, firstName, lastName);
        
        // Process referral if present
        if (referralCode != null && !referralCode.isEmpty() && user.getInvitedByUserId() == null) {
            boolean success = userService.processReferral(user, referralCode);
            
            if (success) {
                userService.findByReferralCode(referralCode).ifPresent(inviter -> {
                    SendMessage successMessage = messageService.createReferralSuccessMessage(
                        inviter.getChatId(),
                        inviter.getReferralCount(),
                        appConfig.getReferral().getRequiredCount(),
                        firstName,
                        chatId
                    );
                    sendMessageAsync(successMessage);
                    checkAndSendPrivateChannelLink(inviter);
                });
            }
        }
        
        // Send welcome sequence asynchronously (after greeting)
        sendWelcomeSequenceAsync(chatId, firstName);
        
        // Return greeting as immediate response
        return messageService.createGreetingMessage(chatId, firstName);
    }
    
    /**
     * Send welcome message sequence asynchronously (only main message, referral info comes from button)
     */
    private void sendWelcomeSequenceAsync(Long chatId, String firstName) {
        new Thread(() -> {
            try {
                // Wait a bit before sending main message
                Thread.sleep(500);
                
                // Send main welcome message with Takliflarim button
                SendMessage mainWelcome = messageService.createMainWelcomeMessage(chatId);
                sendMessageAsync(mainWelcome);
                
            } catch (Exception e) {
                log.error("Error sending welcome sequence async", e);
            }
        }).start();
    }
    
    private SendMessage handleStatsCommand(Message message) {
        Long chatId = message.getChatId();
        
        User user = userService.findByChatId(chatId).orElse(null);
        if (user == null) {
            return messageService.createMessage(chatId, "Please use /start first to register.");
        }
        
        String statsMessage = messageService.createStatsMessage(
            getBotUsername(),
            user.getReferralCode(),
            user.getReferralCount(),
            appConfig.getReferral().getRequiredCount()
        );
        return messageService.createMessage(chatId, statsMessage);
    }
    
    private void handleAdminBroadcast(Message message) {
        Long chatId = message.getChatId();
        
        if (!isAdmin(chatId)) {
            sendMessageAsync(chatId, "❌ You don't have permission to use this command.");
            return;
        }
        
        String text = message.getText();
        if (text.length() <= 16) {
            sendMessageAsync(chatId, "Usage: /admin_broadcast <message>");
            return;
        }
        
        String broadcastMessage = text.substring(16).trim();
        List<User> users = userService.getAllUsers();
        
        // Broadcast in background thread
        new Thread(() -> {
            int successCount = 0;
            int failCount = 0;
            
            for (User user : users) {
                try {
                    sendMessageAsync(user.getChatId(), "📢 *Broadcast Message*\n\n" + broadcastMessage);
                    successCount++;
                    Thread.sleep(50);
                } catch (Exception e) {
                    log.error("Failed to send broadcast to chatId=" + user.getChatId(), e);
                    failCount++;
                }
            }
            
            sendMessageAsync(chatId, String.format(
                "✅ Broadcast complete!\n\nSent: %d\nFailed: %d",
                successCount, failCount
            ));
        }).start();
    }
    
    private SendMessage handleAdminStats(Message message) {
        Long chatId = message.getChatId();
        
        if (!isAdmin(chatId)) {
            return messageService.createMessage(chatId, "❌ You don't have permission to use this command.");
        }
        
        long totalUsers = userService.getTotalUserCount();
        String statsMessage = messageService.createAdminStatsMessage(totalUsers);
        return messageService.createMessage(chatId, statsMessage);
    }
    
    private void checkAndSendPrivateChannelLink(User user) {
        int requiredCount = settingsService.getRequiredReferralCount();
        
        // Check if user already received the link
        if (user.getHasReceivedInviteLink()) {
            return;
        }
        
        // Check if user has enough total referrals
        if (user.getReferralCount() < requiredCount) {
            return;
        }
        
        // Get all users invited by this user
        List<User> invitedUsers = userService.getUsersInvitedBy(user.getId());
        
        // Get all required channels
        List<Channel> requiredChannels = channelService.getAllActiveChannels();
        
        // If no channels configured, just give the link
        if (requiredChannels.isEmpty()) {
            String inviteLink = getPrivateChannelLink();
            String message = messageService.createPrivateChannelMessage(inviteLink, requiredCount);
            sendMessageAsync(user.getChatId(), message);
            userService.markInviteLinkReceived(user);
            return;
        }
        
        // Count active and left users
        int activeCount = 0;
        int leftCount = 0;
        
        for (User invitedUser : invitedUsers) {
            boolean isActiveInAllChannels = true;
            
            // Check membership in each required channel
            for (Channel channel : requiredChannels) {
                if (!checkUserInChannel(invitedUser.getChatId(), channel.getChannelUsername())) {
                    isActiveInAllChannels = false;
                    break;
                }
            }
            
            if (isActiveInAllChannels) {
                activeCount++;
            } else {
                leftCount++;
            }
        }
        
        // Check if user has enough active referrals
        if (activeCount >= requiredCount) {
            // User has enough active referrals - send invite link
            String inviteLink = getPrivateChannelLink();
            String message = messageService.createPrivateChannelMessage(inviteLink, requiredCount);
            sendMessageAsync(user.getChatId(), message);
            userService.markInviteLinkReceived(user);
            log.info("User {} received private channel link with {} active referrals", user.getChatId(), activeCount);
        } else {
            // Not enough active referrals - send info message
            SendMessage message = messageService.createInsufficientActiveReferralsMessage(
                user.getChatId(), activeCount, leftCount, requiredCount);
            sendMessageAsync(message);
            log.info("User {} has {} active, {} left referrals (need {})", 
                user.getChatId(), activeCount, leftCount, requiredCount);
        }
    }
    
    /**
     * Check if user is an active member of a channel
     */
    private boolean checkUserInChannel(Long userId, String channelUsername) {
        try {
            GetChatMember getChatMember = new GetChatMember();
            getChatMember.setChatId("@" + channelUsername);
            getChatMember.setUserId(userId);
            
            ChatMember chatMember = execute(getChatMember);
            
            // Check if user is active member (not left or kicked)
            String status = chatMember.getStatus();
            boolean isActive = status.equals("member") || 
                              status.equals("administrator") || 
                              status.equals("creator");
            
            log.debug("User {} in channel @{}: status={}, active={}", 
                userId, channelUsername, status, isActive);
            
            return isActive;
        } catch (TelegramApiException e) {
            log.error("Error checking user {} in channel @{}: {}", 
                userId, channelUsername, e.getMessage());
            // If we can't check, assume they're not in the channel
            return false;
        }
    }
    
    private boolean isAdmin(Long chatId) {
        return appConfig.getAdmin().getChatIds().contains(chatId);
    }
    
    private void sendMessageAsync(Long chatId, String text) {
        try {
            SendMessage message = messageService.createMessage(chatId, text);
            execute(message);
            log.info("Sent async message to chatId={}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending async message to chatId=" + chatId, e);
        }
    }
    
    private void sendMessageAsync(SendMessage message) {
        try {
            execute(message);
            log.info("Sent async message to chatId={}", message.getChatId());
        } catch (TelegramApiException e) {
            log.error("Error sending async message to chatId=" + message.getChatId(), e);
        }
    }
    
    /**
     * Get private channel invite link from database or fallback to config
     */
    private String getPrivateChannelLink() {
        return settingsService.getPrivateChannelLink()
            .orElse(botConfig.getPrivateChannel().getInviteLink());
    }
}
