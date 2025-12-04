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
    private static final String DEFAULT_WELCOME_TEXT = """
            🎉 Assalomu alaykum!
            
            Matematikadan Milliy sertifikat imtihoniga bepul tayyorlanish imkoniyati!
            Va albatta siz uchun *1 ta bonus kitob* tayyorlab qo'ydim 📚✨
            
            🌟 Har bir yangi do'st taklif qilsangiz — *bonus ball* beriladi!
            🚀 Har bir taklif sizni sertifikatga yaqinlashtiradi!
            
            💥 Do'stingiz botga kirib kanalga a'zo bo'lsa — *+1 ball avto qo'shiladi!*
            
            🎯 5 ta matematikachi do'st taklif qilsangiz:
            🔥 *"Geometriya 0 dan" yopiq guruhiga bepul umrbod kirish!*
            
            🎁 Yuqori natija ko'rsatsangiz — *"Geometriya Pro"* guruhida o'qiysiz!
            
            📘 Bonus kitob faqat faol ishtirokchilarga!
            
            ✨ Imkoniyatni qo'ldan boy bermang!
            
            ❗ *Muhim:*
            Ball olish uchun do'stingiz botga kirib, majburiy kanallarga a'zo bo'lib, 10 kun chiqmasligi kerak
            
            💡 Do'stlaringizni hozir taklif qiling:
            (Havolani olish uchun Takliflarim tugmasini bosing...)
            
            📘 Asosiy menyuga xush kelibsiz!""";
    
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
     * Get welcome message text
     */
    public String getWelcomeMessageText() {
        String text = getSettings().getWelcomeMessageText();
        return (text == null || text.isEmpty()) ? DEFAULT_WELCOME_TEXT : text;
    }
    
    /**
     * Set welcome message text
     */
    @Transactional
    public void setWelcomeMessageText(String text) {
        BotSettings settings = getSettings();
        settings.setWelcomeMessageText(text);
        settingsRepository.save(settings);
        log.info("Updated welcome message text");
    }
    
    /**
     * Get stats header text
     */
    public Optional<String> getStatsHeaderText() {
        String text = getSettings().getStatsHeaderText();
        return (text == null || text.isEmpty()) ? Optional.empty() : Optional.of(text);
    }
    
    /**
     * Set stats header text
     */
    @Transactional
    public void setStatsHeaderText(String text) {
        BotSettings settings = getSettings();
        settings.setStatsHeaderText(text);
        settingsRepository.save(settings);
        log.info("Updated stats header text");
    }
    
    /**
     * Get stats header image path
     */
    public Optional<String> getStatsHeaderImagePath() {
        String path = getSettings().getStatsHeaderImagePath();
        return (path == null || path.isEmpty()) ? Optional.empty() : Optional.of(path);
    }
    
    /**
     * Set stats header image path
     */
    @Transactional
    public void setStatsHeaderImagePath(String path) {
        BotSettings settings = getSettings();
        settings.setStatsHeaderImagePath(path);
        settingsRepository.save(settings);
        log.info("Updated stats header image path: {}", path);
    }
    
    /**
     * Create default settings if they don't exist
     */
    @Transactional
    public BotSettings createDefaultSettings() {
        BotSettings settings = BotSettings.builder()
            .requiredReferralCount(DEFAULT_REFERRAL_COUNT)
            .privateChannelLink(DEFAULT_PRIVATE_LINK)
            .welcomeMessageText(DEFAULT_WELCOME_TEXT)
            .statsHeaderText(null)
            .statsHeaderImagePath(null)
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
