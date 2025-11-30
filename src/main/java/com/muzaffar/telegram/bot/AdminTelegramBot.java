package com.muzaffar.telegram.bot;

import com.muzaffar.telegram.config.AppConfig;
import com.muzaffar.telegram.config.BotConfig;
import com.muzaffar.telegram.entity.Channel;
import com.muzaffar.telegram.service.BotSettingsService;
import com.muzaffar.telegram.service.ChannelService;
import com.muzaffar.telegram.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AdminTelegramBot extends TelegramLongPollingBot {
    
    private final BotConfig botConfig;
    private final AppConfig appConfig;
    private final ChannelService channelService;
    private final BotSettingsService settingsService;
    private final UserService userService;
    
    private final Map<Long, String> userStates = new HashMap<>();
    private final Map<Long, Map<String, String>> userData = new HashMap<>();
    
    public AdminTelegramBot(BotConfig botConfig, AppConfig appConfig,
                           ChannelService channelService, BotSettingsService settingsService,
                           UserService userService) {
        super(botConfig.getAdminBot().getToken());
        this.botConfig = botConfig;
        this.appConfig = appConfig;
        this.channelService = channelService;
        this.settingsService = settingsService;
        this.userService = userService;
    }
    
    @Override
    public String getBotUsername() {
        return botConfig.getAdminBot().getUsername();
    }
    
    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                Message message = update.getMessage();
                Long chatId = message.getChatId();
                
                if (!isAdmin(chatId)) {
                    sendMessage(chatId, "❌ Sizda ushbu botdan foydalanish huquqi yo'q.");
                    return;
                }
                
                String text = message.getText();
                log.info("Admin message from chatId={}: {}", chatId, text);
                
                if (text.equals("/start") || text.equals("/menu")) {
                    showMainMenu(chatId);
                } else {
                    handleTextInput(chatId, text, message.getMessageId());
                }
            } else if (update.hasCallbackQuery()) {
                CallbackQuery callbackQuery = update.getCallbackQuery();
                Long chatId = callbackQuery.getMessage().getChatId();
                
                if (!isAdmin(chatId)) {
                    return;
                }
                
                handleCallbackQuery(callbackQuery);
            }
        } catch (Exception e) {
            log.error("Error processing update", e);
            if (update.hasMessage()) {
                sendMessage(update.getMessage().getChatId(), "❌ Xatolik yuz berdi. Iltimos qaytadan urinib ko'ring.");
            }
        }
    }
    
    /**
     * Show main menu with buttons
     */
    private void showMainMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("👨‍💼 *Admin Panel*\n\nBoshqaruv panelidan foydalaning:");
        message.setParseMode("Markdown");
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Channel Management button
        InlineKeyboardButton channelBtn = new InlineKeyboardButton();
        channelBtn.setText("📺 Kanallarni boshqarish");
        channelBtn.setCallbackData("menu_channels");
        
        // Settings button
        InlineKeyboardButton settingsBtn = new InlineKeyboardButton();
        settingsBtn.setText("⚙️ Sozlamalar");
        settingsBtn.setCallbackData("menu_settings");
        
        rows.add(List.of(channelBtn));
        rows.add(List.of(settingsBtn));
        
        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending main menu", e);
        }
    }
    
    /**
     * Show channel management menu
     */
    private void showChannelMenu(Long chatId, Integer messageId) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText("📺 *Kanallarni boshqarish*\n\nKerakli amalni tanlang:");
        message.setParseMode("Markdown");
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Add channel button
        InlineKeyboardButton addBtn = new InlineKeyboardButton();
        addBtn.setText("➕ Kanal qo'shish");
        addBtn.setCallbackData("channel_add");
        
        // List channels button
        InlineKeyboardButton listBtn = new InlineKeyboardButton();
        listBtn.setText("📋 Kanallar ro'yxati");
        listBtn.setCallbackData("channel_list");
        
        // Remove channel button
        InlineKeyboardButton removeBtn = new InlineKeyboardButton();
        removeBtn.setText("🗑 Kanalni o'chirish");
        removeBtn.setCallbackData("channel_remove");
        
        // Back button
        InlineKeyboardButton backBtn = new InlineKeyboardButton();
        backBtn.setText("« Orqaga");
        backBtn.setCallbackData("menu_main");
        
        rows.add(List.of(addBtn));
        rows.add(List.of(listBtn));
        rows.add(List.of(removeBtn));
        rows.add(List.of(backBtn));
        
        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error showing channel menu", e);
        }
    }
    
    /**
     * Show settings menu
     */
    private void showSettingsMenu(Long chatId, Integer messageId) {
        int currentCount = settingsService.getRequiredReferralCount();
        
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(String.format("⚙️ *Sozlamalar*\n\nHozirgi referallar soni: *%d*\n\nKerakli amalni tanlang:", currentCount));
        message.setParseMode("Markdown");
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Set referrals button
        InlineKeyboardButton setBtn = new InlineKeyboardButton();
        setBtn.setText("🔢 Referallar sonini o'zgartirish");
        setBtn.setCallbackData("settings_set_referrals");
        
        // Back button
        InlineKeyboardButton backBtn = new InlineKeyboardButton();
        backBtn.setText("« Orqaga");
        backBtn.setCallbackData("menu_main");
        
        rows.add(List.of(setBtn));
        rows.add(List.of(backBtn));
        
        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error showing settings menu", e);
        }
    }
    
    /**
     * Handle callback queries from button presses
     */
    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String data = callbackQuery.getData();
        
        log.info("Admin callback from chatId={}: {}", chatId, data);
        
        switch (data) {
            case "menu_main":
            case "main_menu":
                editToMainMenu(chatId, messageId);
                break;
            case "menu_channels":
                showChannelMenu(chatId, messageId);
                break;
            case "menu_settings":
                showSettingsMenu(chatId, messageId);
                break;
            case "channel_add":
                startAddChannel(chatId, messageId);
                break;
            case "channel_list":
                showChannelList(chatId, messageId);
                break;
            case "channel_remove":
                startRemoveChannel(chatId, messageId);
                break;
            case "settings_set_referrals":
                startSetReferrals(chatId, messageId);
                break;
            case "cancel_operation":
                handleCancelOperation(chatId, messageId);
                break;
            default:
                if (data.startsWith("remove_channel_")) {
                    String channelId = data.substring(15);
                    handleRemoveChannelConfirm(chatId, messageId, channelId);
                }
                break;
        }
    }
    
    /**
     * Handle cancel operation - clear state and return to main menu
     */
    private void handleCancelOperation(Long chatId, Integer messageId) {
        // Clear any active states
        userStates.remove(chatId);
        userData.remove(chatId);
        
        log.info("User {} cancelled operation, returning to main menu", chatId);
        
        // Return to main menu
        editToMainMenu(chatId, messageId);
    }
    
    /**
     * Edit message to show main menu
     */
    private void editToMainMenu(Long chatId, Integer messageId) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText("👨‍💼 *Admin Panel*\n\nBoshqaruv panelidan foydalaning:");
        message.setParseMode("Markdown");
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        InlineKeyboardButton channelBtn = new InlineKeyboardButton();
        channelBtn.setText("📺 Kanallarni boshqarish");
        channelBtn.setCallbackData("menu_channels");
        
        InlineKeyboardButton settingsBtn = new InlineKeyboardButton();
        settingsBtn.setText("⚙️ Sozlamalar");
        settingsBtn.setCallbackData("menu_settings");
        
        rows.add(List.of(channelBtn));
        rows.add(List.of(settingsBtn));
        
        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error editing to main menu", e);
        }
    }
    
    /**
     * Start add channel flow
     */
    private void startAddChannel(Long chatId, Integer messageId) {
        userStates.put(chatId, "awaiting_channel_id");
        userData.put(chatId, new HashMap<>());
        
        editMessage(chatId, messageId, "➕ *Kanal qo'shish*\n\nKanal ID sini yuboring:\n\n_Masalan: -1001234567890_", createCancelButton());
    }
    
    /**
     * Show channel list
     */
    private void showChannelList(Long chatId, Integer messageId) {
        List<Channel> channels = channelService.getAllChannels();
        
        if (channels.isEmpty()) {
            editMessage(chatId, messageId, "📋 *Kanallar ro'yxati*\n\nHali kanallar qo'shilmagan.", createBackToMenuButton());
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("📋 *Kanallar ro'yxati:*\n\n");
        
        for (Channel channel : channels) {
            sb.append(String.format(
                "%s *%s*\n" +
                "ID: `%s`\n" +
                "Username: @%s\n\n",
                channel.getIsActive() ? "✅" : "❌",
                channel.getChannelName(),
                channel.getChannelId(),
                channel.getChannelUsername()
            ));
        }
        
        editMessage(chatId, messageId, sb.toString(), createBackToMenuButton());
    }
    
    /**
     * Start remove channel flow
     */
    private void startRemoveChannel(Long chatId, Integer messageId) {
        List<Channel> channels = channelService.getAllActiveChannels();
        
        if (channels.isEmpty()) {
            editMessage(chatId, messageId, "🗑 *Kanalni o'chirish*\n\nO'chirish uchun aktiv kanallar yo'q.", createBackToMenuButton());
            return;
        }
        
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText("🗑 *Kanalni o'chirish*\n\nO'chirish uchun kanalni tanlang:");
        message.setParseMode("Markdown");
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        for (Channel channel : channels) {
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(channel.getChannelName());
            btn.setCallbackData("remove_channel_" + channel.getChannelId());
            rows.add(List.of(btn));
        }
        
        // Back button
        InlineKeyboardButton backBtn = new InlineKeyboardButton();
        backBtn.setText("« Orqaga");
        backBtn.setCallbackData("menu_channels");
        rows.add(List.of(backBtn));
        
        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error showing remove channel menu", e);
        }
    }
    
    /**
     * Handle remove channel confirmation
     */
    private void handleRemoveChannelConfirm(Long chatId, Integer messageId, String channelId) {
        boolean removed = channelService.removeChannel(channelId);
        
        if (removed) {
            editMessage(chatId, messageId, "✅ *Kanal o'chirildi!*", createBackToMenuButton());
        } else {
            editMessage(chatId, messageId, "❌ *Kanal topilmadi*", createBackToMenuButton());
        }
    }
    
    /**
     * Start set referrals flow
     */
    private void startSetReferrals(Long chatId, Integer messageId) {
        userStates.put(chatId, "awaiting_referral_count");
        
        int currentCount = settingsService.getRequiredReferralCount();
        editMessage(chatId, messageId, 
            String.format("🔢 *Referallar sonini o'zgartirish*\n\nHozirgi qiymat: *%d*\n\nYangi qiymatni yuboring (1 dan katta son):", currentCount), createCancelButton());
    }
    
    /**
     * Handle text input based on current state
     */
    private void handleTextInput(Long chatId, String text, Integer messageId) {
        String state = userStates.get(chatId);
        
        if (state == null) {
            // Silently ignore text when no active state (user should use buttons)
            log.debug("Ignoring text input from chatId={} (no active state): {}", chatId, text);
            // Delete the unnecessary message
            deleteMessage(chatId, messageId);
            return;
        }
        
        switch (state) {
            case "awaiting_channel_id":
                handleChannelIdInput(chatId, text, messageId);
                break;
            case "awaiting_channel_name":
                handleChannelNameInput(chatId, text, messageId);
                break;
            case "awaiting_channel_username":
                handleChannelUsernameInput(chatId, text, messageId);
                break;
            case "awaiting_referral_count":
                handleReferralCountInput(chatId, text, messageId);
                break;
            default:
                // Silently ignore unknown states
                log.warn("Unknown state for chatId={}: {}", chatId, state);
                userStates.remove(chatId);
                userData.remove(chatId);
                break;
        }
    }
    
    /**
     * Handle channel ID input
     */
    private void handleChannelIdInput(Long chatId, String channelId, Integer messageId) {
        // Delete the user's message to keep chat clean
        deleteMessage(chatId, messageId);
        Map<String, String> data = userData.get(chatId);
        data.put("channelId", channelId);
        
        userStates.put(chatId, "awaiting_channel_name");
        sendMessage(chatId, "✅ Kanal ID qabul qilindi.\n\n📝 Endi kanal nomini yuboring:\n\n_Masalan: Teacher Muzaffar Math_", createCancelButton());
    }
    
    /**
     * Handle channel name input
     */
    private void handleChannelNameInput(Long chatId, String channelName, Integer messageId) {
        // Delete the user's message to keep chat clean
        deleteMessage(chatId, messageId);
        Map<String, String> data = userData.get(chatId);
        data.put("channelName", channelName);
        
        userStates.put(chatId, "awaiting_channel_username");
        sendMessage(chatId, "✅ Kanal nomi qabul qilindi.\n\n👤 Endi kanal username ini yuboring (@ belgisisiz):\n\n_Masalan: TeacherMuzaffar_", createCancelButton());
    }
    
    /**
     * Handle channel username input
     */
    private void handleChannelUsernameInput(Long chatId, String channelUsername, Integer messageId) {
        // Delete the user's message to keep chat clean
        deleteMessage(chatId, messageId);
        Map<String, String> data = userData.get(chatId);
        
        // Remove @ if present
        if (channelUsername.startsWith("@")) {
            channelUsername = channelUsername.substring(1);
        }
        
        String channelId = data.get("channelId");
        String channelName = data.get("channelName");
        
        try {
            Channel channel = channelService.addChannel(channelId, channelName, channelUsername);
            sendMessage(chatId, String.format(
                "✅ *Kanal muvaffaqiyatli qo'shildi!*\n\n" +
                "📺 *Nomi:* %s\n" +
                "🆔 *ID:* `%s`\n" +
                "👤 *Username:* @%s",
                channel.getChannelName(), channel.getChannelId(), channel.getChannelUsername()
            ), createBackToMenuButton());
        } catch (IllegalArgumentException e) {
            sendMessage(chatId, "❌ *Xatolik:* " + e.getMessage(), createBackToMenuButton());
        } finally {
            // Clear state
            userStates.remove(chatId);
            userData.remove(chatId);
        }
    }
    
    /**
     * Handle referral count input
     */
    private void handleReferralCountInput(Long chatId, String text, Integer messageId) {
        // Delete the user's message to keep chat clean
        deleteMessage(chatId, messageId);
        
        try {
            int count = Integer.parseInt(text.trim());
            
            if (count < 1) {
                sendMessage(chatId, "❌ Referallar soni kamida 1 bo'lishi kerak.\n\nQaytadan kiriting:");
                return;
            }
            
            settingsService.setRequiredReferralCount(count);
            sendMessage(chatId, String.format("✅ *Referallar soni o'zgartirildi!*\n\nYangi qiymat: *%d*", count), createBackToMenuButton());
            
            // Clear state
            userStates.remove(chatId);
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Noto'g'ri format. Faqat son kiriting.\n\nQaytadan kiriting:");
        }
    }
    
    /**
     * Check if user is admin
     */
    private boolean isAdmin(Long chatId) {
        return appConfig.getAdmin().getChatIds().contains(chatId);
    }
    
    /**
     * Delete a message
     */
    private void deleteMessage(Long chatId, Integer messageId) {
        try {
            DeleteMessage deleteMessage = new DeleteMessage();
            deleteMessage.setChatId(chatId.toString());
            deleteMessage.setMessageId(messageId);
            execute(deleteMessage);
            log.debug("Deleted message {} from chatId={}", messageId, chatId);
        } catch (TelegramApiException e) {
            log.debug("Could not delete message {} from chatId={}: {}", messageId, chatId, e.getMessage());
        }
    }
    
    /**
     * Create inline keyboard with back to menu button
     */
    private InlineKeyboardMarkup createBackToMenuButton() {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        InlineKeyboardButton menuBtn = new InlineKeyboardButton();
        menuBtn.setText("📋 Menyu");
        menuBtn.setCallbackData("main_menu");
        
        rows.add(List.of(menuBtn));
        keyboard.setKeyboard(rows);
        
        return keyboard;
    }
    
    /**
     * Create inline keyboard with cancel button
     */
    private InlineKeyboardMarkup createCancelButton() {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton();
        cancelBtn.setText("❌ Bekor qilish");
        cancelBtn.setCallbackData("cancel_operation");
        
        rows.add(List.of(cancelBtn));
        keyboard.setKeyboard(rows);
        
        return keyboard;
    }
    
    /**
     * Send message
     */
    private void sendMessage(Long chatId, String text) {
        sendMessage(chatId, text, null);
    }
    
    /**
     * Send message with keyboard
     */
    private void sendMessage(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text);
            message.setParseMode("Markdown");
            if (keyboard != null) {
                message.setReplyMarkup(keyboard);
            }
            execute(message);
            log.info("Sent admin message to chatId={}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending admin message to chatId=" + chatId, e);
        }
    }
    
    /**
     * Edit message
     */
    private void editMessage(Long chatId, Integer messageId, String text) {
        editMessage(chatId, messageId, text, null);
    }
    
    /**
     * Edit message with keyboard
     */
    private void editMessage(Long chatId, Integer messageId, String text, InlineKeyboardMarkup keyboard) {
        try {
            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(messageId);
            message.setText(text);
            message.setParseMode("Markdown");
            if (keyboard != null) {
                message.setReplyMarkup(keyboard);
            }
            execute(message);
            log.info("Edited admin message for chatId={}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error editing admin message for chatId=" + chatId, e);
        }
    }
}
