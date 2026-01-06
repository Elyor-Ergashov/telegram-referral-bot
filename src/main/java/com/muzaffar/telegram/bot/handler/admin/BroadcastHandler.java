package com.muzaffar.telegram.bot.handler.admin;

import static com.muzaffar.telegram.constants.BotConstants.*;

import com.muzaffar.telegram.bot.ReferralTelegramBot;
import com.muzaffar.telegram.entity.Challenge;
import com.muzaffar.telegram.entity.ChallengeUser;
import com.muzaffar.telegram.service.ChallengeService;
import com.muzaffar.telegram.service.ChallengeUserService;
import com.muzaffar.telegram.service.MessageService;
import com.muzaffar.telegram.util.KeyboardBuilder;
import com.muzaffar.telegram.util.TelegramBotUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;
import java.util.function.Supplier;

/**
 * Handler for broadcast operations.
 * Send information messages to all users or selected users.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BroadcastHandler {

    private final ChallengeService challengeService;
    private final ChallengeUserService challengeUserService;
    private final MessageService messageService;
    private final com.muzaffar.telegram.service.MessageTemplateService messageTemplateService;
    private final AdminStateManager stateManager;
    private final AdminNavigationHandler navigationHandler;
    private final TelegramBotUtils botUtils;

    private Supplier<TelegramLongPollingBot> botSupplier;
    private Supplier<ReferralTelegramBot> referralBotSupplier;

    public void init(Supplier<TelegramLongPollingBot> botSupplier, Supplier<ReferralTelegramBot> referralBotSupplier) {
        this.botSupplier = botSupplier;
        this.referralBotSupplier = referralBotSupplier;
    }

    /**
     * Confirm broadcast information message to all users
     */
    public void confirmBroadcastInformationAll(Long chatId, Integer messageId) {
        // Validation before confirmation dialog
        Long selectedChallengeId = getCurrentChallengeId(chatId);
        String informationText = messageTemplateService.getText(selectedChallengeId,
                com.muzaffar.telegram.enums.MessageKey.INFORMATION_MESSAGE);

        if (informationText == null || informationText.trim().isEmpty()) {
            editMessage(chatId, messageId,
                    "❌ *Xatolik:*\n\nInformation Message matni kiritilmagan. Iltimos, oldin \"Sozlamalar\" -> \"Information xabarini o'zgartirish\" bo'limida matnni kiriting.",
                    navigationHandler.createBackButton());
            return;
        }

        List<Challenge> challenges = challengeService.getAllChallenges();

        StringBuilder statsText = new StringBuilder();
        statsText.append("📢 *Barcha foydalanuvchilarga yuborish*\n\n");
        statsText.append("📊 *Challengelar bo'yicha statistika:*\n\n");

        Set<Long> uniqueUserIds = new HashSet<>();
        int totalInAllChallenges = 0;

        for (Challenge challenge : challenges) {
            List<ChallengeUser> challengeUsers = challengeUserService.getUsersInChallenge(challenge.getId());

            int count = challengeUsers.size();
            totalInAllChallenges += count;

            for (ChallengeUser cu : challengeUsers) {
                uniqueUserIds.add(cu.getUserChatId());
            }

            statsText.append(String.format("• *%s*: %d ta foydalanuvchi\n",
                    challenge.getName(), count));
        }

        statsText.append("\n");
        statsText.append(String.format("📊 *Jami noyob foydalanuvchilar:* %d ta\n", uniqueUserIds.size()));
        statsText.append(String.format("📋 *Barcha ishtiroklar:* %d ta\n\n", totalInAllChallenges));
        statsText.append("Information xabari barcha noyob foydalanuvchilarga yuboriladi.\n");
        statsText.append("(Har bir foydalanuvchi bitta xabar oladi)\n\n");
        statsText.append("Davom etishni xohlaysizmi?");

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(statsText.toString());
        message.setParseMode("Markdown");

        InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                .addButton("✅ Ha, yuborish", CALLBACK_BROADCAST_INFORMATION_ALL_CONFIRM)
                .newRow()
                .addButton("🔙 Orqaga", CALLBACK_MENU_BROADCAST)
                .build();
        message.setReplyMarkup(keyboard);

        try {
            botSupplier.get().execute(message);
        } catch (TelegramApiException e) {
            log.error("Error showing broadcast confirmation", e);
        }
    }

    /**
     * Execute broadcast information message to all unique users
     */
    public void executeBroadcastInformationAll(Long chatId, Integer messageId) {
        Long selectedChallengeId = getCurrentChallengeId(chatId);

        // Double check validation
        String informationText = messageTemplateService.getText(selectedChallengeId,
                com.muzaffar.telegram.enums.MessageKey.INFORMATION_MESSAGE);
        if (informationText == null || informationText.trim().isEmpty()) {
            botUtils.deleteMessage(botSupplier.get(), chatId, messageId);
            botUtils.sendMessageWithKeyboard(botSupplier.get(), chatId,
                    "❌ *Xatolik:*\n\nInformation Message matni kiritilmagan. Iltimos, oldin \"Sozlamalar\" bo'limida matnni kiriting.",
                    navigationHandler.createBackButton());
            return;
        }

        botUtils.deleteMessage(botSupplier.get(), chatId, messageId);

        botUtils.sendMessage(botSupplier.get(), chatId, "⏳ Xabarlar yuborilmoqda...");

        List<Challenge> challenges = challengeService.getAllChallenges();
        Set<Long> uniqueUserIds = new HashSet<>();

        for (Challenge challenge : challenges) {
            List<ChallengeUser> challengeUsers = challengeUserService.getUsersInChallenge(challenge.getId());

            for (ChallengeUser cu : challengeUsers) {
                uniqueUserIds.add(cu.getUserChatId());
            }
        }

        int successCount = 0;
        int failCount = 0;
        int totalUsers = uniqueUserIds.size();

        for (Long userChatId : uniqueUserIds) {
            try {
                sendInformationMessageToUser(selectedChallengeId, userChatId);
                successCount++;

                Thread.sleep(BROADCAST_DELAY_MS);
            } catch (Exception e) {
                log.error("Failed to send information message to chatId={}", userChatId, e);
                failCount++;
            }
        }

        String resultText = String.format(
                "✅ *Yuborish yakunlandi!*\n\n" +
                        "Muvaffaqiyatli: *%d*\n" +
                        "Xatolik: *%d*\n" +
                        "Jami noyob foydalanuvchilar: *%d*",
                successCount, failCount, totalUsers);

        botUtils.sendMessageWithKeyboard(botSupplier.get(), chatId, resultText, navigationHandler.createBackButton());

        log.info("Broadcast completed to all users: success={}, failed={}, total unique users={}",
                successCount, failCount, totalUsers);
    }

    /**
     * Start broadcast information to multiple users
     */
    public void startBroadcastInformationSingle(Long chatId, Integer messageId) {
        Long selectedChallengeId = getCurrentChallengeId(chatId);
        String informationText = messageTemplateService.getText(selectedChallengeId,
                com.muzaffar.telegram.enums.MessageKey.INFORMATION_MESSAGE);

        if (informationText == null || informationText.trim().isEmpty()) {
            editMessage(chatId, messageId,
                    "❌ *Xatolik:*\n\nInformation Message matni kiritilmagan. Iltimos, oldin \"Sozlamalar\" -> \"Information xabarini o'zgartirish\" bo'limida matnni kiriting.",
                    navigationHandler.createBackButton());
            return;
        }

        stateManager.setState(chatId, STATE_AWAITING_BROADCAST_MESSAGE);
        stateManager.setMenuMessageId(chatId, messageId);

        editMessage(chatId, messageId,
                "👥 *Tanlangan foydalanuvchilarga yuborish*\n\n" +
                        "Foydalanuvchilar Chat ID sini vergul bilan ajratib yuboring:\n\n" +
                        "*Misol:*\n" +
                        "`123456789`\n" +
                        "`123456789, 987654321, 456789123`\n\n" +
                        "*Eslatma:* Bir yoki ko'p Chat ID kiritishingiz mumkin.",
                new KeyboardBuilder()
                        .addButton("🔙 Orqaga", CALLBACK_MENU_BROADCAST)
                        .build());
    }

    /**
     * Handle broadcast user ID input
     */
    public void handleBroadcastUserIdInput(Long chatId, String text, Integer messageId) {
        botUtils.deleteMessage(botSupplier.get(), chatId, messageId);

        Long challengeId = getCurrentChallengeId(chatId);
        String informationText = messageTemplateService.getText(challengeId,
                com.muzaffar.telegram.enums.MessageKey.INFORMATION_MESSAGE);
        if (informationText == null || informationText.trim().isEmpty()) {
            botUtils.sendMessageWithKeyboard(botSupplier.get(), chatId,
                    "❌ *Xatolik:*\n\nInformation Message matni kiritilmagan. Iltimos, oldin \"Sozlamalar\" bo'limida matnni kiriting.",
                    navigationHandler.createBackButton());
            return;
        }

        String[] chatIdStrings = text.trim().split("\\s*,\\s*");
        List<Long> targetChatIds = new ArrayList<>();
        List<String> invalidIds = new ArrayList<>();

        for (String idStr : chatIdStrings) {
            try {
                Long targetChatId = Long.parseLong(idStr.trim());
                targetChatIds.add(targetChatId);
            } catch (NumberFormatException e) {
                invalidIds.add(idStr);
            }
        }

        if (!invalidIds.isEmpty()) {
            InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                    .addButton("🔙 Orqaga", CALLBACK_MENU_BROADCAST)
                    .build();
            botUtils.sendMessageWithKeyboard(botSupplier.get(), chatId,
                    String.format("❌ *Noto'g'ri format:*\n\n%s\n\nFaqat raqam kiriting. Qaytadan urinib ko'ring.",
                            String.join(", ", invalidIds)),
                    keyboard);
            return;
        }

        if (targetChatIds.isEmpty()) {
            InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                    .addButton("🔙 Orqaga", CALLBACK_MENU_BROADCAST)
                    .build();
            botUtils.sendMessageWithKeyboard(botSupplier.get(), chatId,
                    "❌ *Noto'g'ri format:*\n\nChat ID kiritilmadi. Qaytadan kiriting:", keyboard);
            return;
        }

        botUtils.sendMessage(botSupplier.get(), chatId,
                String.format("⏳ Xabar %d ta foydalanuvchiga yuborilmoqda...", targetChatIds.size()));

        int successCount = 0;
        int notFoundCount = 0;
        int failCount = 0;
        List<Long> successIds = new ArrayList<>();
        List<Long> notFoundIds = new ArrayList<>();
        List<Long> failedIds = new ArrayList<>();

        for (Long targetChatId : targetChatIds) {
            try {
                sendInformationMessageToUser(challengeId, targetChatId);
                successIds.add(targetChatId);
                successCount++;

                Thread.sleep(BROADCAST_DELAY_MS);

            } catch (Exception e) {
                log.error("Failed to send information message to chatId={}", targetChatId, e);
                failedIds.add(targetChatId);
                failCount++;
            }
        }

        StringBuilder resultText = new StringBuilder("✅ *Yuborish yakunlandi!*\n\n");
        resultText.append(String.format("Muvaffaqiyatli: *%d*\n", successCount));

        if (notFoundCount > 0) {
            resultText.append(String.format("Topilmadi: *%d*\n", notFoundCount));
        }
        if (failCount > 0) {
            resultText.append(String.format("Xatolik: *%d*\n", failCount));
        }

        resultText.append(String.format("Jami: *%d*\n\n", targetChatIds.size()));

        if (targetChatIds.size() == 1) {
            if (successCount == 1) {
                resultText.append(String.format("Chat ID: `%d`", successIds.get(0)));
            } else if (notFoundCount == 1) {
                resultText.append(String.format("❌ Chat ID topilmadi: `%d`", notFoundIds.get(0)));
            } else if (failCount == 1) {
                resultText.append(String.format("❌ Xatolik: `%d`", failedIds.get(0)));
            }
        } else {
            if (!notFoundIds.isEmpty() && notFoundIds.size() <= 5) {
                resultText.append("\n*Topilmaganlar:*\n");
                for (Long id : notFoundIds) {
                    resultText.append(String.format("`%d`\n", id));
                }
            }
            if (!failedIds.isEmpty() && failedIds.size() <= 5) {
                resultText.append("\n*Xatoliklar:*\n");
                for (Long id : failedIds) {
                    resultText.append(String.format("`%d`\n", id));
                }
            }
        }

        botUtils.sendMessageWithKeyboard(botSupplier.get(), chatId, resultText.toString(),
                navigationHandler.createBackButton());

        stateManager.clearState(chatId);
        stateManager.clearMenuMessageId(chatId);

        log.info("Broadcast to multiple users completed: success={}, notFound={}, failed={}, total={}",
                successCount, notFoundCount, failCount, targetChatIds.size());
    }

    /**
     * Send information message with button to a user
     */
    private void sendInformationMessageToUser(Long challengeId, Long targetChatId) throws TelegramApiException {
        ReferralTelegramBot referralBot = referralBotSupplier.get();
        if (referralBot == null) {
            throw new IllegalStateException("Referral bot not set. Cannot send messages to users.");
        }

        referralBot.setUserChallengeContext(targetChatId, challengeId);
        messageService.sendInformationMessageWithImage(referralBot, challengeId, targetChatId);
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
}
