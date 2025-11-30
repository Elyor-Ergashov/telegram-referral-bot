package com.muzaffar.telegram.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {
    
    /**
     * Create welcome message for new user
     */
    public String createWelcomeMessage(String botUsername, String referralCode, int referralCount, int requiredCount) {
        String referralLink = String.format("https://t.me/%s?start=%s", botUsername, referralCode);
        
        return String.format(
            "🎉 *Referral botga xush kelibsiz!*\n\n" +
            "👥 *Sizning referallaringiz:* %d/%d\n\n" +
            "🔗 *Sizning referal havolangiz:*\n`%s`\n\n" +
            "📢 Do'stlaringiz bilan havolangizni ulashing! *%d referal* to'plagandan so'ng, bizning maxsus shaxsiy kanalimizga kirish huquqiga ega bo'lasiz.\n\n" +
            "💡 Jarayoningizni tekshirish uchun /stats dan foydalaning.",
            referralCount, requiredCount, referralLink, requiredCount
        );
    }
    
    /**
     * Create stats message
     */
    public String createStatsMessage(String botUsername, String referralCode, int referralCount, int requiredCount) {
        String referralLink = String.format("https://t.me/%s?start=%s", botUsername, referralCode);
        int remaining = Math.max(0, requiredCount - referralCount);
        
        StringBuilder message = new StringBuilder();
        message.append("📊 *Sizning statistikangiz*\n\n");
        message.append(String.format("✅ *Muvaffaqiyatli referallar:* %d\n", referralCount));
        message.append(String.format("🎯 *Mukofot uchun kerak:* %d\n\n", requiredCount));
        
        if (remaining > 0) {
            message.append(String.format("⏳ *Qolgan referallar:* %d\n\n", remaining));
        } else {
            message.append("🎉 *Tabriklaymiz!* Siz kerakli referallarga erishdingiz!\n\n");
        }
        
        message.append("🔗 *Sizning referal havolangiz:*\n");
        message.append(String.format("`%s`\n\n", referralLink));
        message.append("💡 Ko'proq do'stlarni taklif qilish uchun havolangizni ulashing!");
        
        return message.toString();
    }
    
    /**
     * Create success message for inviter when someone uses their referral
     */
    public String createReferralSuccessMessage(int newCount, int requiredCount) {
        int remaining = Math.max(0, requiredCount - newCount);
        
        String message = String.format(
            "🎊 *Ajoyib xabar!*\n\n" +
            "Kimdir sizning referal havolangiz orqali qo'shildi!\n\n" +
            "👥 *Sizning referallaringiz:* %d/%d\n",
            newCount, requiredCount
        );
        
        if (remaining > 0) {
            message += String.format("\n⏳ Shaxsiy kanalni ochish uchun yana *%d ta* kerak!", remaining);
        } else {
            message += "\n✨ Siz kerakli referallarga erishdingiz!";
        }
        
        return message;
    }
    
    /**
     * Create private channel invite message
     */
    public String createPrivateChannelMessage(String inviteLink, int count) {
        return String.format(
            "🎉 *Tabriklaymiz!*\n\n" +
            "Siz muvaffaqiyatli %s ta do'stingizni taklif qildingiz va bizning maxsus shaxsiy kanalimizga kirish huquqiga ega bo'ldingiz!\n\n" +
            "🔓 *Qo'shilish uchun quyidagi havolani bosing:*\n%s\n\n" +
            "🌟 Hamjamiyatimizni o'stirishda yordam berish uchun do'stlaringizni taklif qilishda davom eting!",
            count,
            inviteLink
        );
    }
    
    /**
     * Create message when user uses referral link
     */
    public String createJoinedViaReferralMessage(String botUsername, String referralCode, int requiredCount) {
        String referralLink = String.format("https://t.me/%s?start=%s", botUsername, referralCode);
        
        return String.format(
            "🎉 *Xush kelibsiz!*\n\n" +
            "Siz referal havola orqali qo'shildingiz!\n\n" +
            "Endi o'zingizning referal havolangizni yarating va do'stlaringizni taklif qiling:\n\n" +
            "🔗 *Sizning referal havolangiz:*\n`%s`\n\n" +
            "Maxsus shaxsiy kanalni ochish uchun *%d ta do'stingizni* taklif qiling!\n\n" +
            "💡 Jarayoningizni tekshirish uchun /stats dan foydalaning.",
            referralLink, requiredCount
        );
    }
    
    /**
     * Create admin help message
     */
    public String createAdminHelpMessage() {
        return "👨‍💼 *Admin buyruqlari*\n\n" +
               "/admin_broadcast <xabar> - Barcha foydalanuvchilarga xabar yuborish\n" +
               "/admin_stats - Bot statistikasini ko'rish";
    }
    
    /**
     * Create admin stats message
     */
    public String createAdminStatsMessage(long totalUsers) {
        return String.format(
            "📈 *Bot statistikasi*\n\n" +
            "👥 *Jami foydalanuvchilar:* %d\n",
            totalUsers
        );
    }
    
    /**
     * Create channel subscription request message with buttons
     */
    public SendMessage createChannelSubscriptionMessage(Long chatId, List<com.muzaffar.telegram.entity.Channel> channels) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        
        StringBuilder text = new StringBuilder();
        text.append("Bizning loyihamizda ishtirok etish uchun bizning sahifalarimizga a'zo bo'ling.\n\n");
        text.append("*Keyin \"✅ A'zo bo'ldim\" tugmasini bosing!*\n\n");
        
        message.setText(text.toString());
        message.setParseMode("Markdown");
        
        // Create inline keyboard with channel buttons and confirmation button
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Add channel buttons
        for (com.muzaffar.telegram.entity.Channel channel : channels) {
            InlineKeyboardButton channelButton = new InlineKeyboardButton();
            channelButton.setText(channel.getChannelName());
            channelButton.setUrl("https://t.me/" + channel.getChannelUsername());
            
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(channelButton);
            rows.add(row);
        }
        
        // Add confirmation button
        InlineKeyboardButton confirmButton = new InlineKeyboardButton();
        confirmButton.setText("✅ A'zo bo'ldim");
        confirmButton.setCallbackData("verify_channels");
        
        List<InlineKeyboardButton> confirmRow = new ArrayList<>();
        confirmRow.add(confirmButton);
        rows.add(confirmRow);
        
        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);
        
        return message;
    }
    
    /**
     * Create SendMessage with Markdown formatting
     */
    public SendMessage createMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");
        return message;
    }
}
