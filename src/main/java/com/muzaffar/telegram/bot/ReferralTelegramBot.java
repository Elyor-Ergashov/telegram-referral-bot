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
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberLeft;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ReferralTelegramBot extends TelegramLongPollingBot {
    
    private final BotConfig botConfig;
    private final AppConfig appConfig;
    private final UserService userService;
    private final MessageService messageService;
    private final ChannelService channelService;
    private final BotSettingsService settingsService;
    
    // Store referral codes temporarily for users who need to verify channels first
    private final Map<Long, String> pendingReferrals = new HashMap<>();
    
    public ReferralTelegramBot(BotConfig botConfig, AppConfig appConfig, 
                      UserService userService, MessageService messageService,
                      ChannelService channelService, BotSettingsService settingsService) {
        super(botConfig.getBot().getToken());
        this.botConfig = botConfig;
        this.appConfig = appConfig;
        this.userService = userService;
        this.messageService = messageService;
        this.channelService = channelService;
        this.settingsService = settingsService;
    }
    
    @Override
    public String getBotUsername() {
        return botConfig.getBot().getUsername();
    }
    
    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                Message message = update.getMessage();
                Long chatId = message.getChatId();
                String text = message.getText();
                
                log.info("Received message from chatId={}: {}", chatId, text);
                
                if (text.startsWith("/start")) {
                    handleStartCommand(message);
                } else if (text.equals("/stats")) {
                    handleStatsCommand(message);
                } else if (text.startsWith("/admin_broadcast")) {
                    handleAdminBroadcast(message);
                } else if (text.equals("/admin_stats")) {
                    handleAdminStats(message);
                } else {
                    // Silently delete unknown messages to keep chat clean
                    deleteMessage(chatId, message.getMessageId());
                }
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery());
            }
        } catch (Exception e) {
            log.error("Error processing update", e);
            if (update.hasMessage()) {
                sendMessage(update.getMessage().getChatId(), "❌ Xatolik yuz berdi. Iltimos, qaytadan urinib ko'ring.");
            }
        }
    }
    
    /**
     * Handle /start command
     */
    private void handleStartCommand(Message message) {
        Long chatId = message.getChatId();
        String username = message.getFrom().getUserName();
        String firstName = message.getFrom().getFirstName();
        String lastName = message.getFrom().getLastName();
        String text = message.getText();
        
        // Extract referral code if present (/start <referralCode>)
        String referralCode = null;
        if (text.length() > 7) { // "/start " is 7 characters
            referralCode = text.substring(7).trim();
        }
        
        // Get or create user
        User user = userService.getOrCreateUser(chatId, username, firstName, lastName);
        
        // Check if user has verified channels
        if (!user.getHasVerifiedChannels()) {
            // Store referral code for later processing (after channel verification)
            if (referralCode != null && !referralCode.isEmpty()) {
                pendingReferrals.put(chatId, referralCode);
                log.info("Stored referral code for user {} to process after channel verification", chatId);
            }
            
            // Show channel subscription request
            List<Channel> activeChannels = channelService.getAllActiveChannels();
            if (!activeChannels.isEmpty()) {
                try {
                    SendMessage channelMessage = messageService.createChannelSubscriptionMessage(chatId, activeChannels);
                    execute(channelMessage);
                    return;
                } catch (TelegramApiException e) {
                    log.error("Error sending channel subscription message", e);
                }
            } else {
                // No channels configured, mark as verified
                user.setHasVerifiedChannels(true);
                userService.getOrCreateUser(chatId, username, firstName, lastName);
            }
        }
        
        // Check if this is a new user with a referral code
        if (referralCode != null && !referralCode.isEmpty() && user.getInvitedByUserId() == null) {
            boolean success = userService.processReferral(user, referralCode);
            
            if (success) {
                // Notify new user
                String joinMessage = messageService.createJoinedViaReferralMessage(
                    getBotUsername(), 
                    user.getReferralCode(),
                    settingsService.getRequiredReferralCount()
                );
                sendMessage(chatId, joinMessage);
                
                // Notify inviter
                userService.findByReferralCode(referralCode).ifPresent(inviter -> {
                    String successMessage = messageService.createReferralSuccessMessage(
                        inviter.getReferralCount(),
                        settingsService.getRequiredReferralCount()
                    );
                    sendMessage(inviter.getChatId(), successMessage);
                    
                    // Check if inviter should receive private channel link
                    checkAndSendPrivateChannelLink(inviter);
                });
                
                return;
            }
        }
        
        // Send welcome message
        String welcomeMessage = messageService.createWelcomeMessage(
            getBotUsername(),
            user.getReferralCode(),
            user.getReferralCount(),
            settingsService.getRequiredReferralCount()
        );
        sendMessage(chatId, welcomeMessage);
    }
    
    /**
     * Handle /stats command
     */
    private void handleStatsCommand(Message message) {
        Long chatId = message.getChatId();
        
        User user = userService.findByChatId(chatId).orElse(null);
        if (user == null) {
            sendMessage(chatId, "❌ Iltimos, avval /start buyrug'ini yuboring.");
            return;
        }
        
        String statsMessage = messageService.createStatsMessage(
            getBotUsername(),
            user.getReferralCode(),
            user.getReferralCount(),
            settingsService.getRequiredReferralCount()
        );
        sendMessage(chatId, statsMessage);
    }
    
    /**
     * Handle /admin_broadcast command
     */
    private void handleAdminBroadcast(Message message) {
        Long chatId = message.getChatId();
        
        // Check if user is admin
        if (!isAdmin(chatId)) {
            sendMessage(chatId, "❌ Sizda ushbu buyruqni ishlatish huquqi yo'q.");
            return;
        }
        
        String text = message.getText();
        if (text.length() <= 16) { // "/admin_broadcast " is 16 characters
            sendMessage(chatId, "❌ Foydalanish: /admin_broadcast <xabar>");
            return;
        }
        
        String broadcastMessage = text.substring(16).trim();
        List<User> users = userService.getAllUsers();
        
        int successCount = 0;
        int failCount = 0;
        
        for (User user : users) {
            try {
                sendMessage(user.getChatId(), "📢 *Xabar*\n\n" + broadcastMessage);
                successCount++;
                Thread.sleep(50); // Small delay to avoid rate limiting
            } catch (Exception e) {
                log.error("Failed to send broadcast to chatId=" + user.getChatId(), e);
                failCount++;
            }
        }
        
        sendMessage(chatId, String.format(
            "✅ Xabar yuborish tugadi!\n\nYuborildi: %d\nXatolik: %d",
            successCount, failCount
        ));
    }
    
    /**
     * Handle /admin_stats command
     */
    private void handleAdminStats(Message message) {
        Long chatId = message.getChatId();
        
        // Check if user is admin
        if (!isAdmin(chatId)) {
            sendMessage(chatId, "❌ Sizda ushbu buyruqni ishlatish huquqi yo'q.");
            return;
        }
        
        long totalUsers = userService.getTotalUserCount();
        String statsMessage = messageService.createAdminStatsMessage(totalUsers);
        sendMessage(chatId, statsMessage);
    }
    
    /**
     * Handle callback queries (button presses)
     */
    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        
        log.info("Received callback query from chatId={}: {}", chatId, data);
        
        if ("verify_channels".equals(data)) {
            handleChannelVerification(chatId, messageId, callbackQuery.getFrom().getUserName(),
                callbackQuery.getFrom().getFirstName(), callbackQuery.getFrom().getLastName());
        }
    }
    
    /**
     * Handle channel verification
     */
    private void handleChannelVerification(Long chatId, Integer messageId, String username, String firstName, String lastName) {
        // Get all active channels
        List<Channel> activeChannels = channelService.getAllActiveChannels();
        
        if (activeChannels.isEmpty()) {
            editMessage(chatId, messageId, "✅ Verification complete!");
            User user = userService.getOrCreateUser(chatId, username, firstName, lastName);
            user.setHasVerifiedChannels(true);
            userService.getOrCreateUser(chatId, username, firstName, lastName);
            sendWelcomeMessageAfterVerification(chatId, username, firstName, lastName);
            return;
        }
        
        // Check if user is member of all channels
        boolean allSubscribed = true;
        StringBuilder notSubscribedChannels = new StringBuilder();
        
        for (Channel channel : activeChannels) {
            if (!isUserMemberOfChannel(chatId, channel)) {
                allSubscribed = false;
                notSubscribedChannels.append("• ").append(channel.getChannelName()).append("\n");
            }
        }
        
        if (allSubscribed) {
            // User is subscribed to all channels
            editMessage(chatId, messageId, "✅ Barcha kanallarga obuna bo'lganingiz tasdiqlandi!");
            
            // Mark user as verified
            User user = userService.getOrCreateUser(chatId, username, firstName, lastName);
            user.setHasVerifiedChannels(true);
            userService.getOrCreateUser(chatId, username, firstName, lastName);
            
            // Send welcome message
            sendWelcomeMessageAfterVerification(chatId, username, firstName, lastName);
        } else {
            // User is not subscribed to all channels
            String errorMessage = "❌ Iltimos, avval quyidagi kanallarga obuna bo'ling:\n\n" + notSubscribedChannels.toString();
            editMessage(chatId, messageId, errorMessage);
            
            // Resend subscription buttons
            try {
                Thread.sleep(1000);
                SendMessage channelMessage = messageService.createChannelSubscriptionMessage(chatId, activeChannels);
                execute(channelMessage);
            } catch (Exception e) {
                log.error("Error resending channel subscription message", e);
            }
        }
    }
    
    /**
     * Check if user is member of a channel
     * Tries both numeric ID and @username format
     */
    private boolean isUserMemberOfChannel(Long userId, Channel channel) {
        // Try with numeric channel ID first
        ChatMember chatMember = tryGetChatMember(userId, channel.getChannelId());
        
        // If failed and we have a username, try with @username format
        if (chatMember == null && channel.getChannelUsername() != null && !channel.getChannelUsername().isEmpty()) {
            log.info("Retrying with @username format for channel: {}", channel.getChannelName());
            String usernameFormat = "@" + channel.getChannelUsername().replace("@", "");
            chatMember = tryGetChatMember(userId, usernameFormat);
        }
        
        if (chatMember != null) {
            String status = chatMember.getStatus();
            // Check if user is a member (not "left" or "kicked")
            boolean isMember = !"left".equals(status) && !"kicked".equals(status);
            log.info("User {} membership in channel {} ({}): {}", userId, channel.getChannelName(), status, isMember);
            return isMember;
        }
        
        log.error("⚠️ CONFIGURATION ERROR: Bot cannot access channel {} (ID: {}, Username: @{}). " +
            "The bot must be added as an ADMINISTRATOR to the channel! " +
            "Please add the bot as admin in the channel settings.", 
            channel.getChannelName(), channel.getChannelId(), channel.getChannelUsername());
        
        return false;
    }
    
    /**
     * Try to get chat member with specific chat identifier
     * Returns ChatMember if found, or a marker object if user not subscribed
     */
    private ChatMember tryGetChatMember(Long userId, String chatId) {
        try {
            GetChatMember getChatMember = new GetChatMember();
            getChatMember.setChatId(chatId);
            getChatMember.setUserId(userId);
            
            ChatMember chatMember = execute(getChatMember);
            log.debug("Successfully checked membership for userId={} in chatId={}", userId, chatId);
            return chatMember;
        } catch (TelegramApiException e) {
            String errorMsg = e.getMessage();
            
            if (errorMsg != null && errorMsg.contains("chat not found")) {
                // Bot cannot access this chat - needs to be admin
                log.warn("Chat not found with identifier '{}' - bot may not be admin or ID is incorrect", chatId);
                return null;
            } else if (errorMsg != null && (errorMsg.contains("USER_NOT_PARTICIPANT") || errorMsg.contains("user not found"))) {
                // User is definitely not a member - this is valid information
                log.debug("User {} is not a member of {}", userId, chatId);
                // Create a ChatMemberLeft to indicate user is not subscribed
                // ChatMemberLeft automatically has status "left"
                ChatMemberLeft leftMember = new ChatMemberLeft();
                org.telegram.telegrambots.meta.api.objects.User user = new org.telegram.telegrambots.meta.api.objects.User();
                user.setId(userId);
                user.setFirstName("");
                user.setIsBot(false);
                leftMember.setUser(user);
                // No need to set status - it's automatically "left" for ChatMemberLeft
                return leftMember;
            } else if (errorMsg != null && errorMsg.contains("Bad Request")) {
                log.warn("Bad request error for chatId '{}': {}", chatId, errorMsg);
                return null;
            } else {
                log.debug("Unexpected error checking membership for userId={}, chatId={}: {}", userId, chatId, errorMsg);
                return null;
            }
        }
    }
    
    /**
     * Send welcome message after channel verification
     * Also processes pending referrals
     */
    private void sendWelcomeMessageAfterVerification(Long chatId, String username, String firstName, String lastName) {
        User user = userService.getOrCreateUser(chatId, username, firstName, lastName);
        
        // Check if there's a pending referral to process
        String referralCode = pendingReferrals.get(chatId);
        if (referralCode != null && !referralCode.isEmpty() && user.getInvitedByUserId() == null) {
            log.info("Processing pending referral for user {} with code {}", chatId, referralCode);
            boolean success = userService.processReferral(user, referralCode);
            
            if (success) {
                // Remove from pending
                pendingReferrals.remove(chatId);
                
                // Notify new user
                String joinMessage = messageService.createJoinedViaReferralMessage(
                    getBotUsername(), 
                    user.getReferralCode(),
                    settingsService.getRequiredReferralCount()
                );
                sendMessage(chatId, joinMessage);
                
                // Notify inviter and check for private channel link
                userService.findByReferralCode(referralCode).ifPresent(inviter -> {
                    String successMessage = messageService.createReferralSuccessMessage(
                        inviter.getReferralCount(),
                        settingsService.getRequiredReferralCount()
                    );
                    sendMessage(inviter.getChatId(), successMessage);
                    
                    // Check if inviter should receive private channel link
                    checkAndSendPrivateChannelLink(inviter);
                });
                
                return;
            } else {
                // Referral failed, remove from pending
                pendingReferrals.remove(chatId);
            }
        }
        
        // Send welcome message
        String welcomeMessage = messageService.createWelcomeMessage(
            getBotUsername(),
            user.getReferralCode(),
            user.getReferralCount(),
            settingsService.getRequiredReferralCount()
        );
        sendMessage(chatId, welcomeMessage);
    }
    
    /**
     * Check if user should receive private channel link and send it
     */
    private void checkAndSendPrivateChannelLink(User user) {
        if (userService.shouldReceiveInviteLink(user, settingsService.getRequiredReferralCount())) {
            String inviteLink = botConfig.getPrivateChannel().getInviteLink();
            String message = messageService.createPrivateChannelMessage(inviteLink, settingsService.getRequiredReferralCount());
            sendMessage(user.getChatId(), message);
            userService.markInviteLinkReceived(user);
        }
    }

    /**
     * Check if chat ID is admin
     */
    private boolean isAdmin(Long chatId) {
        return appConfig.getAdmin().getChatIds().contains(chatId);
    }
    
    /**
     * Send message to user
     */
    private void sendMessage(Long chatId, String text) {
        try {
            SendMessage message = messageService.createMessage(chatId, text);
            execute(message);
            log.info("Sent message to chatId={}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending message to chatId=" + chatId, e);
        }
    }
    
    /**
     * Edit message
     */
    private void editMessage(Long chatId, Integer messageId, String text) {
        try {
            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(chatId.toString());
            editMessage.setMessageId(messageId);
            editMessage.setText(text);
            editMessage.setParseMode("Markdown");
            execute(editMessage);
            log.info("Edited message for chatId={}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error editing message for chatId=" + chatId, e);
        }
    }
    
    /**
     * Delete a message
     */
    private void deleteMessage(Long chatId, Integer messageId) {
        try {
            DeleteMessage deleteMessage = new DeleteMessage();
            deleteMessage.setChatId(chatId.toString());
            deleteMessage.setMessageId(messageId);
            execute(deleteMessage);
            log.debug("Deleted message {} from chatId={}", messageId, chatId);
        } catch (TelegramApiException e) {
            log.debug("Could not delete message {} from chatId={}: {}", messageId, chatId, e.getMessage());
        }
    }
}
