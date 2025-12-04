package com.muzaffar.telegram.service;

import com.muzaffar.telegram.entity.BotSettings;
import com.muzaffar.telegram.repository.BotSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BotSettingsService {
    
    private final BotSettingsRepository settingsRepository;
    
    private static final int DEFAULT_REFERRAL_COUNT = 5;
    private static final String DEFAULT_PRIVATE_LINK = "";
    
    /**
     * Get the settings entity (single row)
     */
    private BotSettings getSettings() {
        return settingsRepository.findAll().stream()
            .findFirst()
            .orElseGet(this::createDefaultSettings);
    }
    
    /**
     * Get required referral count
     */
    public int getRequiredReferralCount() {
        return getSettings().getRequiredReferralCount();
    }
    
    /**
     * Set required referral count
     */
    @Transactional
    public void setRequiredReferralCount(int count) {
        BotSettings settings = getSettings();
        settings.setRequiredReferralCount(count);
        settingsRepository.save(settings);
        log.info("Updated required referral count to: {}", count);
    }
    
    /**
     * Get private channel invite link
     */
    public Optional<String> getPrivateChannelLink() {
        String link = getSettings().getPrivateChannelLink();
        return (link == null || link.isEmpty()) ? Optional.empty() : Optional.of(link);
    }
    
    /**
     * Set private channel invite link
     */
    @Transactional
    public void setPrivateChannelLink(String link) {
        BotSettings settings = getSettings();
        settings.setPrivateChannelLink(link);
        settingsRepository.save(settings);
        log.info("Updated private channel link to: {}", link);
    }
    
    /**
     * Create default settings if they don't exist
     */
    @Transactional
    public BotSettings createDefaultSettings() {
        BotSettings settings = BotSettings.builder()
            .requiredReferralCount(DEFAULT_REFERRAL_COUNT)
            .privateChannelLink(DEFAULT_PRIVATE_LINK)
            .description("Bot configuration settings")
            .build();
        BotSettings saved = settingsRepository.save(settings);
        log.info("Created default settings with referral count: {}", DEFAULT_REFERRAL_COUNT);
        return saved;
    }
    
    /**
     * Initialize default settings if they don't exist
     */
    @Transactional
    public void initializeDefaultSettings() {
        if (settingsRepository.count() == 0) {
            createDefaultSettings();
            log.info("Initialized default bot settings");
        }
    }
}
