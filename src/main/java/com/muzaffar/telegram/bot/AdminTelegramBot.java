package com.muzaffar.telegram.bot;

import com.muzaffar.telegram.config.AppConfig;
import com.muzaffar.telegram.config.BotConfig;
import com.muzaffar.telegram.entity.Channel;
import com.muzaffar.telegram.entity.User;
import com.muzaffar.telegram.service.BotSettingsService;
import com.muzaffar.telegram.service.ChannelService;
import com.muzaffar.telegram.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Slf4j
@Component
public class AdminTelegramBot extends TelegramLongPollingBot {
    
    private final BotConfig botConfig;
    private final AppConfig appConfig;
    private final ChannelService channelService;
    private final BotSettingsService settingsService;
    private final UserService userService;
    
    private ReferralTelegramBot referralBot;
    
    private final Map<Long, String> userStates = new HashMap<>();
    private final Map<Long, Map<String, String>> userData = new HashMap<>();
    
    private static final String IMAGES_DIRECTORY = "bot_images/stats_headers/";
    
    public AdminTelegramBot(BotConfig botConfig, AppConfig appConfig,
                           ChannelService channelService, BotSettingsService settingsService,
                           UserService userService) {
        super(botConfig.getAdminBot().getToken());
        this.botConfig = botConfig;
        this.appConfig = appConfig;
        this.channelService = channelService;
        this.settingsService = settingsService;
        this.userService = userService;
        
        // Create images directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(IMAGES_DIRECTORY));
            log.info("Stats header images directory created/verified: {}", IMAGES_DIRECTORY);
        } catch (IOException e) {
            log.error("Failed to create images directory", e);
        }
    }
    
    /**
     * Set referral bot instance for sending messages to users
     */
    public void setReferralBot(ReferralTelegramBot referralBot) {
        this.referralBot = referralBot;
    }
    
    @Override
    public String getBotUsername() {
        return botConfig.getAdminBot().getUsername();
    }
    
    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage()) {
                Message message = update.getMessage();
                Long chatId = message.getChatId();
                
                if (!isAdmin(chatId)) {
                    sendMessage(chatId, "❌ Sizda ushbu botdan foydalanish huquqi yo'q.");
                    return;
                }
                
                if (message.hasText()) {
                    String text = message.getText();
                    log.info("Admin message from chatId={}: {}", chatId, text);
                    
                    if (text.equals("/start") || text.equals("/menu")) {
                        showMainMenu(chatId);
                    } else {
                        handleTextInput(chatId, text, message.getMessageId());
                    }
                } else if (message.hasPhoto()) {
                    // Handle compressed photo upload
                    handlePhotoUpload(chatId, message);
                } else if (message.hasDocument()) {
                    // Handle uncompressed file upload (sent as document)
                    handleDocumentUpload(chatId, message);
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
        
        // Broadcast button
        InlineKeyboardButton broadcastBtn = new InlineKeyboardButton();
        broadcastBtn.setText("📢 Xabar yuborish");
        broadcastBtn.setCallbackData("menu_broadcast");
        
        rows.add(List.of(channelBtn));
        rows.add(List.of(settingsBtn));
        rows.add(List.of(broadcastBtn));
        
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
        String currentLink = settingsService.getPrivateChannelLink().orElse("❌ O'rnatilmagan");
        
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(String.format(
            "⚙️ *Sozlamalar*\n\n" +
            "🔢 Hozirgi referallar soni: *%d*\n" +
            "🔗 Kerakli amalahni tanlang:" +
            "\n\nKerakli amalni tanlang:",
            currentCount
        ));
        message.setParseMode("Markdown");
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Set referrals button
        InlineKeyboardButton setRefBtn = new InlineKeyboardButton();
        setRefBtn.setText("🔢 Referallar sonini o'zgartirish");
        setRefBtn.setCallbackData("settings_set_referrals");
        
        // Set private channel link button
        InlineKeyboardButton setLinkBtn = new InlineKeyboardButton();
        setLinkBtn.setText("🔗 Shaxsiy kanal havolasini o'zgartirish");
        setLinkBtn.setCallbackData("settings_set_link");
        
        // Set welcome message button
        InlineKeyboardButton setWelcomeBtn = new InlineKeyboardButton();
        setWelcomeBtn.setText("📝 Welcome xabarini o'zgartirish");
        setWelcomeBtn.setCallbackData("settings_set_welcome");
        
        // Set stats header text button
        InlineKeyboardButton setStatsTextBtn = new InlineKeyboardButton();
        setStatsTextBtn.setText("📊 Statistika sarlavhasini o'zgartirish");
        setStatsTextBtn.setCallbackData("settings_set_stats_text");
        
        // Set stats header image button
        InlineKeyboardButton setStatsImageBtn = new InlineKeyboardButton();
        setStatsImageBtn.setText("🖼 Statistika rasmini o'zgartirish");
        setStatsImageBtn.setCallbackData("settings_set_stats_image");
        
        // Back button
        InlineKeyboardButton backBtn = new InlineKeyboardButton();
        backBtn.setText("« Orqaga");
        backBtn.setCallbackData("menu_main");
        
        rows.add(List.of(setRefBtn));
        rows.add(List.of(setLinkBtn));
        rows.add(List.of(setWelcomeBtn));
        rows.add(List.of(setStatsTextBtn));
        rows.add(List.of(setStatsImageBtn));
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
     * Show broadcast menu
     */
    private void showBroadcastMenu(Long chatId, Integer messageId) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(
            "📢 *Xabar yuborish*\n\n" +
            "Welcome xabarini foydalanuvchilarga yuborish:\n\n" +
            "Quyidagi variantlardan birini tanlang:"
        );
        message.setParseMode("Markdown");
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Send to all users button
        InlineKeyboardButton sendAllBtn = new InlineKeyboardButton();
        sendAllBtn.setText("📤 Barcha foydalanuvchilarga");
        sendAllBtn.setCallbackData("broadcast_welcome_all");
        
        // Send to multiple users button
        InlineKeyboardButton sendSingleBtn = new InlineKeyboardButton();
        sendSingleBtn.setText("� Tanlangan foydalanuvchilarga");
        sendSingleBtn.setCallbackData("broadcast_welcome_single");
        
        // Back button
        InlineKeyboardButton backBtn = new InlineKeyboardButton();
        backBtn.setText("« Orqaga");
        backBtn.setCallbackData("menu_main");
        
        rows.add(List.of(sendAllBtn));
        rows.add(List.of(sendSingleBtn));
        rows.add(List.of(backBtn));
        
        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error showing broadcast menu", e);
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
            case "menu_broadcast":
                showBroadcastMenu(chatId, messageId);
                break;
            case "broadcast_welcome_all":
                confirmBroadcastWelcomeAll(chatId, messageId);
                break;
            case "broadcast_welcome_all_confirm":
                executeBroadcastWelcomeAll(chatId, messageId);
                break;
            case "broadcast_welcome_single":
                startBroadcastWelcomeSingle(chatId, messageId);
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
            case "settings_set_link":
                startSetPrivateLink(chatId, messageId);
                break;
            case "settings_set_welcome":
                startSetWelcomeMessage(chatId, messageId);
                break;
            case "settings_set_stats_text":
                startSetStatsHeaderText(chatId, messageId);
                break;
            case "settings_set_stats_image":
                startSetStatsHeaderImage(chatId, messageId);
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
        
        InlineKeyboardButton broadcastBtn = new InlineKeyboardButton();
        broadcastBtn.setText("📢 Xabar yuborish");
        broadcastBtn.setCallbackData("menu_broadcast");
        
        rows.add(List.of(channelBtn));
        rows.add(List.of(settingsBtn));
        rows.add(List.of(broadcastBtn));
        
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
        
        editMessage(chatId, messageId, "➕ *Kanal qo'shish*\n\nKanal ID sini yuboring:\n\n*Masalan:* `-1001234567890`", createCancelButton());
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
                "Username: `@%s`\n\n",
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
     * Handle remove channel confirmation - permanently deletes channel
     */
    private void handleRemoveChannelConfirm(Long chatId, Integer messageId, String channelId) {
        boolean deleted = channelService.deleteChannel(channelId);
        
        if (deleted) {
            editMessage(chatId, messageId, "✅ *Kanal butunlay o'chirildi!*", createBackToMenuButton());
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
            case "awaiting_private_link":
                handlePrivateLinkInput(chatId, text, messageId);
                break;
            case "awaiting_welcome_message":
                handleWelcomeMessageInput(chatId, text, messageId);
                break;
            case "awaiting_stats_header_text":
                handleStatsHeaderTextInput(chatId, text, messageId);
                break;
            case "awaiting_stats_header_image":
                handleStatsHeaderImageInput(chatId, text, messageId);
                break;
            case "awaiting_broadcast_user_id":
                handleBroadcastUserIdInput(chatId, text, messageId);
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
        sendMessage(chatId, "✅ Kanal ID qabul qilindi.\n\n📝 Endi kanal nomini yuboring:\n\n*Masalan:* `Teacher Muzaffar Math`", createCancelButton());
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
        sendMessage(chatId, "✅ Kanal nomi qabul qilindi.\n\n👤 Endi kanal username ini yuboring (@ belgisisiz):\n\n*Masalan:* `TeacherMuzaffar`", createCancelButton());
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
                "👤 *Username:* `@%s`",
                channel.getChannelName(), channel.getChannelId(), channel.getChannelUsername()
            ), createBackToMenuButton());
        } catch (IllegalArgumentException e) {
            sendMessage(chatId, "❌ *Xatolik:*\n" + e.getMessage(), createBackToMenuButton());
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
     * Start set private channel link flow
     */
    private void startSetPrivateLink(Long chatId, Integer messageId) {
        userStates.put(chatId, "awaiting_private_link");
        
        String currentLink = settingsService.getPrivateChannelLink().orElse("O'rnatilmagan");
        editMessage(chatId, messageId, 
            String.format("🔗 *Shaxsiy kanal havolasini o'zgartirish*\n\nHozirgi havola: `%s`\n\nYangi havolani yuboring (Telegram invite link):\n\n*Masalan:* `https://t.me/+abc123` yoki `https://t.me/joinchat/abc123`", currentLink), 
            createCancelButton());
    }
    
    /**
     * Handle private channel link input
     */
    private void handlePrivateLinkInput(Long chatId, String text, Integer messageId) {
        // Delete the user's message to keep chat clean
        deleteMessage(chatId, messageId);
        
        String link = text.trim();
        
        // Basic validation - check if it looks like a Telegram link
        if (!link.startsWith("https://t.me/") && !link.startsWith("http://t.me/")) {
            sendMessage(chatId, "❌ Noto'g'ri havola formati.\n\nTelegram havolasi bo'lishi kerak (https://t.me/...).\n\nQaytadan kiriting:");
            return;
        }
        
        settingsService.setPrivateChannelLink(link);
        sendMessage(chatId, String.format("✅ *Shaxsiy kanal havolasi o'zgartirildi!*\n\nYangi havola: `%s`", link), createBackToMenuButton());
        
        // Clear state
        userStates.remove(chatId);
    }
    
    /**
     * Start set welcome message flow
     */
    private void startSetWelcomeMessage(Long chatId, Integer messageId) {
        userStates.put(chatId, "awaiting_welcome_message");
        
        String currentText = settingsService.getWelcomeMessageText();
        
        editMessage(chatId, messageId, 
            String.format("📝 *Welcome xabarini o'zgartirish*\n\nHozirgi xabar:\n```\n%s\n```\n\nAgar matnni o'zgartirmoqchi bo'lsayiz,yangi xabar matnini yuboring:\n\n*Eslatma:* Markdown formatidan foydalanishingiz mumkin (*bold*, _italic_, `code`)", currentText),
            createCancelButton());
    }
    
    /**
     * Handle welcome message input
     */
    private void handleWelcomeMessageInput(Long chatId, String text, Integer messageId) {
        deleteMessage(chatId, messageId);
        
        if (text.trim().isEmpty()) {
            sendMessage(chatId, "❌ Xabar bo'sh bo'lishi mumkin emas.\n\nQaytadan kiriting:");
            return;
        }
        
        settingsService.setWelcomeMessageText(text);
        sendMessage(chatId, "✅ *Xush kelibsiz xabari o'zgartirildi!*\n\nYangi foydalanuvchilar ushbu xabarni ko'rishadi.", createBackToMenuButton());
        
        userStates.remove(chatId);
    }
    
    /**
     * Start set stats header text flow
     */
    private void startSetStatsHeaderText(Long chatId, Integer messageId) {
        userStates.put(chatId, "awaiting_stats_header_text");
        
        String currentText = settingsService.getStatsHeaderText().orElse("O'rnatilmagan");
        
        editMessage(chatId, messageId, 
            String.format("📊 *Statistika sarlavhasini o'zgartirish*\n\nHozirgi sarlavha: ```\n%s\n```\n\nAgar matnni o'zgartirmoqchi bo'lsayiz,yangi xabar matnini yuboring:\n\n*Eslatma:* Bu matn statistika xabarining boshida ko'rsatiladi.", currentText),
            createCancelButton());
    }
    
    /**
     * Handle stats header text input
     */
    private void handleStatsHeaderTextInput(Long chatId, String text, Integer messageId) {
        deleteMessage(chatId, messageId);
        
        String trimmedText = text.trim();
        
        if (trimmedText.equalsIgnoreCase("o'chirish") || trimmedText.equalsIgnoreCase("delete")) {
            settingsService.setStatsHeaderText("");
            sendMessage(chatId, "✅ *Statistika sarlavhasi o'chirildi!*", createBackToMenuButton());
        } else {
            settingsService.setStatsHeaderText(trimmedText);
            sendMessage(chatId, "✅ *Statistika sarlavhasi o'zgartirildi!*", createBackToMenuButton());
        }
        
        userStates.remove(chatId);
    }
    
    /**
     * Start set stats header image flow
     */
    private void startSetStatsHeaderImage(Long chatId, Integer messageId) {
        userStates.put(chatId, "awaiting_stats_header_image");
        
        // Delete the menu message first
        deleteMessage(chatId, messageId);
        
        // Send current image if it exists
        settingsService.getStatsHeaderImagePath().ifPresentOrElse(
            imagePath -> {
                try {
                    // Send current image
                    sendPhotoFromFile(chatId, imagePath, "🖼 *Hozirgi rasm:*");
                } catch (Exception e) {
                    log.error("Error sending current stats header image", e);
                    sendMessage(chatId, "⚠️ Hozirgi rasmni yuklashda xatolik.\n\nYangi rasm yuboring:");
                }
            },
            () -> {
                // No current image
                sendMessage(chatId, "🖼 *Statistika rasmini o'zgartirish*\n\nHozirda rasm o'rnatilmagan.");
            }
        );
        
        // Send instruction message
        sendMessage(chatId, 
            "🖼 *Statistika rasmini o'zgartirish*\n\nAgar rasmni o'zgartirmoqchi bo'lsayiz,yangi rasmni yuboring:\n\n*Eslatma:* Bu rasm statistika xabarining yuqorisida yuboriladi.",
            createCancelButton());
    }
    
    /**
     * Handle stats header image text input (for delete command)
     */
    private void handleStatsHeaderImageInput(Long chatId, String text, Integer messageId) {
        deleteMessage(chatId, messageId);
        
        String trimmedText = text.trim();
        
        if (trimmedText.equalsIgnoreCase("o'chirish") || trimmedText.equalsIgnoreCase("delete")) {
            // Delete old image file if exists
            settingsService.getStatsHeaderImagePath().ifPresent(oldPath -> {
                try {
                    Files.deleteIfExists(Paths.get(oldPath));
                    log.info("Deleted old stats header image: {}", oldPath);
                } catch (IOException e) {
                    log.error("Failed to delete old image file", e);
                }
            });
            
            settingsService.setStatsHeaderImagePath("");
            sendMessage(chatId, "✅ *Statistika rasmi o'chirildi!*", createBackToMenuButton());
            userStates.remove(chatId);
        } else {
            sendMessage(chatId, "❌ Iltimos, rasm faylini yuboring yoki \"o'chirish\" deb yozing.");
        }
    }
    
    /**
     * Confirm broadcast welcome message to all users
     */
    private void confirmBroadcastWelcomeAll(Long chatId, Integer messageId) {
        long totalUsers = userService.getTotalUserCount();
        
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(
            String.format("📢 *Barcha foydalanuvchilarga yuborish*\n\n" +
            "Welcome xabari *%d* ta foydalanuvchiga yuboriladi.\n\n" +
            "Davom etishni xohlaysizmi?", totalUsers)
        );
        message.setParseMode("Markdown");
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Confirm button
        InlineKeyboardButton confirmBtn = new InlineKeyboardButton();
        confirmBtn.setText("✅ Ha, yuborish");
        confirmBtn.setCallbackData("broadcast_welcome_all_confirm");
        
        // Cancel button
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton();
        cancelBtn.setText("❌ Bekor qilish");
        cancelBtn.setCallbackData("menu_broadcast");
        
        rows.add(List.of(confirmBtn));
        rows.add(List.of(cancelBtn));
        
        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error showing broadcast confirmation", e);
        }
    }
    
    /**
     * Execute broadcast welcome message to all users
     */
    private void executeBroadcastWelcomeAll(Long chatId, Integer messageId) {
        // Delete confirmation message
        deleteMessage(chatId, messageId);
        
        // Send processing message
        sendMessage(chatId, "⏳ Xabarlar yuborilmoqda...");
        
        // Get all users
        List<com.muzaffar.telegram.entity.User> users = userService.getAllUsers();
        
        int successCount = 0;
        int failCount = 0;
        
        for (com.muzaffar.telegram.entity.User user : users) {
            try {
                // Send welcome message with button
                sendWelcomeMessageToUser(user.getChatId());
                successCount++;
                
                // Small delay to avoid rate limits
                Thread.sleep(50);
            } catch (Exception e) {
                log.error("Failed to send welcome message to chatId={}", user.getChatId(), e);
                failCount++;
            }
        }
        
        // Send result
        String resultText = String.format(
            "✅ *Yuborish yakunlandi!*\n\n" +
            "Muvaffaqiyatli: *%d*\n" +
            "Xatolik: *%d*\n" +
            "Jami: *%d*",
            successCount, failCount, users.size()
        );
        
        sendMessage(chatId, resultText, createBackToMenuButton());
        
        log.info("Broadcast completed: success={}, failed={}, total={}", successCount, failCount, users.size());
    }
    
    /**
     * Start broadcast welcome to multiple users
     */
    private void startBroadcastWelcomeSingle(Long chatId, Integer messageId) {
        userStates.put(chatId, "awaiting_broadcast_user_id");
        
        editMessage(chatId, messageId,
            "� *Tanlangan foydalanuvchilarga yuborish*\n\n" +
            "Foydalanuvchilar Chat ID sini vergul bilan ajratib yuboring:\n\n" +
            "*Misol:*\n" +
            "`123456789`\n" +
            "`123456789, 987654321, 456789123`\n\n" +
            "*Eslatma:* Bir yoki ko'p Chat ID kiritishingiz mumkin.",
            createCancelButton());
    }
    
    /**
     * Handle broadcast user ID input (supports comma-separated IDs)
     */
    private void handleBroadcastUserIdInput(Long chatId, String text, Integer messageId) {
        deleteMessage(chatId, messageId);
        
        // Parse comma-separated Chat IDs
        String[] chatIdStrings = text.trim().split("\\s*,\\s*");
        List<Long> targetChatIds = new ArrayList<>();
        List<String> invalidIds = new ArrayList<>();
        
        // Validate each Chat ID
        for (String idStr : chatIdStrings) {
            try {
                Long targetChatId = Long.parseLong(idStr.trim());
                targetChatIds.add(targetChatId);
            } catch (NumberFormatException e) {
                invalidIds.add(idStr);
            }
        }
        
        // If there are invalid IDs, show error
        if (!invalidIds.isEmpty()) {
            sendMessage(chatId, 
                String.format("❌ *Noto'g'ri format:*\n\n%s\n\nFaqat raqam kiriting. Qaytadan urinib ko'ring.",
                String.join(", ", invalidIds)));
            return;
        }
        
        // If no valid IDs, show error
        if (targetChatIds.isEmpty()) {
            sendMessage(chatId, "❌ Chat ID kiritilmadi. Qaytadan kiriting:");
            return;
        }
        
        // Send progress message
        sendMessage(chatId, String.format("⏳ Xabar %d ta foydalanuvchiga yuborilmoqda...", targetChatIds.size()));
        
        int successCount = 0;
        int notFoundCount = 0;
        int failCount = 0;
        List<Long> successIds = new ArrayList<>();
        List<Long> notFoundIds = new ArrayList<>();
        List<Long> failedIds = new ArrayList<>();
        
        // Send to each user
        for (Long targetChatId : targetChatIds) {
            try {
                // Check if user exists
                Optional<User> user = userService.findByChatId(targetChatId);
                if (user.isEmpty()) {
                    notFoundIds.add(targetChatId);
                    notFoundCount++;
                    continue;
                }
                
                // Send welcome message
                sendWelcomeMessageToUser(targetChatId);
                successIds.add(targetChatId);
                successCount++;
                
                // Small delay to avoid rate limits
                Thread.sleep(50);
                
            } catch (Exception e) {
                log.error("Failed to send welcome message to chatId={}", targetChatId, e);
                failedIds.add(targetChatId);
                failCount++;
            }
        }
        
        // Build result message
        StringBuilder resultText = new StringBuilder("✅ *Yuborish yakunlandi!*\n\n");
        resultText.append(String.format("Muvaffaqiyatli: *%d*\n", successCount));
        
        if (notFoundCount > 0) {
            resultText.append(String.format("Topilmadi: *%d*\n", notFoundCount));
        }
        if (failCount > 0) {
            resultText.append(String.format("Xatolik: *%d*\n", failCount));
        }
        
        resultText.append(String.format("Jami: *%d*\n\n", targetChatIds.size()));
        
        // Add details for single user or show IDs for multiple
        if (targetChatIds.size() == 1) {
            if (successCount == 1) {
                resultText.append(String.format("Chat ID: `%d`", successIds.get(0)));
            } else if (notFoundCount == 1) {
                resultText.append(String.format("❌ Chat ID topilmadi: `%d`", notFoundIds.get(0)));
            } else if (failCount == 1) {
                resultText.append(String.format("❌ Xatolik: `%d`", failedIds.get(0)));
            }
        } else {
            // For multiple users, show categorized lists if needed
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
        
        sendMessage(chatId, resultText.toString(), createBackToMenuButton());
        
        // Clear state
        userStates.remove(chatId);
        
        log.info("Broadcast to multiple users completed: success={}, notFound={}, failed={}, total={}", 
                successCount, notFoundCount, failCount, targetChatIds.size());
    }
    
    /**
     * Send welcome message with button to a user
     */
    private void sendWelcomeMessageToUser(Long targetChatId) throws TelegramApiException {
        if (referralBot == null) {
            throw new IllegalStateException("Referral bot not set. Cannot send messages to users.");
        }
        
        String welcomeText = settingsService.getWelcomeMessageText();
        
        SendMessage message = new SendMessage();
        message.setChatId(targetChatId.toString());
        message.setText(welcomeText);
        message.setParseMode("Markdown");
        
        // Add "Takliflarim" button
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        InlineKeyboardButton myReferralsButton = new InlineKeyboardButton();
        myReferralsButton.setText("👥 Takliflarim");
        myReferralsButton.setCallbackData("my_referrals");
        
        rows.add(List.of(myReferralsButton));
        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);
        
        // Send via referral bot
        referralBot.execute(message);
    }
    
    /**
     * Handle photo upload for stats header image
     */
    private void handlePhotoUpload(Long chatId, Message message) {
        String state = userStates.get(chatId);
        
        if (state == null || !state.equals("awaiting_stats_header_image")) {
            sendMessage(chatId, "❌ Rasm kutilmayapti. Iltimos sozlamalar menyusidan boshqa harakatni tanlang.");
            return;
        }
        
        try {
            // Get the highest resolution photo
            List<PhotoSize> photos = message.getPhoto();
            PhotoSize photo = photos.stream()
                .max(Comparator.comparing(PhotoSize::getFileSize))
                .orElse(null);
            
            if (photo == null) {
                sendMessage(chatId, "❌ Rasm topilmadi. Qaytadan urinib ko'ring.");
                return;
            }
            
            // Get file from Telegram
            GetFile getFileMethod = new GetFile();
            getFileMethod.setFileId(photo.getFileId());
            org.telegram.telegrambots.meta.api.objects.File file = execute(getFileMethod);
            
            // Download file
            java.io.File downloadedFile = downloadFile(file);
            
            // Generate unique filename
            String fileName = "stats_header_" + System.currentTimeMillis() + ".jpg";
            Path destinationPath = Paths.get(IMAGES_DIRECTORY + fileName);
            
            // Delete old image if exists
            settingsService.getStatsHeaderImagePath().ifPresent(oldPath -> {
                try {
                    Files.deleteIfExists(Paths.get(oldPath));
                    log.info("Deleted old stats header image: {}", oldPath);
                } catch (IOException e) {
                    log.error("Failed to delete old image file", e);
                }
            });
            
            // Move file to permanent location
            Files.move(downloadedFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
            
            // Save path to database
            String savedPath = destinationPath.toString();
            settingsService.setStatsHeaderImagePath(savedPath);
            
            sendMessage(chatId, String.format("✅ *Statistika rasmi o'zgartirildi!*\n\nFayl saqlandi: `%s`", fileName), createBackToMenuButton());
            
            // Clear state
            userStates.remove(chatId);
            
            log.info("Stats header image uploaded successfully: {}", savedPath);
            
        } catch (Exception e) {
            log.error("Error handling photo upload", e);
            sendMessage(chatId, "❌ Rasmni yuklashda xatolik yuz berdi. Qaytadan urinib ko'ring.");
        }
    }
    
    /**
     * Handle document upload for stats header image (uncompressed files)
     */
    private void handleDocumentUpload(Long chatId, Message message) {
        String state = userStates.get(chatId);
        
        if (state == null || !state.equals("awaiting_stats_header_image")) {
            sendMessage(chatId, "❌ Rasm kutilmayapti. Iltimos sozlamalar menyusidan boshqa harakatni tanlang.");
            return;
        }
        
        try {
            Document document = message.getDocument();
            
            if (document == null) {
                sendMessage(chatId, "❌ Fayl topilmadi. Qaytadan urinib ko'ring.");
                return;
            }
            
            // Verify it's an image file
            String mimeType = document.getMimeType();
            String fileName = document.getFileName();
            
            if (mimeType == null || (!mimeType.startsWith("image/") && !isImageFile(fileName))) {
                sendMessage(chatId, "❌ Iltimos, faqat rasm faylini yuboring (JPG, PNG, JPEG, etc.).");
                return;
            }
            
            // Get file from Telegram
            GetFile getFileMethod = new GetFile();
            getFileMethod.setFileId(document.getFileId());
            org.telegram.telegrambots.meta.api.objects.File file = execute(getFileMethod);
            
            // Download file
            java.io.File downloadedFile = downloadFile(file);
            
            // Get file extension from original filename
            String extension = getFileExtension(fileName);
            if (extension.isEmpty()) {
                extension = "jpg"; // Default to jpg if no extension found
            }
            
            // Generate unique filename
            String savedFileName = "stats_header_" + System.currentTimeMillis() + "." + extension;
            Path destinationPath = Paths.get(IMAGES_DIRECTORY + savedFileName);
            
            // Delete old image if exists
            settingsService.getStatsHeaderImagePath().ifPresent(oldPath -> {
                try {
                    Files.deleteIfExists(Paths.get(oldPath));
                    log.info("Deleted old stats header image: {}", oldPath);
                } catch (IOException e) {
                    log.error("Failed to delete old image file", e);
                }
            });
            
            // Move file to permanent location
            Files.move(downloadedFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
            
            // Save path to database
            String savedPath = destinationPath.toString();
            settingsService.setStatsHeaderImagePath(savedPath);
            
            sendMessage(chatId, String.format("✅ *Statistika rasmi o'zgartirildi!*\n\nFayl saqlandi: `%s`", savedFileName), createBackToMenuButton());
            
            // Clear state
            userStates.remove(chatId);
            
            log.info("Stats header image (document) uploaded successfully: {}", savedPath);
            
        } catch (Exception e) {
            log.error("Error handling document upload", e);
            sendMessage(chatId, "❌ Faylni yuklashda xatolik yuz berdi. Qaytadan urinib ko'ring.");
        }
    }
    
    /**
     * Check if filename has image extension
     */
    private boolean isImageFile(String fileName) {
        if (fileName == null) return false;
        String lowerFileName = fileName.toLowerCase();
        return lowerFileName.endsWith(".jpg") || 
               lowerFileName.endsWith(".jpeg") || 
               lowerFileName.endsWith(".png") || 
               lowerFileName.endsWith(".gif") || 
               lowerFileName.endsWith(".bmp") || 
               lowerFileName.endsWith(".webp");
    }
    
    /**
     * Get file extension from filename
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
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
     * Send photo from file with caption
     */
    private void sendPhotoFromFile(Long chatId, String filePath, String caption) throws TelegramApiException {
        SendPhoto photo = new SendPhoto();
        photo.setChatId(chatId.toString());
        photo.setPhoto(new InputFile(new File(filePath)));
        if (caption != null && !caption.isEmpty()) {
            photo.setCaption(caption);
            photo.setParseMode("Markdown");
        }
        execute(photo);
        log.info("Sent photo from file to chatId={}: {}", chatId, filePath);
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
