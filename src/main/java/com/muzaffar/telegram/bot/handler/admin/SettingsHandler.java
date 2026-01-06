package com.muzaffar.telegram.bot.handler.admin;

import static com.muzaffar.telegram.constants.BotConstants.*;

import com.muzaffar.telegram.entity.Challenge;
import com.muzaffar.telegram.enums.MessageKey;
import com.muzaffar.telegram.service.ChallengeService;
import com.muzaffar.telegram.service.MessageTemplateService;
import com.muzaffar.telegram.util.KeyboardBuilder;
import com.muzaffar.telegram.util.TelegramBotUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Supplier;

/**
 * Handler for settings operations.
 * Manages referral count, private link, information message, and stats header
 * settings.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingsHandler {

    private final ChallengeService challengeService;
    private final MessageTemplateService messageTemplateService;
    private final AdminStateManager stateManager;
    private final AdminNavigationHandler navigationHandler;
    private final TelegramBotUtils botUtils;

    private Supplier<TelegramLongPollingBot> botSupplier;

    public void init(Supplier<TelegramLongPollingBot> botSupplier) {
        this.botSupplier = botSupplier;
    }

    // ==================== Referral Count ====================

    public void startSetReferrals(Long chatId, Integer messageId) {
        Long challengeId = stateManager.getCurrentChallengeId(chatId);
        if (challengeId == null) {
            sendMessage(chatId, "❌ Challenge tanlanmagan.");
            return;
        }

        Challenge challenge = challengeService.getChallengeById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));

        stateManager.setState(chatId, STATE_AWAITING_REFERRAL_COUNT);
        stateManager.setMenuMessageId(chatId, messageId);

        int currentCount = challenge.getRequiredReferralCount();
        InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                .addButton("🔙 Orqaga", CALLBACK_MENU_SETTINGS)
                .build();
        editMessage(chatId, messageId,
                String.format(
                        "🔢 *Referallar sonini o'zgartirish*\n\nHozirgi qiymat: *%d*\n\nYangi qiymatni yuboring (1 dan katta son):",
                        currentCount),
                keyboard);
    }

    public void handleReferralCountInput(Long chatId, String text, Integer messageId) {
        Long challengeId = stateManager.getCurrentChallengeId(chatId);
        if (challengeId == null) {
            sendMessage(chatId, "❌ Challenge tanlanmagan.", navigationHandler.createBackButton());
            stateManager.clearState(chatId);
            stateManager.clearMenuMessageId(chatId);
            return;
        }

        try {
            int count = Integer.parseInt(text.trim());

            if (count < 1) {
                InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                        .addButton("🔙 Orqaga", CALLBACK_MENU_SETTINGS)
                        .build();
                sendMessage(chatId,
                        "❌ *Noto'g'ri format:*\n\nReferallar soni kamida 1 bo'lishi kerak.\n\nQaytadan kiriting:",
                        keyboard);
                return;
            }

            botUtils.deleteMessage(botSupplier.get(), chatId, messageId);

            challengeService.updateRequiredReferralCount(challengeId, count);

            sendMessage(chatId, String.format("✅ *Referallar soni o'zgartirildi!*\n\nYangi qiymat: *%d*", count),
                    navigationHandler.createBackButton());

            stateManager.clearState(chatId);
            stateManager.clearMenuMessageId(chatId);
        } catch (NumberFormatException e) {
            InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                    .addButton("🔙 Orqaga", CALLBACK_MENU_SETTINGS)
                    .build();
            sendMessage(chatId, "❌ *Noto'g'ri format:*\n\nFaqat raqam kiriting. Qaytadan urinib ko'ring.", keyboard);
        }
    }

    // ==================== Private Link ====================

    public void startSetPrivateLink(Long chatId, Integer messageId) {
        Long challengeId = stateManager.getCurrentChallengeId(chatId);
        if (challengeId == null) {
            sendMessage(chatId, "❌ Challenge tanlanmagan.");
            return;
        }

        Challenge challenge = challengeService.getChallengeById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));

        stateManager.setState(chatId, STATE_AWAITING_PRIVATE_LINK);
        stateManager.setMenuMessageId(chatId, messageId);

        String currentId = challenge.getPrivateChannelId();
        if (currentId == null || currentId.isEmpty()) {
            currentId = "O'rnatilmagan";
        }
        InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                .addButton("🔙 Orqaga", CALLBACK_MENU_SETTINGS)
                .build();
        editMessage(chatId, messageId,
                String.format("🔗 *Shaxsiy kanal ID o'zgartirish*\n\n" +
                        "Hozirgi ID: `%s`\n\n" +
                        "Bir martalik havolalar yaratish uchun shaxsiy kanalning *ID raqamini* yuboring.\n\n" +
                        "*Masalan:* `-1001234567890`\n\n" +
                        "⚠️ *Muhim:* Bot shaxsiy kanalda admin bo'lishi kerak!", currentId),
                keyboard);
    }

    public void handlePrivateLinkInput(Long chatId, String text, Integer messageId) {
        Long challengeId = stateManager.getCurrentChallengeId(chatId);
        if (challengeId == null) {
            sendMessage(chatId, "❌ Challenge tanlanmagan.", navigationHandler.createBackButton());
            stateManager.clearState(chatId);
            stateManager.clearMenuMessageId(chatId);
            return;
        }

        String channelId = text.trim();

        if (!channelId.matches("-?\\d+")) {
            InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                    .addButton("🔙 Orqaga", CALLBACK_MENU_SETTINGS)
                    .build();
            sendMessage(chatId,
                    "❌ *Noto'g'ri format:*\n\nKanal ID faqat raqamlardan iborat bo'lishi kerak (masalan: `-1001234567890`).\n\nQaytadan kiriting:",
                    keyboard);
            return;
        }

        botUtils.deleteMessage(botSupplier.get(), chatId, messageId);

        challengeService.updatePrivateChannelId(challengeId, channelId);

        sendMessage(chatId, String.format("✅ *Shaxsiy kanal ID o'zgartirildi!*\n\nYangi ID: `%s`\n\n" +
                "Endi bir martalik havolalar avtomatik yaratiladi.", channelId), navigationHandler.createBackButton());

        stateManager.clearState(chatId);
        stateManager.clearMenuMessageId(chatId);
    }

    // ==================== Information Message ====================

    public void startSetInformationMessage(Long chatId, Integer messageId) {
        botUtils.deleteMessage(botSupplier.get(), chatId, messageId);

        Long challengeId = getCurrentChallengeId(chatId);
        messageTemplateService.getImagePath(challengeId, MessageKey.INFORMATION_MESSAGE).ifPresent(imagePath -> {
            try {
                String fullPath = botUtils.getFullImagePath(imagePath);
                SendPhoto sendPhoto = new SendPhoto();
                sendPhoto.setChatId(chatId.toString());
                sendPhoto.setPhoto(new InputFile(new File(fullPath)));
                sendPhoto.setCaption("🖼 *Hozirgi rasm:*");
                Message sentPhoto = botSupplier.get().execute(sendPhoto);
                stateManager.trackTempMessage(chatId, sentPhoto.getMessageId());
            } catch (Exception e) {
                log.error("Error sending current information image", e);
            }
        });

        String currentText = messageTemplateService.getText(challengeId, MessageKey.INFORMATION_MESSAGE);
        boolean hasImage = messageTemplateService.getImagePath(challengeId, MessageKey.INFORMATION_MESSAGE).isPresent();

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(
                String.format(
                        "📝 *Information xabarini o'zgartirish*\n\n" +
                                "📝 *Hozirgi matn:*\n```\n%s\n```\n\n" +
                                "🖼 *Rasm:* %s\n\n" +
                                "Kerakli amalni tanlang:",
                        currentText,
                        hasImage ? "✅ O'rnatilgan" : "❌ O'rnatilmagan"));
        message.setParseMode("Markdown");

        KeyboardBuilder builder = new KeyboardBuilder()
                .addRow("🔹 Matnni o'zgartirish", CALLBACK_EDIT_INFORMATION_TEXT)
                .addRow(hasImage ? "🔹 Rasmni o'zgartirish" : "🔹 Rasm qo'shish", CALLBACK_EDIT_INFORMATION_IMAGE);

        if (hasImage) {
            builder.addButton("🔹 Rasmni o'chirish", CALLBACK_DELETE_INFORMATION_IMAGE);
        }

        InlineKeyboardMarkup keyboard = builder
                .addRow("🔙 Orqaga", CALLBACK_MENU_SETTINGS)
                .build();
        message.setReplyMarkup(keyboard);

        try {
            botSupplier.get().execute(message);
        } catch (TelegramApiException e) {
            log.error("Error showing information message menu", e);
        }
    }

    public void startEditInformationText(Long chatId, Integer messageId) {
        stateManager.setState(chatId, STATE_AWAITING_INFORMATION_MESSAGE);
        stateManager.setMenuMessageId(chatId, messageId);

        Long challengeId = getCurrentChallengeId(chatId);
        String currentText = messageTemplateService.getText(challengeId, MessageKey.INFORMATION_MESSAGE);

        editMessage(chatId, messageId,
                String.format(
                        "📝 *Information matnini o'zgartirish*\n\n" +
                                "📝 *Hozirgi matn:*\n```\n%s\n```\n\n" +
                                "Yangi matnni yuboring:\n\n" +
                                "*Eslatma:* Markdown formatidan foydalanishingiz mumkin (*bold*, _italic_, `code`)",
                        currentText),
                new KeyboardBuilder()
                        .addButton("🔙 Orqaga", CALLBACK_SETTINGS_INFORMATION)
                        .build());
    }

    public void startEditInformationImage(Long chatId, Integer messageId) {
        stateManager.setState(chatId, STATE_AWAITING_INFORMATION_IMAGE);

        Long challengeId = getCurrentChallengeId(chatId);
        messageTemplateService.getImagePath(challengeId, MessageKey.INFORMATION_MESSAGE).ifPresent(imagePath -> {
            try {
                Message sentPhoto = botUtils.sendPhotoWithCaption(botSupplier.get(), chatId, imagePath,
                        "🖼 *Hozirgi rasm:*");
                if (sentPhoto != null) {
                    stateManager.trackTempMessage(chatId, sentPhoto.getMessageId());
                }
            } catch (Exception e) {
                log.error("Error sending current information image", e);
            }
        });

        Message message = sendMessage(chatId,
                "🖼 *Information rasmini o'zgartirish*\n\n" +
                        "Yangi rasmni yuboring (foto yoki fayl ko'rinishida):\n\n" +
                        "*Eslatma:* Sifatli rasm uchun faylni siqilmagan holda yuboring (Document).",
                new KeyboardBuilder()
                        .addButton("🔙 Orqaga", CALLBACK_SETTINGS_INFORMATION)
                        .build());
        if (message != null) {
            stateManager.trackTempMessage(chatId, message.getMessageId());
        }
    }

    public void confirmDeleteInformationImage(Long chatId, Integer messageId) {
        Long challengeId = getCurrentChallengeId(chatId);
        messageTemplateService.getImagePath(challengeId, MessageKey.INFORMATION_MESSAGE).ifPresent(oldPath -> {
            try {
                String fullOldPath = botUtils.getFullImagePath(oldPath);
                Files.deleteIfExists(Paths.get(fullOldPath));
                log.info("Deleted information image: {}", fullOldPath);
            } catch (IOException e) {
                log.error("Failed to delete old image file", e);
            }
        });

        messageTemplateService.updateImagePath(challengeId, MessageKey.INFORMATION_MESSAGE, "");

        editMessage(chatId, messageId,
                "✅ *Information rasmi o'chirildi!*\n\nRasm endi ko'rsatilmaydi.",
                navigationHandler.createBackButton());
    }

    public void handleInformationMessageInput(Long chatId, String text, Integer messageId) {
        String trimmedText = text.trim();

        if (trimmedText.isEmpty()) {
            InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                    .addButton("🔙 Orqaga", CALLBACK_SETTINGS_INFORMATION)
                    .build();
            sendMessage(chatId, "❌ *Noto'g'ri format:*\n\nXabar bo'sh bo'lishi mumkin emas.\n\nQaytadan kiriting:",
                    keyboard);
            return;
        }

        botUtils.deleteMessage(botSupplier.get(), chatId, messageId);
        Long challengeId = getCurrentChallengeId(chatId);
        messageTemplateService.updateText(challengeId, MessageKey.INFORMATION_MESSAGE, trimmedText);
        sendMessage(chatId, "✅ *Information xabari o'zgartirildi!*\n\nYangi foydalanuvchilar ushbu xabarni ko'rishadi.",
                navigationHandler.createBackButton());

        stateManager.clearState(chatId);
        stateManager.clearMenuMessageId(chatId);
    }

    // ==================== Stats Header ====================

    public void startSetStatsHeader(Long chatId, Integer messageId) {
        botUtils.deleteMessage(botSupplier.get(), chatId, messageId);

        Long challengeId = getCurrentChallengeId(chatId);
        messageTemplateService.getImagePath(challengeId, MessageKey.STATS_HEADER).ifPresent(imagePath -> {
            try {
                String fullPath = botUtils.getFullImagePath(imagePath);
                SendPhoto sendPhoto = new SendPhoto();
                sendPhoto.setChatId(chatId.toString());
                sendPhoto.setPhoto(new InputFile(new File(fullPath)));
                sendPhoto.setCaption("🖼 *Hozirgi rasm:*");
                Message sentPhoto = botSupplier.get().execute(sendPhoto);
                stateManager.trackTempMessage(chatId, sentPhoto.getMessageId());
            } catch (Exception e) {
                log.error("Error sending current stats header image", e);
            }
        });

        String currentText = messageTemplateService.getText(challengeId, MessageKey.STATS_HEADER);
        boolean hasImage = messageTemplateService.getImagePath(challengeId, MessageKey.STATS_HEADER).isPresent();

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(
                String.format(
                        "📊 *Statistika sarlavhasini o'zgartirish*\n\n" +
                                "📝 *Hozirgi matn:*\n```\n%s\n```\n\n" +
                                "🖼 *Rasm:* %s\n\n" +
                                "Kerakli amalni tanlang:",
                        currentText,
                        hasImage ? "✅ O'rnatilgan" : "❌ O'rnatilmagan"));
        message.setParseMode("Markdown");

        KeyboardBuilder builder = new KeyboardBuilder()
                .addRow("🔹 Matnni o'zgartirish", CALLBACK_EDIT_STATS_TEXT)
                .addRow(hasImage ? "🔹 Rasmni o'zgartirish" : "🔹 Rasm qo'shish", CALLBACK_EDIT_STATS_IMAGE);

        if (hasImage) {
            builder.addButton("🔹 Rasmni o'chirish", CALLBACK_DELETE_STATS_IMAGE);
        }

        InlineKeyboardMarkup keyboard = builder
                .addRow("🔙 Orqaga", CALLBACK_MENU_SETTINGS)
                .build();
        message.setReplyMarkup(keyboard);

        try {
            botSupplier.get().execute(message);
        } catch (TelegramApiException e) {
            log.error("Error showing stats header menu", e);
        }
    }

    public void startEditStatsText(Long chatId, Integer messageId) {
        stateManager.setState(chatId, STATE_AWAITING_STATS_HEADER);
        stateManager.setMenuMessageId(chatId, messageId);

        Long challengeId = getCurrentChallengeId(chatId);
        String currentText = messageTemplateService.getText(challengeId, MessageKey.STATS_HEADER);
        if (currentText == null || currentText.isEmpty()) {
            currentText = "O'rnatilmagan";
        }

        editMessage(chatId, messageId,
                String.format(
                        "📊 *Statistika matnini o'zgartirish*\n\n" +
                                "📝 *Hozirgi matn:*\n```\n%s\n```\n\n" +
                                "Yangi matnni yuboring:\n\n" +
                                "*Eslatma:* Markdown formatidan foydalanishingiz mumkin (*bold*, _italic_, `code`)",
                        currentText),
                new KeyboardBuilder()
                        .addButton("🔙 Orqaga", CALLBACK_SETTINGS_SET_STATS_TEXT)
                        .build());
    }

    public void startEditStatsImage(Long chatId, Integer messageId) {
        stateManager.setState(chatId, STATE_AWAITING_STATS_IMAGE);

        Long challengeId = getCurrentChallengeId(chatId);
        messageTemplateService.getImagePath(challengeId, MessageKey.STATS_HEADER).ifPresent(imagePath -> {
            try {
                Message sentPhoto = botUtils.sendPhotoWithCaption(botSupplier.get(), chatId, imagePath,
                        "🖼 *Hozirgi rasm:*");
                if (sentPhoto != null) {
                    stateManager.trackTempMessage(chatId, sentPhoto.getMessageId());
                }
            } catch (Exception e) {
                log.error("Error sending current stats header image", e);
            }
        });

        Message message = sendMessage(chatId,
                "🖼 *Statistika rasmini o'zgartirish*\n\n" +
                        "Yangi rasmni yuboring (foto yoki fayl ko'rinishida):\n\n" +
                        "*Eslatma:* Sifatli rasm uchun faylni siqilmagan holda yuboring (Document).",
                new KeyboardBuilder()
                        .addButton("🔙 Orqaga", CALLBACK_SETTINGS_SET_STATS_TEXT)
                        .build());
        if (message != null) {
            stateManager.trackTempMessage(chatId, message.getMessageId());
        }
    }

    public void confirmDeleteStatsImage(Long chatId, Integer messageId) {
        Long challengeId = getCurrentChallengeId(chatId);
        messageTemplateService.getImagePath(challengeId, MessageKey.STATS_HEADER).ifPresent(oldPath -> {
            try {
                String fullOldPath = botUtils.getFullImagePath(oldPath);
                Files.deleteIfExists(Paths.get(fullOldPath));
                log.info("Deleted stats header image: {}", fullOldPath);
            } catch (IOException e) {
                log.error("Failed to delete old image file", e);
            }
        });

        messageTemplateService.updateImagePath(challengeId, MessageKey.STATS_HEADER, "");

        editMessage(chatId, messageId,
                "✅ *Statistika rasmi o'chirildi!*\n\nRasm endi ko'rsatilmaydi.",
                navigationHandler.createBackButton());
    }

    public void handleStatsHeaderInput(Long chatId, String text, Integer messageId) {
        String trimmedText = text.trim();

        if (trimmedText.isEmpty()) {
            InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                    .addButton("🔙 Orqaga", CALLBACK_SETTINGS_SET_STATS_TEXT)
                    .build();
            sendMessage(chatId, "❌ *Noto'g'ri format:*\n\nMatn bo'sh bo'lishi mumkin emas.\n\nQaytadan kiriting:",
                    keyboard);
            return;
        }

        botUtils.deleteMessage(botSupplier.get(), chatId, messageId);
        Long challengeId = getCurrentChallengeId(chatId);
        messageTemplateService.updateText(challengeId, MessageKey.STATS_HEADER, trimmedText);
        sendMessage(chatId, "✅ *Statistika sarlavhasi o'zgartirildi!*", navigationHandler.createBackButton());

        stateManager.clearState(chatId);
        stateManager.clearMenuMessageId(chatId);
    }

    private Long getCurrentChallengeId(Long chatId) {
        Long challengeId = stateManager.getCurrentChallengeId(chatId);
        if (challengeId != null) {
            return challengeId;
        }
        return challengeService.getDefaultChallenge()
                .map(Challenge::getId)
                .orElse(1L);
    }

    private void editMessage(Long chatId, Integer messageId, String text, InlineKeyboardMarkup keyboard) {
        try {
            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(messageId);
            message.setText(text);
            message.setParseMode("Markdown");
            message.setReplyMarkup(keyboard);
            botSupplier.get().execute(message);
        } catch (TelegramApiException e) {
            log.error("Error editing message", e);
        }
    }

    private void sendMessage(Long chatId, String text) {
        sendMessage(chatId, text, null);
    }

    private Message sendMessage(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        if (keyboard != null) {
            return botUtils.sendMessageWithKeyboard(botSupplier.get(), chatId, text, keyboard);
        } else {
            botUtils.sendMessage(botSupplier.get(), chatId, text);
            return null;
        }
    }
}
