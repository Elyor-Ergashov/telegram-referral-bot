package com.muzaffar.telegram.bot;

import com.muzaffar.telegram.config.AppConfig;
import com.muzaffar.telegram.config.BotConfig;
import com.muzaffar.telegram.entity.User;
import com.muzaffar.telegram.service.BotSettingsService;
import com.muzaffar.telegram.service.MessageService;
import com.muzaffar.telegram.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
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

    public ReferralWebhookBot(BotConfig botConfig, AppConfig appConfig,
                             UserService userService, MessageService messageService, BotSettingsService settingsService) {
        super(botConfig.getBot().getToken());
        this.botConfig = botConfig;
        this.appConfig = appConfig;
        this.userService = userService;
        this.messageService = messageService;
        this.settingsService = settingsService;
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
        
        if (referralCode != null && !referralCode.isEmpty() && user.getInvitedByUserId() == null) {
            boolean success = userService.processReferral(user, referralCode);
            
            if (success) {
                userService.findByReferralCode(referralCode).ifPresent(inviter -> {
                    String successMessage = messageService.createReferralSuccessMessage(
                        inviter.getReferralCount(),
                        appConfig.getReferral().getRequiredCount()
                    );
                    sendMessageAsync(inviter.getChatId(), successMessage);
                    checkAndSendPrivateChannelLink(inviter);
                });
                
                String joinMessage = messageService.createJoinedViaReferralMessage(
                    getBotUsername(),
                    user.getReferralCode(),
                    appConfig.getReferral().getRequiredCount()
                );
                return messageService.createMessage(chatId, joinMessage);
            }
        }
        
        String welcomeMessage = messageService.createWelcomeMessage(
            getBotUsername(),
            user.getReferralCode(),
            user.getReferralCount(),
            appConfig.getReferral().getRequiredCount()
        );
        return messageService.createMessage(chatId, welcomeMessage);
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
        if (userService.shouldReceiveInviteLink(user, appConfig.getReferral().getRequiredCount())) {
            String inviteLink = botConfig.getPrivateChannel().getInviteLink();
            String message = messageService.createPrivateChannelMessage(inviteLink, settingsService.getRequiredReferralCount());
            sendMessageAsync(user.getChatId(), message);
            userService.markInviteLinkReceived(user);
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
}
