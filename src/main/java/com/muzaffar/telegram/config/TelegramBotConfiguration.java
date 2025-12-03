package com.muzaffar.telegram.config;

import com.muzaffar.telegram.bot.AdminTelegramBot;
import com.muzaffar.telegram.bot.ReferralTelegramBot;
import com.muzaffar.telegram.bot.ReferralWebhookBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class TelegramBotConfiguration {
    
    private final BotConfig botConfig;
    
    @Configuration
    @ConditionalOnProperty(name = "telegram.bot.mode", havingValue = "polling", matchIfMissing = true)
    public static class PollingBotConfiguration {
        
        private final ReferralTelegramBot telegramBot;
        private final AdminTelegramBot adminBot;
        
        public PollingBotConfiguration(ReferralTelegramBot telegramBot, AdminTelegramBot adminBot) {
            this.telegramBot = telegramBot;
            this.adminBot = adminBot;
        }
        
        @EventListener(ContextRefreshedEvent.class)
        public void init() {
            try {
                TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
                
                botsApi.registerBot(telegramBot);
                log.info("✅ User bot registered successfully with LONG POLLING mode");
                log.info("User bot username: @{}", telegramBot.getBotUsername());
                
                if (adminBot.getBotToken() != null && !adminBot.getBotToken().isEmpty()) {
                    botsApi.registerBot(adminBot);
                    log.info("✅ Admin bot registered successfully with LONG POLLING mode");
                    log.info("Admin bot username: @{}", adminBot.getBotUsername());
                } else {
                    log.warn("⚠️ Admin bot token not configured, skipping admin bot registration");
                }
            } catch (TelegramApiException e) {
                log.error("❌ Failed to register Telegram bots", e);
                throw new RuntimeException("Failed to initialize Telegram bots", e);
            }
        }
    }
    
    @Configuration
    @ConditionalOnProperty(name = "telegram.bot.mode", havingValue = "webhook")
    public static class WebhookBotConfiguration {
        
        private final ReferralWebhookBot webhookBot;
        private final BotConfig botConfig;
        
        public WebhookBotConfiguration(ReferralWebhookBot webhookBot, BotConfig botConfig) {
            this.webhookBot = webhookBot;
            this.botConfig = botConfig;
        }
        
        @EventListener(ContextRefreshedEvent.class)
        public void init() {
            try {
                TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
                
                String webhookUrl = botConfig.getWebhook().getUrl() + botConfig.getWebhook().getPath();
                SetWebhook setWebhook = SetWebhook.builder()
                    .url(webhookUrl)
                    .build();
                
                botsApi.registerBot(webhookBot, setWebhook);
                log.info("✅ Telegram bot registered successfully with WEBHOOK mode");
                log.info("Bot username: @{}", webhookBot.getBotUsername());
                log.info("Webhook URL: {}", webhookUrl);
            } catch (TelegramApiException e) {
                log.error("❌ Failed to register Telegram webhook bot", e);
                throw new RuntimeException("Failed to initialize Telegram webhook bot", e);
            }
        }
    }
}
