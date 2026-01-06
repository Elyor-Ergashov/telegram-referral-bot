package com.muzaffar.telegram.bot.handler.admin;

import static com.muzaffar.telegram.constants.BotConstants.*;

import com.muzaffar.telegram.entity.ChallengeChannel;
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
 * Handler for channel management operations.
 * Add, list, and remove channels from challenges.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelManagementHandler {

    private final ChallengeService challengeService;
    private final AdminStateManager stateManager;
    private final AdminNavigationHandler navigationHandler;
    private final TelegramBotUtils botUtils;

    private Supplier<TelegramLongPollingBot> botSupplier;

    public void init(Supplier<TelegramLongPollingBot> botSupplier) {
        this.botSupplier = botSupplier;
    }

    /**
     * Start add channel flow
     */
    public void startAddChannel(Long chatId, Integer messageId) {
        stateManager.setState(chatId, STATE_AWAITING_CHANNEL_ID);
        stateManager.initUserData(chatId);

        InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                .addButton("🔙 Orqaga", CALLBACK_MENU_CHANNELS)
                .build();
        editMessage(chatId, messageId, "➕ *Kanal qo'shish*\n\nKanal ID sini yuboring:\n\n*Masalan:* `-1001234567890`",
                keyboard);
    }

    /**
     * Show channel list (for current challenge)
     */
    public void showChannelList(Long chatId, Integer messageId) {
        Long challengeId = stateManager.getCurrentChallengeId(chatId);
        if (challengeId == null) {
            sendMessage(chatId, "❌ Challenge tanlanmagan.");
            return;
        }

        List<ChallengeChannel> channels = challengeService.getChannelsForChallenge(challengeId);

        InlineKeyboardMarkup backButton = new KeyboardBuilder()
                .addButton("🔙 Orqaga", CALLBACK_MENU_CHANNELS)
                .build();

        if (channels.isEmpty()) {
            editMessage(chatId, messageId, "📋 *Kanallar ro'yxati*\n\nHali kanallar qo'shilmagan.", backButton);
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📋 *Kanallar ro'yxati:*\n\n");

        for (ChallengeChannel channel : channels) {
            sb.append(String.format(
                    "%s *%s*\n" +
                            "ID: `%s`\n" +
                            "Username: `@%s`\n\n",
                    channel.getIsActive() ? "✅" : "❌",
                    channel.getChannelName(),
                    channel.getChannelId(),
                    channel.getChannelUsername()));
        }

        editMessage(chatId, messageId, sb.toString(), backButton);
    }

    /**
     * Start remove channel flow (for current challenge)
     */
    public void startRemoveChannel(Long chatId, Integer messageId) {
        Long challengeId = stateManager.getCurrentChallengeId(chatId);
        if (challengeId == null) {
            sendMessage(chatId, "❌ Challenge tanlanmagan.");
            return;
        }

        List<ChallengeChannel> channels = challengeService.getActiveChannelsForChallenge(challengeId);

        if (channels.isEmpty()) {
            InlineKeyboardMarkup backButton = new KeyboardBuilder()
                    .addButton("🔙 Orqaga", CALLBACK_MENU_CHANNELS)
                    .build();
            editMessage(chatId, messageId, "🗑 *Kanalni o'chirish*\n\nO'chirish uchun aktiv kanallar yo'q.",
                    backButton);
            return;
        }

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText("🗑 *Kanalni o'chirish*\n\nO'chirish uchun kanalni tanlang:");
        message.setParseMode("Markdown");

        KeyboardBuilder builder = new KeyboardBuilder();
        for (ChallengeChannel channel : channels) {
            builder.addRow(channel.getChannelName(), "remove_channel_" + channel.getChannelId());
        }

        InlineKeyboardMarkup keyboard = builder
                .addRow("🔙 Orqaga", CALLBACK_MENU_CHANNELS)
                .build();
        message.setReplyMarkup(keyboard);

        try {
            botSupplier.get().execute(message);
        } catch (TelegramApiException e) {
            log.error("Error showing remove channel menu", e);
        }
    }

    /**
     * Handle remove channel confirmation
     */
    public void handleRemoveChannelConfirm(Long chatId, Integer messageId, String channelId) {
        Long challengeId = stateManager.getCurrentChallengeId(chatId);
        if (challengeId == null) {
            editMessage(chatId, messageId, "❌ *Challenge tanlanmagan*", navigationHandler.createBackButton());
            return;
        }

        boolean deleted = challengeService.removeChannelFromChallenge(challengeId, channelId);

        if (deleted) {
            editMessage(chatId, messageId, "✅ *Kanal muvaffaqiyatli o'chirildi!*",
                    navigationHandler.createBackButton());
        } else {
            editMessage(chatId, messageId, "❌ *Kanal topilmadi*", navigationHandler.createBackButton());
        }
    }

    /**
     * Handle channel ID input
     */
    public void handleChannelIdInput(Long chatId, String channelId, Integer messageId) {
        botUtils.deleteMessage(botSupplier.get(), chatId, messageId);
        stateManager.setUserData(chatId, "channelId", channelId);

        stateManager.setState(chatId, STATE_AWAITING_CHANNEL_NAME);
        InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                .addButton("🔙 Orqaga", CALLBACK_MENU_CHANNELS)
                .build();
        sendMessage(chatId,
                "✅ Kanal ID qabul qilindi.\n\n📝 Endi kanal nomini yuboring:\n\n*Masalan:* `Teacher Muzaffar Math`",
                keyboard);
    }

    /**
     * Handle channel name input
     */
    public void handleChannelNameInput(Long chatId, String channelName, Integer messageId) {
        botUtils.deleteMessage(botSupplier.get(), chatId, messageId);
        stateManager.setUserData(chatId, "channelName", channelName);

        stateManager.setState(chatId, STATE_AWAITING_CHANNEL_USERNAME);
        InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                .addButton("🔙 Orqaga", CALLBACK_MENU_CHANNELS)
                .build();
        sendMessage(chatId,
                "✅ Kanal nomi qabul qilindi.\n\n👤 Endi kanal username ini yuboring (@ belgisisiz):\n\n*Masalan:* `TeacherMuzaffar`",
                keyboard);
    }

    /**
     * Handle channel username input
     */
    public void handleChannelUsernameInput(Long chatId, String channelUsername, Integer messageId) {
        botUtils.deleteMessage(botSupplier.get(), chatId, messageId);

        if (channelUsername.startsWith("@")) {
            channelUsername = channelUsername.substring(1);
        }

        Long challengeId = stateManager.getCurrentChallengeId(chatId);
        if (challengeId == null) {
            sendMessage(chatId, "❌ Challenge tanlanmagan.", navigationHandler.createBackButton());
            stateManager.clearState(chatId);
            stateManager.clearUserData(chatId);
            return;
        }

        Map<String, String> data = stateManager.getAllUserData(chatId);
        String channelId = data.get("channelId");
        String channelName = data.get("channelName");

        try {
            ChallengeChannel channel = challengeService.addChannelToChallenge(
                    challengeId, channelId, channelName, channelUsername);
            sendMessage(chatId, String.format(
                    "✅ *Kanal muvaffaqiyatli qo'shildi!*\n\n" +
                            "📺 *Nomi:* %s\n" +
                            "🆔 *ID:* `%s`\n" +
                            "👤 *Username:* `@%s`",
                    channel.getChannelName(), channel.getChannelId(), channel.getChannelUsername()),
                    navigationHandler.createBackButton());
        } catch (IllegalArgumentException e) {
            sendMessage(chatId, "❌ *Xatolik:*\n" + e.getMessage(), navigationHandler.createBackButton());
        } finally {
            stateManager.clearState(chatId);
            stateManager.clearUserData(chatId);
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
