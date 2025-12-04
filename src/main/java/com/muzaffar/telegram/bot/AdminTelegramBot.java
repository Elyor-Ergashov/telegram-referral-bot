package com.muzaffar.telegram.bot;

import static com.muzaffar.telegram.constants.BotConstants.*;

import com.muzaffar.telegram.config.AppConfig;
import com.muzaffar.telegram.config.BotConfig;
import com.muzaffar.telegram.entity.Channel;
import com.muzaffar.telegram.entity.User;
import com.muzaffar.telegram.enums.MessageKey;
import com.muzaffar.telegram.service.BotSettingsService;
import com.muzaffar.telegram.service.ChannelService;
import com.muzaffar.telegram.service.MessageTemplateService;
import com.muzaffar.telegram.service.UserService;
import com.muzaffar.telegram.util.KeyboardBuilder;
import com.muzaffar.telegram.util.TelegramBotUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import javax.imageio.ImageIO;

@Slf4j
@Component
public class AdminTelegramBot extends TelegramLongPollingBot {
    
    private final BotConfig botConfig;
    private final AppConfig appConfig;
    private final ChannelService channelService;
    private final BotSettingsService settingsService;
    private final MessageTemplateService messageTemplateService;
    private final UserService userService;
    private final com.muzaffar.telegram.service.MessageService messageService;
    private final TelegramBotUtils botUtils;
    
    private ReferralTelegramBot referralBot;
    
    private final Map<Long, String> userStates = new HashMap<>();
    private final Map<Long, Map<String, String>> userData = new HashMap<>();
    
    public AdminTelegramBot(BotConfig botConfig, AppConfig appConfig,
                           ChannelService channelService, BotSettingsService settingsService,
                           MessageTemplateService messageTemplateService, UserService userService,
                           com.muzaffar.telegram.service.MessageService messageService,
                           TelegramBotUtils botUtils) {
        super(botConfig.getAdminBot().getToken());
        this.botConfig = botConfig;
        this.appConfig = appConfig;
        this.channelService = channelService;
        this.botUtils = botUtils;
        this.settingsService = settingsService;
        this.messageTemplateService = messageTemplateService;
        this.userService = userService;
        this.messageService = messageService;
        
        try {
            String fullPath = botUtils.getFullImagePath("");
            Files.createDirectories(Paths.get(fullPath));
            log.info("Stats header images directory created/verified: {}", fullPath);
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
                    
                    if (text.equals(START_COMMAND) || text.equals(MENU_COMMAND)) {
                        showMainMenu(chatId);
                    } else {
                        handleTextInput(chatId, text, message.getMessageId());
                    }
                } else if (message.hasPhoto()) {
                    handlePhotoUpload(chatId, message);
                } else if (message.hasDocument()) {
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
        
        InlineKeyboardMarkup keyboard = new KeyboardBuilder()
            .addButton("📺 Kanallarni boshqarish", CALLBACK_MENU_CHANNELS)
            .newRow()
            .addButton("⚙️ Sozlamalar", CALLBACK_MENU_SETTINGS)
            .addButton("📢 Xabar yuborish", CALLBACK_MENU_BROADCAST)
            .build();
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
        
        InlineKeyboardMarkup keyboard = new KeyboardBuilder()
            .addButton("➕ Kanal qo'shish", CALLBACK_CHANNEL_ADD)
            .newRow()
            .addButton("📋 Kanallar ro'yxati", CALLBACK_CHANNEL_LIST)
            .newRow()
            .addButton("🗑 Kanalni o'chirish", CALLBACK_CHANNEL_REMOVE)
            .newRow()
            .addButton("« Orqaga", CALLBACK_MENU_MAIN)
            .build();
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
        message.setText("⚙️ *Sozlamalar*\n\n" +
                "🔗 Kerakli amallarni tanlang:");
        message.setParseMode("Markdown");
        
        InlineKeyboardMarkup keyboard = new KeyboardBuilder()
            .addButton("🔢 Referallar sonini o'zgartirish", CALLBACK_SETTINGS_SET_REFERRALS)
            .newRow()
            .addButton("🔗 Shaxsiy kanal havolasini o'zgartirish", CALLBACK_SETTINGS_SET_PRIVATE_LINK)
            .newRow()
            .addButton("📝 Information xabarini o'zgartirish", CALLBACK_SETTINGS_INFORMATION)
            .newRow()
            .addButton("📊 Statistika sarlavhasini o'zgartirish", CALLBACK_SETTINGS_SET_STATS_TEXT)
            .newRow()
            .addButton("« Orqaga", CALLBACK_MENU_MAIN)
            .build();
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
            "Information xabarini foydalanuvchilarga yuborish:\n\n" +
            "Quyidagi variantlardan birini tanlang:"
        );
        message.setParseMode("Markdown");
        
        InlineKeyboardMarkup keyboard = new KeyboardBuilder()
            .addButton("📤 Barcha foydalanuvchilarga", CALLBACK_BROADCAST_INFORMATION_ALL)
            .newRow()
            .addButton("👥 Tanlangan foydalanuvchilarga", CALLBACK_BROADCAST_INFORMATION_SINGLE)
            .newRow()
            .addButton("« Orqaga", CALLBACK_MENU_MAIN)
            .build();
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
            case CALLBACK_MENU_MAIN:
            case CALLBACK_MAIN_MENU:
                editToMainMenu(chatId, messageId);
                break;
            case CALLBACK_MENU_CHANNELS:
                showChannelMenu(chatId, messageId);
                break;
            case CALLBACK_MENU_SETTINGS:
                showSettingsMenu(chatId, messageId);
                break;
            case CALLBACK_MENU_BROADCAST:
                showBroadcastMenu(chatId, messageId);
                break;
            case CALLBACK_BROADCAST_INFORMATION_ALL:
                IonfirmBroadcastinformationAll(chatId, messageId);
                break;
            case CALLBACK_BROADCAST_INFORMATION_ALL_CONFIRM:
                IxecuteBroadcastinformationAll(chatId, messageId);
                break;
            case CALLBACK_BROADCAST_INFORMATION_SINGLE:
                ItartBroadcastinformationSingle(chatId, messageId);
                break;
            case CALLBACK_CHANNEL_ADD:
                startAddChannel(chatId, messageId);
                break;
            case CALLBACK_CHANNEL_LIST:
                showChannelList(chatId, messageId);
                break;
            case CALLBACK_CHANNEL_REMOVE:
                startRemoveChannel(chatId, messageId);
                break;
            case CALLBACK_SETTINGS_SET_REFERRALS:
                startSetReferrals(chatId, messageId);
                break;
            case CALLBACK_SETTINGS_SET_PRIVATE_LINK:
                startSetPrivateLink(chatId, messageId);
                break;
            case CALLBACK_SETTINGS_INFORMATION:
                ItartSetinformationMessage(chatId, messageId);
                break;
            case CALLBACK_SETTINGS_SET_STATS_TEXT:
                startSetStatsHeader(chatId, messageId);
                break;
            case CALLBACK_EDIT_INFORMATION_TEXT:
                ItartEditinformationText(chatId, messageId);
                break;
            case CALLBACK_EDIT_INFORMATION_IMAGE:
                ItartEditinformationImage(chatId, messageId);
                break;
            case CALLBACK_DELETE_INFORMATION_IMAGE:
                IonfirmDeleteinformationImage(chatId, messageId);
                break;
            case CALLBACK_EDIT_STATS_TEXT:
                startEditStatsText(chatId, messageId);
                break;
            case CALLBACK_EDIT_STATS_IMAGE:
                startEditStatsImage(chatId, messageId);
                break;
            case CALLBACK_DELETE_STATS_IMAGE:
                confirmDeleteStatsImage(chatId, messageId);
                break;
            case CALLBACK_CANCEL_OPERATION:
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
        userStates.remove(chatId);
        userData.remove(chatId);
        
        log.info("User {} cancelled operation, returning to main menu", chatId);
        
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
        
        InlineKeyboardMarkup keyboard = new KeyboardBuilder()
            .addButton("📺 Kanallarni boshqarish", CALLBACK_MENU_CHANNELS)
                .newRow()
            .addButton("⚙️ Sozlamalar", CALLBACK_MENU_SETTINGS)
            .addButton("📢 Xabar yuborish", CALLBACK_MENU_BROADCAST)
            .build();
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
        userStates.put(chatId, STATE_AWAITING_CHANNEL_ID);
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
        
        KeyboardBuilder builder = new KeyboardBuilder();
        for (Channel channel : channels) {
            builder.addRow(channel.getChannelName(), "remove_channel_" + channel.getChannelId());
        }
        
        InlineKeyboardMarkup keyboard = builder
            .addRow("« Orqaga", CALLBACK_MENU_CHANNELS)
            .build();
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
        userStates.put(chatId, STATE_AWAITING_REFERRAL_COUNT);
        
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
            deleteMessage(chatId, messageId);
            return;
        }
        
        switch (state) {
            case STATE_AWAITING_CHANNEL_ID:
                handleChannelIdInput(chatId, text, messageId);
                break;
            case STATE_AWAITING_CHANNEL_NAME:
                handleChannelNameInput(chatId, text, messageId);
                break;
            case STATE_AWAITING_CHANNEL_USERNAME:
                handleChannelUsernameInput(chatId, text, messageId);
                break;
            case STATE_AWAITING_REFERRAL_COUNT:
                handleReferralCountInput(chatId, text, messageId);
                break;
            case STATE_AWAITING_PRIVATE_LINK:
                handlePrivateLinkInput(chatId, text, messageId);
                break;
            case STATE_AWAITING_INFORMATION_MESSAGE:
                IandleinformationMessageInput(chatId, text, messageId);
                break;
            case STATE_AWAITING_STATS_HEADER:
                handleStatsHeaderInput(chatId, text, messageId);
                break;
            case STATE_AWAITING_BROADCAST_MESSAGE:
                handleBroadcastUserIdInput(chatId, text, messageId);
                break;
            default:
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
        deleteMessage(chatId, messageId);
        Map<String, String> data = userData.get(chatId);
        data.put("channelId", channelId);
        
        userStates.put(chatId, STATE_AWAITING_CHANNEL_NAME);
        sendMessage(chatId, "✅ Kanal ID qabul qilindi.\n\n📝 Endi kanal nomini yuboring:\n\n*Masalan:* `Teacher Muzaffar Math`", createCancelButton());
    }
    
    /**
     * Handle channel name input
     */
    private void handleChannelNameInput(Long chatId, String channelName, Integer messageId) {
        deleteMessage(chatId, messageId);
        Map<String, String> data = userData.get(chatId);
        data.put("channelName", channelName);
        
        userStates.put(chatId, STATE_AWAITING_CHANNEL_USERNAME);
        sendMessage(chatId, "✅ Kanal nomi qabul qilindi.\n\n👤 Endi kanal username ini yuboring (@ belgisisiz):\n\n*Masalan:* `TeacherMuzaffar`", createCancelButton());
    }
    
    /**
     * Handle channel username input
     */
    private void handleChannelUsernameInput(Long chatId, String channelUsername, Integer messageId) {
        deleteMessage(chatId, messageId);
        Map<String, String> data = userData.get(chatId);
        
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
            userStates.remove(chatId);
            userData.remove(chatId);
        }
    }
    
    /**
     * Handle referral count input
     */
    private void handleReferralCountInput(Long chatId, String text, Integer messageId) {
        deleteMessage(chatId, messageId);
        
        try {
            int count = Integer.parseInt(text.trim());
            
            if (count < 1) {
                sendMessage(chatId, "❌ Referallar soni kamida 1 bo'lishi kerak.\n\nQaytadan kiriting:");
                return;
            }
            
            settingsService.setRequiredReferralCount(count);
            sendMessage(chatId, String.format("✅ *Referallar soni o'zgartirildi!*\n\nYangi qiymat: *%d*", count), createBackToMenuButton());
            
            userStates.remove(chatId);
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Noto'g'ri format. Faqat son kiriting.\n\nQaytadan kiriting:");
        }
    }
    
    /**
     * Start set private channel link flow
     */
    private void startSetPrivateLink(Long chatId, Integer messageId) {
        userStates.put(chatId, STATE_AWAITING_PRIVATE_LINK);
        
        String currentLink = settingsService.getPrivateChannelLink().orElse("O'rnatilmagan");
        editMessage(chatId, messageId, 
            String.format("🔗 *Shaxsiy kanal havolasini o'zgartirish*\n\nHozirgi havola: `%s`\n\nYangi havolani yuboring (Telegram invite link):\n\n*Masalan:* `https://t.me/+abc123`", currentLink),
            createCancelButton());
    }
    
    /**
     * Handle private channel link input
     */
    private void handlePrivateLinkInput(Long chatId, String text, Integer messageId) {
        deleteMessage(chatId, messageId);
        
        String link = text.trim();
        
        if (!link.startsWith("https://t.me/") && !link.startsWith("http://t.me/")) {
            sendMessage(chatId, "❌ Noto'g'ri havola formati.\n\nTelegram havolasi bo'lishi kerak (https://t.me/...).\n\nQaytadan kiriting:");
            return;
        }
        
        settingsService.setPrivateChannelLink(link);
        sendMessage(chatId, String.format("✅ *Shaxsiy kanal havolasi o'zgartirildi!*\n\nYangi havola: `%s`", link), createBackToMenuButton());
        
        userStates.remove(chatId);
    }
    
    /**
     * Start set information message flow - show action menu
     */
    private void ItartSetinformationMessage(Long chatId, Integer messageId) {
        deleteMessage(chatId, messageId);
        
        messageTemplateService.getImagePath(MessageKey.INFORMATION_MESSAGE).ifPresent(imagePath -> {
            try {
                sendPhotoFromFile(chatId, imagePath, "🖼 *Hozirgi rasm:*");
            } catch (Exception e) {
                log.error("Error sending current information image", e);
            }
        });
        
        String currentText = messageTemplateService.getText(MessageKey.INFORMATION_MESSAGE);
        boolean hasImage = messageTemplateService.getImagePath(MessageKey.INFORMATION_MESSAGE).isPresent();
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(
            String.format(
                "📝 *Information xabarini o'zgartirish*\n\n" +
                "📝 *Hozirgi matn:*\n```\n%s\n```\n\n" +
                "🖼 *Rasm:* %s\n\n" +
                "Kerakli amalni tanlang:",
                currentText,
                hasImage ? "✅ O'rnatilgan" : "❌ O'rnatilmagan"
            )
        );
        message.setParseMode("Markdown");
        
        KeyboardBuilder builder = new KeyboardBuilder()
            .addRow("🔹 Matnni o'zgartirish", CALLBACK_EDIT_INFORMATION_TEXT)
            .addRow(hasImage ? "🔹 Rasmni o'zgartirish" : "🔹 Rasm qo'shish", CALLBACK_EDIT_INFORMATION_IMAGE);
        
        if (hasImage) {
            builder.addButton("🔹 Rasmni o'chirish", CALLBACK_DELETE_INFORMATION_IMAGE);
        }
        
        InlineKeyboardMarkup keyboard = builder
            .addRow("« Orqaga", CALLBACK_MENU_SETTINGS)
            .build();
        message.setReplyMarkup(keyboard);
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error showing information message menu", e);
        }
    }
    
    /**
     * Handle information message text input
     */
    private void IandleinformationMessageInput(Long chatId, String text, Integer messageId) {
        deleteMessage(chatId, messageId);
        
        String trimmedText = text.trim();
        
        if (trimmedText.isEmpty()) {
            sendMessage(chatId, "❌ Xabar bo'sh bo'lishi mumkin emas.\n\nQaytadan kiriting:");
            return;
        }
        
        messageTemplateService.updateText(MessageKey.INFORMATION_MESSAGE, trimmedText);
        sendMessage(chatId, "✅ *Information xabari o'zgartirildi!*\n\nYangi foydalanuvchilar ushbu xabarni ko'rishadi.", createBackToMenuButton());
        
        userStates.remove(chatId);
    }
    
    /**
     * Start set stats header flow - show action menu
     */
    private void startSetStatsHeader(Long chatId, Integer messageId) {
        deleteMessage(chatId, messageId);
        
        messageTemplateService.getImagePath(MessageKey.STATS_HEADER).ifPresent(imagePath -> {
            try {
                sendPhotoFromFile(chatId, imagePath, "🖼 *Hozirgi rasm:*");
            } catch (Exception e) {
                log.error("Error sending current stats header image", e);
            }
        });
        
        String currentText = messageTemplateService.getText(MessageKey.STATS_HEADER);
        if (currentText == null || currentText.isEmpty()) {
            currentText = "O'rnatilmagan";
        }
        boolean hasImage = messageTemplateService.getImagePath(MessageKey.STATS_HEADER).isPresent();
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(
            String.format(
                "📊 *Statistika sarlavhasini o'zgartirish*\n\n" +
                "📝 *Hozirgi matn:*\n```\n%s\n```\n\n" +
                "🖼 *Rasm:* %s\n\n" +
                "Kerakli amalni tanlang:",
                currentText,
                hasImage ? "✅ O'rnatilgan" : "❌ O'rnatilmagan"
            )
        );
        message.setParseMode("Markdown");
        
        KeyboardBuilder builder = new KeyboardBuilder()
            .addRow("🔹 Matnni o'zgartirish", CALLBACK_EDIT_STATS_TEXT)
            .addRow(hasImage ? "🔹 Rasmni o'zgartirish" : "🔹 Rasm qo'shish", CALLBACK_EDIT_STATS_IMAGE);
        
        if (hasImage) {
            builder.addButton("🔹 Rasmni o'chirish", CALLBACK_DELETE_STATS_IMAGE);
        }
        
        InlineKeyboardMarkup keyboard = builder
            .addRow("« Orqaga", CALLBACK_MENU_SETTINGS)
            .build();
        message.setReplyMarkup(keyboard);
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error showing stats header menu", e);
        }
    }
    
    /**
     * Handle stats header text input
     */
    private void handleStatsHeaderInput(Long chatId, String text, Integer messageId) {
        deleteMessage(chatId, messageId);
        
        String trimmedText = text.trim();
        
        if (trimmedText.isEmpty()) {
            sendMessage(chatId, "❌ Matn bo'sh bo'lishi mumkin emas.\n\nQaytadan kiriting:");
            return;
        }
        
        messageTemplateService.updateText(MessageKey.STATS_HEADER, trimmedText);
        sendMessage(chatId, "✅ *Statistika sarlavhasi o'zgartirildi!*", createBackToMenuButton());
        
        userStates.remove(chatId);
    }
    
    // ========== Information Message Actions ==========
    
    /**
     * Start editing information text
     */
    private void ItartEditinformationText(Long chatId, Integer messageId) {
        userStates.put(chatId, STATE_AWAITING_INFORMATION_MESSAGE);
        
        String currentText = messageTemplateService.getText(MessageKey.INFORMATION_MESSAGE);
        
        editMessage(chatId, messageId,
            String.format(
                "📝 *Information matnini o'zgartirish*\n\n" +
                "📝 *Hozirgi matn:*\n```\n%s\n```\n\n" +
                "Yangi matnni yuboring:\n\n" +
                "*Eslatma:* Markdown formatidan foydalanishingiz mumkin (*bold*, _italic_, `code`)",
                currentText
            ),
            createCancelButton()
        );
    }
    
    /**
     * Start editing information image
     */
    private void ItartEditinformationImage(Long chatId, Integer messageId) {
        userStates.put(chatId, STATE_AWAITING_INFORMATION_IMAGE);
        
        deleteMessage(chatId, messageId);
        
        messageTemplateService.getImagePath(MessageKey.INFORMATION_MESSAGE).ifPresent(imagePath -> {
            try {
                sendPhotoFromFile(chatId, imagePath, "🖼 *Hozirgi rasm:*");
            } catch (Exception e) {
                log.error("Error sending current information image", e);
            }
        });
        
        sendMessage(chatId,
            "🖼 *Information rasmini o'zgartirish*\n\n" +
            "Yangi rasmni yuboring (foto yoki fayl ko'rinishida):\n\n" +
            "*Eslatma:* Sifatli rasm uchun faylni siqilmagan holda yuboring (Document).",
            createCancelButton()
        );
    }
    
    /**
     * Confirm delete information image
     */
    private void IonfirmDeleteinformationImage(Long chatId, Integer messageId) {
        messageTemplateService.getImagePath(MessageKey.INFORMATION_MESSAGE).ifPresent(oldPath -> {
            try {
                String fullOldPath = botUtils.getFullImagePath(oldPath);
                Files.deleteIfExists(Paths.get(fullOldPath));
                log.info("Deleted information image: {}", fullOldPath);
            } catch (IOException e) {
                log.error("Failed to delete old image file", e);
            }
        });
        
        messageTemplateService.updateImagePath(MessageKey.INFORMATION_MESSAGE, "");
        
        editMessage(chatId, messageId,
            "✅ *Information rasmi o'chirildi!*\n\nRasm endi ko'rsatilmaydi.",
            createBackToMenuButton()
        );
    }
    
    // ========== Stats Header Actions ==========
    
    /**
     * Start editing stats header text
     */
    private void startEditStatsText(Long chatId, Integer messageId) {
        userStates.put(chatId, STATE_AWAITING_STATS_HEADER);
        
        String currentText = messageTemplateService.getText(MessageKey.STATS_HEADER);
        if (currentText == null || currentText.isEmpty()) {
            currentText = "O'rnatilmagan";
        }
        
        editMessage(chatId, messageId,
            String.format(
                "📝 *Statistika sarlavhasini o'zgartirish*\n\n" +
                "📝 *Hozirgi matn:*\n```\n%s\n```\n\n" +
                "Yangi matnni yuboring:",
                currentText
            ),
            createCancelButton()
        );
    }
    
    /**
     * Start editing stats header image
     */
    private void startEditStatsImage(Long chatId, Integer messageId) {
        userStates.put(chatId, STATE_AWAITING_STATS_IMAGE);
        
        deleteMessage(chatId, messageId);
        
        messageTemplateService.getImagePath(MessageKey.STATS_HEADER).ifPresent(imagePath -> {
            try {
                sendPhotoFromFile(chatId, imagePath, "🖼 *Hozirgi rasm:*");
            } catch (Exception e) {
                log.error("Error sending current stats header image", e);
            }
        });
        
        sendMessage(chatId,
            "🖼 *Statistika rasmini o'zgartirish*\n\n" +
            "Yangi rasmni yuboring (foto yoki fayl ko'rinishida):\n\n" +
            "*Eslatma:* Sifatli rasm uchun faylni siqilmagan holda yuboring (Document).",
            createCancelButton()
        );
    }
    
    /**
     * Confirm delete stats header image
     */
    private void confirmDeleteStatsImage(Long chatId, Integer messageId) {
        messageTemplateService.getImagePath(MessageKey.STATS_HEADER).ifPresent(oldPath -> {
            try {
                String fullOldPath = botUtils.getFullImagePath(oldPath);
                Files.deleteIfExists(Paths.get(fullOldPath));
                log.info("Deleted stats header image: {}", fullOldPath);
            } catch (IOException e) {
                log.error("Failed to delete old image file", e);
            }
        });
        
        messageTemplateService.updateImagePath(MessageKey.STATS_HEADER, "");
        
        editMessage(chatId, messageId,
            "✅ *Statistika rasmi o'chirildi!*\n\nRasm endi ko'rsatilmaydi.",
            createBackToMenuButton()
        );
    }
    
    /**
     * Confirm broadcast information message to all users
     */
    private void IonfirmBroadcastinformationAll(Long chatId, Integer messageId) {
        long totalUsers = userService.getTotalUserCount();
        
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(
            String.format("📢 *Barcha foydalanuvchilarga yuborish*\n\n" +
            "Information xabari *%d* ta foydalanuvchiga yuboriladi.\n\n" +
            "Davom etishni xohlaysizmi?", totalUsers)
        );
        message.setParseMode("Markdown");
        
        InlineKeyboardMarkup keyboard = new KeyboardBuilder()
            .addButton("✅ Ha, yuborish", CALLBACK_BROADCAST_INFORMATION_ALL_CONFIRM)
            .newRow()
            .addButton(BUTTON_CANCEL, CALLBACK_MENU_BROADCAST)
            .build();
        message.setReplyMarkup(keyboard);
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error showing broadcast confirmation", e);
        }
    }
    
    /**
     * Execute broadcast information message to all users
     */
    private void IxecuteBroadcastinformationAll(Long chatId, Integer messageId) {
        deleteMessage(chatId, messageId);
        
        sendMessage(chatId, "⏳ Xabarlar yuborilmoqda...");
        
        List<com.muzaffar.telegram.entity.User> users = userService.getAllUsers();
        
        int successCount = 0;
        int failCount = 0;
        
        for (com.muzaffar.telegram.entity.User user : users) {
            try {
                IendinformationMessageToUser(user.getChatId());
                successCount++;
                
                Thread.sleep(BROADCAST_DELAY_MS);
            } catch (Exception e) {
                log.error("Failed to send information message to chatId={}", user.getChatId(), e);
                failCount++;
            }
        }
        
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
     * Start broadcast information to multiple users
     */
    private void ItartBroadcastinformationSingle(Long chatId, Integer messageId) {
        userStates.put(chatId, STATE_AWAITING_BROADCAST_MESSAGE);
        
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
            sendMessage(chatId, 
                String.format("❌ *Noto'g'ri format:*\n\n%s\n\nFaqat raqam kiriting. Qaytadan urinib ko'ring.",
                String.join(", ", invalidIds)));
            return;
        }
        
        if (targetChatIds.isEmpty()) {
            sendMessage(chatId, "❌ Chat ID kiritilmadi. Qaytadan kiriting:");
            return;
        }
        
        sendMessage(chatId, String.format("⏳ Xabar %d ta foydalanuvchiga yuborilmoqda...", targetChatIds.size()));
        
        int successCount = 0;
        int notFoundCount = 0;
        int failCount = 0;
        List<Long> successIds = new ArrayList<>();
        List<Long> notFoundIds = new ArrayList<>();
        List<Long> failedIds = new ArrayList<>();
        
        for (Long targetChatId : targetChatIds) {
            try {
                Optional<User> user = userService.findByChatId(targetChatId);
                if (user.isEmpty()) {
                    notFoundIds.add(targetChatId);
                    notFoundCount++;
                    continue;
                }
                
                IendinformationMessageToUser(targetChatId);
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
        
        sendMessage(chatId, resultText.toString(), createBackToMenuButton());
        
        userStates.remove(chatId);
        
        log.info("Broadcast to multiple users completed: success={}, notFound={}, failed={}, total={}", 
                successCount, notFoundCount, failCount, targetChatIds.size());
    }
    
    /**
     * Send information message with button to a user
     * If an image exists for information message, sends it as photo with caption
     */
    private void IendinformationMessageToUser(Long targetChatId) throws TelegramApiException {
        if (referralBot == null) {
            throw new IllegalStateException("Referral bot not set. Cannot send messages to users.");
        }
        
        messageService.sendInformationMessageWithImage(referralBot, targetChatId);
    }
    
    /**
     * Handle photo upload for message template images
     */
    private void handlePhotoUpload(Long chatId, Message message) {
        String state = userStates.get(chatId);
        
        MessageKey templateKey;
        if (STATE_AWAITING_INFORMATION_MESSAGE.equals(state) || STATE_AWAITING_INFORMATION_IMAGE.equals(state)) {
            templateKey = MessageKey.INFORMATION_MESSAGE;
        } else if (STATE_AWAITING_STATS_HEADER.equals(state) || STATE_AWAITING_STATS_IMAGE.equals(state)) {
            templateKey = MessageKey.STATS_HEADER;
        } else {
            sendMessage(chatId, "❌ Rasm kutilmayapti. Iltimos sozlamalar menyusidan boshqa harakatni tanlang.");
            return;
        }
        
        try {
            List<PhotoSize> photos = message.getPhoto();
            PhotoSize photo = photos.stream()
                .max(Comparator.comparing(PhotoSize::getFileSize))
                .orElse(null);
            
            if (photo == null) {
                sendMessage(chatId, "❌ Rasm topilmadi. Qaytadan urinib ko'ring.");
                return;
            }
            
            GetFile getFileMethod = new GetFile();
            getFileMethod.setFileId(photo.getFileId());
            org.telegram.telegrambots.meta.api.objects.File file = execute(getFileMethod);
            
            java.io.File downloadedFile = downloadFile(file);
            
            File processedFile = validateAndProcessImage(downloadedFile, chatId);
            if (processedFile == null) {
                downloadedFile.delete();
                return;
            }
            
            String fileName = "stats_header_" + System.currentTimeMillis() + ".jpg";
            String relativePath = IMAGES_RELATIVE_DIRECTORY + fileName;
            String fullPath = botUtils.getFullImagePath(relativePath);
            Path destinationPath = Paths.get(fullPath);
            
            messageTemplateService.getImagePath(templateKey).ifPresent(oldPath -> {
                try {
                    String fullOldPath = botUtils.getFullImagePath(oldPath);
                    Files.deleteIfExists(Paths.get(fullOldPath));
                    log.info("Deleted old image for {}: {}", templateKey, fullOldPath);
                } catch (IOException e) {
                    log.error("Failed to delete old image file", e);
                }
            });
            
            Files.move(processedFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
            
            messageTemplateService.updateImagePath(templateKey, relativePath);
            
            String successMessage = templateKey == MessageKey.INFORMATION_MESSAGE
                ? "✅ *Information rasmi o'zgartirildi!*\n\nRasm muvaffaqiyatli saqlandi."
                : "✅ *Statistika rasmi o'zgartirildi!*\n\nRasm muvaffaqiyatli saqlandi.";
            sendMessage(chatId, successMessage, createBackToMenuButton());
            
            userStates.remove(chatId);
            
            log.info("Image uploaded successfully: {} -> {}", relativePath, fullPath);
            
        } catch (Exception e) {
            log.error("Error handling photo upload", e);
            sendMessage(chatId, "❌ Rasmni yuklashda xatolik yuz berdi. Qaytadan urinib ko'ring.");
        }
    }
    
    /**
     * Handle document upload for message template images (uncompressed files)
     */
    private void handleDocumentUpload(Long chatId, Message message) {
        String state = userStates.get(chatId);
        
        MessageKey templateKey;
        if (STATE_AWAITING_INFORMATION_MESSAGE.equals(state) || STATE_AWAITING_INFORMATION_IMAGE.equals(state)) {
            templateKey = MessageKey.INFORMATION_MESSAGE;
        } else if (STATE_AWAITING_STATS_HEADER.equals(state) || STATE_AWAITING_STATS_IMAGE.equals(state)) {
            templateKey = MessageKey.STATS_HEADER;
        } else {
            sendMessage(chatId, "❌ Rasm kutilmayapti. Iltimos sozlamalar menyusidan boshqa harakatni tanlang.");
            return;
        }
        
        try {
            Document document = message.getDocument();
            
            if (document == null) {
                sendMessage(chatId, "❌ Fayl topilmadi. Qaytadan urinib ko'ring.");
                return;
            }
            
            String mimeType = document.getMimeType();
            String fileName = document.getFileName();
            
            if (mimeType == null || (!mimeType.startsWith("image/") && !isImageFile(fileName))) {
                sendMessage(chatId, "❌ Iltimos, faqat rasm faylini yuboring (JPG, PNG, JPEG, etc.).");
                return;
            }
            
            GetFile getFileMethod = new GetFile();
            getFileMethod.setFileId(document.getFileId());
            org.telegram.telegrambots.meta.api.objects.File file = execute(getFileMethod);
            
            java.io.File downloadedFile = downloadFile(file);
            
            File processedFile = validateAndProcessImage(downloadedFile, chatId);
            if (processedFile == null) {
                downloadedFile.delete();
                return;
            }
            
            String savedFileName = "stats_header_" + System.currentTimeMillis() + ".jpg";
            String relativePath = IMAGES_RELATIVE_DIRECTORY + savedFileName;
            String fullPath = botUtils.getFullImagePath(relativePath);
            Path destinationPath = Paths.get(fullPath);
            
            messageTemplateService.getImagePath(templateKey).ifPresent(oldPath -> {
                try {
                    String fullOldPath = botUtils.getFullImagePath(oldPath);
                    Files.deleteIfExists(Paths.get(fullOldPath));
                    log.info("Deleted old image for {}: {}", templateKey, fullOldPath);
                } catch (IOException e) {
                    log.error("Failed to delete old image file", e);
                }
            });
            
            Files.move(processedFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
            
            messageTemplateService.updateImagePath(templateKey, relativePath);
            
            String successMessage = templateKey == MessageKey.INFORMATION_MESSAGE
                ? "✅ *Information rasmi o'zgartirildi!*\n\nRasm muvaffaqiyatli saqlandi."
                : "✅ *Statistika rasmi o'zgartirildi!*\n\nRasm muvaffaqiyatli saqlandi.";
            sendMessage(chatId, successMessage, createBackToMenuButton());
            
            userStates.remove(chatId);
            
            log.info("Image (document) uploaded successfully: {} -> {}", relativePath, fullPath);
            
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
     * Check if user is admin (delegates to TelegramBotUtils)
     */
    private boolean isAdmin(Long chatId) {
        return botUtils.isAdmin(chatId);
    }
    
    /**
     * Delete a message (delegates to TelegramBotUtils)
     */
    private void deleteMessage(Long chatId, Integer messageId) {
        botUtils.deleteMessage(this, chatId, messageId);
    }
    
    /**
     * Create inline keyboard with back to menu button
     */
    private InlineKeyboardMarkup createBackToMenuButton() {
        return KeyboardBuilder.singleButton(BUTTON_MENU, CALLBACK_MAIN_MENU);
    }
    
    /**
     * Create inline keyboard with cancel button
     */
    private InlineKeyboardMarkup createCancelButton() {
        return KeyboardBuilder.singleButton(BUTTON_CANCEL, CALLBACK_CANCEL_OPERATION);
    }
    
    /**
     * Validate uploaded image according to size standards
     * @param sourceFile The downloaded image file
     * @return The source file if validation passes, or null if validation fails
     */
    private File validateAndProcessImage(File sourceFile, Long chatId) {
        try {
            long fileSize = sourceFile.length();
            if (fileSize > MAX_FILE_SIZE_BYTES) {
                sendMessage(chatId,
                    String.format("❌ *Fayl hajmi juda katta!*\n\nMaksimal hajm: *%d MB*\nSizning faylingiz: *%.2f MB*",
                        MAX_FILE_SIZE_MB, fileSize / (1024.0 * 1024.0)),
                    createBackToMenuButton());
                return null;
            }
            
            BufferedImage originalImage = ImageIO.read(sourceFile);
            if (originalImage == null) {
                sendMessage(chatId, "❌ *Rasm faylini o'qib bo'lmadi!*\n\nIltimos, to'g'ri rasm formatida fayl yuklang (JPG, PNG).");
                return null;
            }
            
            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();
            
            log.info("Image dimensions: {}x{}", originalWidth, originalHeight);
            log.info("Image validation passed");
            return sourceFile;
            
        } catch (IOException e) {
            log.error("Error processing image", e);
            sendMessage(chatId, "❌ *Rasmni qayta ishlashda xatolik yuz berdi!*\n\nIltimos, boshqa rasm yuklang.");
            return null;
        }
    }
    
    /**
     * Send message (delegates to TelegramBotUtils)
     */
    private void sendMessage(Long chatId, String text) {
        sendMessage(chatId, text, null);
    }
    
    /**
     * Send message with keyboard (delegates to TelegramBotUtils)
     */
    private void sendMessage(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        if (keyboard != null) {
            botUtils.sendMessageWithKeyboard(this, chatId, text, keyboard);
        } else {
            botUtils.sendMessage(this, chatId, text);
        }
    }
    
    /**
     * Send photo from file with caption (delegates to TelegramBotUtils)
     */
    private void sendPhotoFromFile(Long chatId, String filePath, String caption) throws TelegramApiException {
        if (caption != null && !caption.isEmpty()) {
            botUtils.sendPhotoWithCaption(this, chatId, filePath, caption);
        } else {
            botUtils.sendPhotoFromFile(this, chatId, filePath);
        }
    }
    
    /**
     * Edit message (delegates to TelegramBotUtils)
     */
    private void editMessage(Long chatId, Integer messageId, String text) {
        editMessage(chatId, messageId, text, null);
    }
    
    /**
     * Edit message with keyboard (delegates to TelegramBotUtils)
     */
    private void editMessage(Long chatId, Integer messageId, String text, InlineKeyboardMarkup keyboard) {
        botUtils.editMessage(this, chatId, messageId, text);
        // Note: TelegramBotUtils.editMessage doesn't support keyboard parameter yet
        // For now, handle keyboard case inline if needed
        if (keyboard != null) {
            try {
                EditMessageText message = new EditMessageText();
                message.setChatId(chatId.toString());
                message.setMessageId(messageId);
                message.setText(text);
                message.setParseMode("Markdown");
                message.setReplyMarkup(keyboard);
                execute(message);
            } catch (TelegramApiException e) {
                log.error("Error editing message with keyboard", e);
            }
        }
    }
}
