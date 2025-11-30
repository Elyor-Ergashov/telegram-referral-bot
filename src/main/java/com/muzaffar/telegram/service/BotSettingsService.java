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
    
    public static final String REFERRAL_COUNT_KEY = "referral_required_count";
    
    /**
     * Get setting value by key
     */
    public Optional<String> getSetting(String key) {
        return settingsRepository.findBySettingKey(key)
            .map(BotSettings::getSettingValue);
    }
    
    /**
     * Get required referral count
     */
    public int getRequiredReferralCount() {
        return getSetting(REFERRAL_COUNT_KEY)
            .map(Integer::parseInt)
            .orElse(5); // Default value
    }
    
    /**
     * Set required referral count
     */
    @Transactional
    public void setRequiredReferralCount(int count) {
        setSetting(REFERRAL_COUNT_KEY, String.valueOf(count), "Number of referrals required to get access");
        log.info("Updated required referral count to: {}", count);
    }
    
    /**
     * Set a setting value
     */
    @Transactional
    public void setSetting(String key, String value, String description) {
        Optional<BotSettings> existingOpt = settingsRepository.findBySettingKey(key);
        
        if (existingOpt.isPresent()) {
            BotSettings existing = existingOpt.get();
            existing.setSettingValue(value);
            if (description != null) {
                existing.setDescription(description);
            }
            settingsRepository.save(existing);
        } else {
            BotSettings newSetting = BotSettings.builder()
                .settingKey(key)
                .settingValue(value)
                .description(description)
                .build();
            settingsRepository.save(newSetting);
        }
        
        log.info("Updated setting: {} = {}", key, value);
    }
    
    /**
     * Initialize default settings if they don't exist
     */
    @Transactional
    public void initializeDefaultSettings() {
        if (settingsRepository.findBySettingKey(REFERRAL_COUNT_KEY).isEmpty()) {
            setSetting(REFERRAL_COUNT_KEY, "2", "Number of referrals required to get access");
            log.info("Initialized default referral count setting");
        }
    }
}
