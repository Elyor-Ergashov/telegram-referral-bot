package com.muzaffar.telegram.config;

import com.muzaffar.telegram.service.BotSettingsService;
import com.muzaffar.telegram.service.MessageTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    
    private final BotSettingsService settingsService;
    private final MessageTemplateService messageTemplateService;
    
    @Override
    public void run(String... args) {
        log.info("Initializing bot settings...");
//        settingsService.initializeDefaultSettings();
        log.info("Bot settings initialized successfully");
        
        log.info("Initializing message templates...");
//        messageTemplateService.initializeDefaultTemplates();
        log.info("Message templates initialized successfully");
    }
}
