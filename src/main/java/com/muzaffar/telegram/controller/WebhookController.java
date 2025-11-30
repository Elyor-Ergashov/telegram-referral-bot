package com.muzaffar.telegram.controller;

import com.muzaffar.telegram.bot.ReferralWebhookBot;
import com.muzaffar.telegram.config.BotConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;

@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.bot.mode", havingValue = "webhook")
public class WebhookController {
    
    private final ReferralWebhookBot webhookBot;
    private final BotConfig botConfig;
    
    @PostMapping("${telegram.webhook.path:/webhook}")
    public BotApiMethod<?> onUpdateReceived(@RequestBody Update update) {
        log.info("Received webhook update: {}", update);
        return webhookBot.onWebhookUpdateReceived(update);
    }
}
