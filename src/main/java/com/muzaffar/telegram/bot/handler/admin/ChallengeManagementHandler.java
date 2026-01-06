package com.muzaffar.telegram.bot.handler.admin;

import static com.muzaffar.telegram.constants.BotConstants.*;

import com.muzaffar.telegram.entity.Challenge;
import com.muzaffar.telegram.service.ChallengeService;
import com.muzaffar.telegram.util.KeyboardBuilder;
import com.muzaffar.telegram.util.TelegramBotUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Handler for challenge management operations.
 * CRUD operations for challenges.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChallengeManagementHandler {

    private final ChallengeService challengeService;
    private final AdminStateManager stateManager;
    private final AdminNavigationHandler navigationHandler;
    private final TelegramBotUtils botUtils;

    private Supplier<TelegramLongPollingBot> botSupplier;

    public void init(Supplier<TelegramLongPollingBot> botSupplier) {
        this.botSupplier = botSupplier;
    }

    /**
     * Show list of all challenges
     */
    public void showChallengeList(Long chatId, Integer messageId) {
        List<Challenge> challenges = challengeService.getAllChallenges();

        if (challenges.isEmpty()) {
            InlineKeyboardMarkup backButton = new KeyboardBuilder()
                    .addButton("🔙 Orqaga", CALLBACK_CHALLENGE_MANAGEMENT)
                    .build();
            editMessage(chatId, messageId,
                    "📋 *Challengelar ro'yxati*\n\nHali challengelar yaratilmagan.",
                    backButton);
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📋 *Challengelar ro'yxati:*\n\n");

        KeyboardBuilder builder = new KeyboardBuilder();
        for (Challenge challenge : challenges) {
            ChallengeService.ChallengeStats stats = challengeService.getChallengeStats(challenge.getId());

            String statusEmoji = challenge.getIsActive() ? "✅" : "❌";
            String mainIndicator = challenge.getIsMain() ? " ⭐" : "";

            sb.append(String.format(
                    "%s *%s*%s\n" +
                            "   Referallar: %d ta kerak\n" +
                            "   Ishtirokchilar: %d ta\n" +
                            "   Bajarganlar: %d ta\n\n",
                    statusEmoji,
                    challenge.getName(),
                    mainIndicator,
                    challenge.getRequiredReferralCount(),
                    stats.totalUsers(),
                    stats.completedUsers()));

            builder.addRow(
                    String.format("%s %s%s", statusEmoji, challenge.getName(), mainIndicator),
                    CALLBACK_CHALLENGE_SELECT_PREFIX + challenge.getId());
        }

        InlineKeyboardMarkup keyboard = builder
                .addRow("🔙 Orqaga", CALLBACK_CHALLENGE_MANAGEMENT)
                .build();

        editMessage(chatId, messageId, sb.toString(), keyboard);
    }

    /**
     * Select a challenge to manage
     */
    public void selectChallenge(Long chatId, Integer messageId, Long challengeId) {
        stateManager.setCurrentChallengeId(chatId, challengeId);

        Challenge challenge = challengeService.getChallengeById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));

        ChallengeService.ChallengeStats stats = challengeService.getChallengeStats(challengeId);

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);

        String mainStatus = challenge.getIsMain() ? "⭐ Asosiy challenge" : "";
        message.setText(String.format(
                "🏆 *%s*%s\n\n" +
                        "📊 *Statistika:*\n" +
                        "Ishtirokchilar: %d ta\n" +
                        "Bajarganlar: %d ta\n" +
                        "Kanallar: %d ta\n" +
                        "Referallar: %d ta kerak\n\n" +
                        "Holati: %s\n\n" +
                        "Boshqarish panelini tanlang:",
                challenge.getName(),
                mainStatus.isEmpty() ? "" : " (" + mainStatus + ")",
                stats.totalUsers(),
                stats.completedUsers(),
                stats.totalChannels(),
                challenge.getRequiredReferralCount(),
                challenge.getIsActive() ? "✅ Faol" : "❌ Faol emas"));
        message.setParseMode("Markdown");

        KeyboardBuilder builder = new KeyboardBuilder()
                .addButton("📺 Kanallarni boshqarish", CALLBACK_MENU_CHANNELS)
                .newRow()
                .addButton("⚙️ Sozlamalar", CALLBACK_MENU_SETTINGS)
                .addButton("📢 Xabar yuborish", CALLBACK_MENU_BROADCAST)
                .newRow();

        if (!challenge.getIsMain() && challenge.getIsActive()) {
            builder.addButton("⭐ Asosiy challenge qilish", CALLBACK_CHALLENGE_SET_MAIN)
                    .newRow();
        }

        builder.addButton("❌ Bekor qilish", CALLBACK_CHALLENGE_MANAGEMENT);

        InlineKeyboardMarkup keyboard = builder.build();
        message.setReplyMarkup(keyboard);

        try {
            botSupplier.get().execute(message);
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException e) {
            if (e.getMessage() != null && e.getMessage().contains("message is not modified")) {
                log.debug("Message already shows this challenge panel, skipping update for chatId={}", chatId);
            } else {
                log.error("Error showing challenge panel", e);
            }
        } catch (TelegramApiException e) {
            log.error("Error showing challenge panel", e);
        }
    }

    /**
     * Start challenge creation flow
     */
    public void startCreateChallenge(Long chatId, Integer messageId) {
        stateManager.setState(chatId, STATE_AWAITING_CHALLENGE_NAME);
        stateManager.initUserData(chatId);

        InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                .addButton("🔙 Orqaga", CALLBACK_CHALLENGE_MANAGEMENT)
                .build();

        editMessage(chatId, messageId,
                "➕ *Yangi challenge yaratish*\n\n" +
                        "1️⃣ Challenge nomini kiriting:\n\n" +
                        "*Masalan:* Bahorgi Challenge 2025",
                keyboard);
    }

    /**
     * Handle challenge name input
     */
    public void handleChallengeNameInput(Long chatId, String name, Integer messageId) {
        botUtils.deleteMessage(botSupplier.get(), chatId, messageId);
        stateManager.setUserData(chatId, "challengeName", name);

        stateManager.setState(chatId, STATE_AWAITING_CHALLENGE_REFERRAL_COUNT);
        sendMessage(chatId,
                "✅ Nom qabul qilindi.\n\n" +
                        "2️⃣ Kerakli referallar sonini kiriting:\n\n" +
                        "*Masalan:* 5",
                new KeyboardBuilder().addButton("🔙 Orqaga", CALLBACK_CHALLENGE_MANAGEMENT).build());
    }

    /**
     * Handle challenge referral count input
     */
    public void handleChallengeReferralCountInput(Long chatId, String text, Integer messageId) {
        try {
            int count = Integer.parseInt(text.trim());

            if (count < 1) {
                sendMessage(chatId, "❌ Referallar soni kamida 1 bo'lishi kerak.\n\nQaytadan kiriting:",
                        new KeyboardBuilder().addButton("🔙 Orqaga", CALLBACK_CHALLENGE_MANAGEMENT).build());
                return;
            }

            botUtils.deleteMessage(botSupplier.get(), chatId, messageId);
            stateManager.setUserData(chatId, "challengeReferralCount", String.valueOf(count));

            stateManager.setState(chatId, STATE_AWAITING_CHALLENGE_PRIVATE_LINK);
            sendMessage(chatId,
                    "✅ Referallar soni qabul qilindi.\n\n" +
                            "3️⃣ Shaxsiy kanal ID raqamini kiriting:\n\n" +
                            "*Masalan:* `-1001234567890`\n\n" +
                            "⚠️ Bot shaxsiy kanalda admin bo'lishi kerak!",
                    new KeyboardBuilder().addButton("🔙 Orqaga", CALLBACK_CHALLENGE_MANAGEMENT).build());
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Noto'g'ri format. Faqat raqam kiriting.",
                    new KeyboardBuilder().addButton("🔙 Orqaga", CALLBACK_CHALLENGE_MANAGEMENT).build());
        }
    }

    /**
     * Handle challenge private channel ID input
     */
    public void handleChallengePrivateLinkInput(Long chatId, String channelId, Integer messageId) {
        String trimmedId = channelId.trim();

        if (!trimmedId.matches("-?\\d+")) {
            sendMessage(chatId,
                    "❌ *Noto'g'ri format:*\n\nKanal ID faqat raqamlardan iborat bo'lishi kerak (masalan: `-1001234567890`).\n\nQaytadan kiriting:",
                    new KeyboardBuilder().addButton("🔙 Orqaga", CALLBACK_CHALLENGE_MANAGEMENT).build());
            return;
        }

        botUtils.deleteMessage(botSupplier.get(), chatId, messageId);
        Map<String, String> data = stateManager.getAllUserData(chatId);

        String name = data.get("challengeName");
        int referralCount = Integer.parseInt(data.get("challengeReferralCount"));

        Challenge challenge = challengeService.createChallenge(name, referralCount, trimmedId);

        sendMessage(chatId,
                String.format(
                        "✅ *Challenge yaratildi!*\n\n" +
                                "🏆 *Nom:* %s\n" +
                                "🔢 *Referallar:* %d ta\n" +
                                "🔗 *Kanal ID:* `%s`\n\n" +
                                "Challenge faol holga keltirildi.",
                        name, referralCount, trimmedId),
                navigationHandler.createBackButton());

        stateManager.clearState(chatId);
        stateManager.clearUserData(chatId);
    }

    /**
     * Handle setting a challenge as main
     */
    public void handleSetMainChallenge(Long chatId, Integer messageId) {
        Long challengeId = stateManager.getCurrentChallengeId(chatId);
        if (challengeId == null) {
            sendMessage(chatId, "❌ Xatolik: Challenge topilmadi");
            return;
        }

        try {
            challengeService.setMainChallenge(challengeId);
            selectChallenge(chatId, messageId, challengeId);
        } catch (IllegalArgumentException e) {
            sendMessage(chatId, "❌ Xatolik: " + e.getMessage());
        }
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

    private void sendMessage(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        if (keyboard != null) {
            botUtils.sendMessageWithKeyboard(botSupplier.get(), chatId, text, keyboard);
        } else {
            botUtils.sendMessage(botSupplier.get(), chatId, text);
        }
    }
}
