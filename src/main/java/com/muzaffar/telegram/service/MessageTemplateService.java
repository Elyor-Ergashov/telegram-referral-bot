package com.muzaffar.telegram.service;

import com.muzaffar.telegram.entity.MessageTemplate;
import com.muzaffar.telegram.enums.MessageKey;
import com.muzaffar.telegram.repository.MessageTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for managing message templates
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageTemplateService {
    
    private final MessageTemplateRepository repository;
    
    /**
     * Get message template by key
     */
    public Optional<MessageTemplate> getTemplate(MessageKey messageKey) {
        return repository.findByMessageKey(messageKey);
    }
    
    /**
     * Get message text by key
     */
    public String getText(MessageKey messageKey) {
        return getTemplate(messageKey)
            .map(MessageTemplate::getText)
            .orElse("");
    }
    
    /**
     * Get image path by key
     */
    public Optional<String> getImagePath(MessageKey messageKey) {
        return getTemplate(messageKey)
            .map(MessageTemplate::getImagePath)
            .filter(path -> path != null && !path.isEmpty());
    }
    
    /**
     * Update message text
     */
    @Transactional
    public void updateText(MessageKey messageKey, String text) {
        MessageTemplate template = repository.findByMessageKey(messageKey)
            .orElseGet(() -> {
                MessageTemplate newTemplate = new MessageTemplate();
                newTemplate.setMessageKey(messageKey);
                return newTemplate;
            });
        
        template.setText(text);
        repository.save(template);
        log.info("Updated text for {}: {}", messageKey, text.substring(0, Math.min(50, text.length())));
    }
    
    /**
     * Update image path
     */
    @Transactional
    public void updateImagePath(MessageKey messageKey, String imagePath) {
        MessageTemplate template = repository.findByMessageKey(messageKey)
            .orElseGet(() -> {
                MessageTemplate newTemplate = new MessageTemplate();
                newTemplate.setMessageKey(messageKey);
                return newTemplate;
            });
        
        template.setImagePath(imagePath);
        repository.save(template);
        log.info("Updated image path for {}: {}", messageKey, imagePath);
    }
    
    /**
     * Update both text and image path
     */
    @Transactional
    public void updateTemplate(MessageKey messageKey, String text, String imagePath) {
        MessageTemplate template = repository.findByMessageKey(messageKey)
            .orElseGet(() -> {
                MessageTemplate newTemplate = new MessageTemplate();
                newTemplate.setMessageKey(messageKey);
                return newTemplate;
            });
        
        if (text != null) {
            template.setText(text);
        }
        if (imagePath != null) {
            template.setImagePath(imagePath);
        }
        
        repository.save(template);
        log.info("Updated template for {}", messageKey);
    }
    
    /**
     * Initialize default templates if they don't exist
     */
    @Transactional
    public void initializeDefaultTemplates() {
        // Initialize WELCOME_MESSAGE
        if (!repository.existsByMessageKey(MessageKey.WELCOME_MESSAGE)) {
            MessageTemplate welcomeTemplate = new MessageTemplate();
            welcomeTemplate.setMessageKey(MessageKey.WELCOME_MESSAGE);
            welcomeTemplate.setText(
                "🎉 Assalomu alaykum!\n\n" +
                "Matematikadan Milliy sertifikat imtihoniga bepul tayyorlanish imkoniyati!\n" +
                "Va albatta siz uchun *1 ta bonus kitob* tayyorlab qo'ydim 📚✨\n\n" +
                "🌟 Har bir yangi do'st taklif qilsangiz — *bonus ball* beriladi!\n" +
                "🚀 Har bir taklif sizni sertifikatga yaqinlashtiradi!\n\n" +
                "💥 Do'stingiz botga kirib kanalga a'zo bo'lsa — *+1 ball avto qo'shiladi!*\n\n" +
                "🎯 5 ta matematikachi do'st taklif qilsangiz:\n" +
                "🔥 *\"Geometriya 0 dan\" yopiq guruhiga bepul umrbod kirish!*\n\n" +
                "🎁 Yuqori natija ko'rsatsangiz — *\"Geometriya Pro\"* guruhida o'qiysiz!\n\n" +
                "📘 Bonus kitob faqat faol ishtirokchilarga!\n\n" +
                "✨ Imkoniyatni qo'ldan boy bermang!\n\n" +
                "❗ *Muhim:*\n" +
                "Ball olish uchun do'stingiz botga kirib, majburiy kanallarga a'zo bo'lib, 10 kun chiqmasligi kerak\n\n" +
                "💡 Do'stlaringizni hozir taklif qiling:\n" +
                "(Havolani olish uchun Takliflarim tugmasini bosing...)\n\n" +
                "📘 Asosiy menyuga xush kelibsiz!"
            );
            repository.save(welcomeTemplate);
            log.info("Initialized default WELCOME_MESSAGE template");
        }
        
        // Initialize STATS_HEADER
        if (!repository.existsByMessageKey(MessageKey.STATS_HEADER)) {
            MessageTemplate statsTemplate = new MessageTemplate();
            statsTemplate.setMessageKey(MessageKey.STATS_HEADER);
            statsTemplate.setText("📊 *Sizning statistikangiz:*\n");
            repository.save(statsTemplate);
            log.info("Initialized default STATS_HEADER template");
        }
    }
}
