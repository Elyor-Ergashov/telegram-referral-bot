package com.muzaffar.telegram.bot;

import static com.muzaffar.telegram.constants.BotConstants.*;

import com.muzaffar.telegram.config.AppConfig;
import com.muzaffar.telegram.config.BotConfig;
import com.muzaffar.telegram.entity.Challenge;
import com.muzaffar.telegram.entity.ChallengeChannel;
import com.muzaffar.telegram.entity.ChallengeUser;
import com.muzaffar.telegram.enums.MessageKey;
import com.muzaffar.telegram.service.ChallengeService;
import com.muzaffar.telegram.service.ChallengeUserService;
import com.muzaffar.telegram.service.InviteLinkService;
import com.muzaffar.telegram.service.MessageService;
import com.muzaffar.telegram.service.MessageTemplateService;
import com.muzaffar.telegram.service.UserSessionService;
import com.muzaffar.telegram.util.TelegramBotUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberLeft;
import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class ReferralTelegramBot extends TelegramLongPollingBot {
    
    private final BotConfig botConfig;
    private final AppConfig appConfig;
    private final MessageService messageService;
    private final MessageTemplateService messageTemplateService;
    private final TelegramBotUtils botUtils;
    private final ChallengeService challengeService;
    private final ChallengeUserService challengeUserService;
    private final InviteLinkService inviteLinkService;
    private final UserSessionService sessionService;
    
    public ReferralTelegramBot(BotConfig botConfig, AppConfig appConfig, 
                      MessageService messageService,
                      MessageTemplateService messageTemplateService,
                      TelegramBotUtils botUtils,
                      ChallengeService challengeService,
                      ChallengeUserService challengeUserService,
                      InviteLinkService inviteLinkService,
                      UserSessionService sessionService) {
        super(botConfig.getBot().getToken());
        this.botConfig = botConfig;
        this.appConfig = appConfig;
        this.botUtils = botUtils;
        this.messageService = messageService;
        this.messageTemplateService = messageTemplateService;
        this.challengeService = challengeService;
        this.challengeUserService = challengeUserService;
        this.inviteLinkService = inviteLinkService;
        this.sessionService = sessionService;
    }
    
    @Override
    public String getBotUsername() {
        return botConfig.getBot().getUsername();
    }
    
    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                Message message = update.getMessage();
                Long chatId = message.getChatId();
                String text = message.getText();
                
                log.info("Received message from chatId={}: {}", chatId, text);
                
                if (text.startsWith(START_COMMAND)) {
                    handleStartCommand(message);
                } else if (text.equals(STATS_COMMAND)) {
                    handleStatsCommand(message);
                } else {
                    // Silently delete unknown messages to keep chat clean
                    deleteMessage(chatId, message.getMessageId());
                }
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery());
            } else if (update.hasInlineQuery()) {
                handleInlineQuery(update.getInlineQuery());
            }
        } catch (Exception e) {
            log.error("Error processing update", e);
            if (update.hasMessage()) {
                sendMessage(update.getMessage().getChatId(), "❌ Xatolik yuz berdi. Iltimos, qaytadan urinib ko'ring.");
            }
        }
    }
    
    /**
     * Handle /start command (challenge-aware)
     */
    private void handleStartCommand(Message message) {
        Long chatId = message.getChatId();
        String username = message.getFrom().getUserName();
        String firstName = message.getFrom().getFirstName();
        String lastName = message.getFrom().getLastName();
        String text = message.getText();
        
        // Extract referral code if present (/start <referralCode>)
        String referralCode = null;
        if (text.length() > START_COMMAND_PREFIX_LENGTH) {
            referralCode = text.substring(START_COMMAND_PREFIX_LENGTH).trim();
        }
        
        // Determine which challenge to use
        Challenge challenge;
        if (referralCode != null && !referralCode.isEmpty()) {
            // User came via referral link - find which challenge the referrer belongs to
            ChallengeUser referrer = challengeUserService.getUserByReferralCode(referralCode).orElse(null);
            if (referrer != null) {
                // Fetch the full challenge object to avoid LazyInitializationException
                Long challengeId = referrer.getChallenge().getId();
                challenge = challengeService.getChallengeById(challengeId).orElse(null);
                if (challenge != null) {
                    log.info("User {} joining challenge {} via referral code {}", chatId, challenge.getName(), referralCode);
                } else {
                    log.warn("Challenge {} not found, using default", challengeId);
                    challenge = challengeService.getDefaultChallenge().orElse(null);
                }
            } else {
                // Invalid referral code, use default challenge
                challenge = challengeService.getDefaultChallenge().orElse(null);
                log.warn("Invalid referral code {} for user {}, using default challenge", referralCode, chatId);
            }
        } else {
            // No referral code, use default challenge
            challenge = challengeService.getDefaultChallenge().orElse(null);
        }
        
        if (challenge == null) {
            sendMessage(chatId, "❌ Hozircha faol challenge yo'q. Iltimos, keyinroq urinib ko'ring.");
            log.warn("No active challenge found for user {}", chatId);
            return;
        }
        
        // Make final copy for use in lambda
        final Challenge finalChallenge = challenge;
        
        // Get or create challenge user
        ChallengeUser challengeUser = challengeUserService.getUserInChallenge(finalChallenge.getId(), chatId)
                .orElseGet(() -> challengeUserService.addUserToChallenge(
                        finalChallenge.getId(), chatId, username, firstName, lastName));
        
        // Fetch active channels once for membership checks
        List<ChallengeChannel> activeChannels = challengeService.getActiveChannelsForChallenge(finalChallenge.getId());
        
        // If user previously verified, ensure they are still subscribed to all channels
        if (challengeUser.getHasVerifiedChannels() && !activeChannels.isEmpty()) {
            List<ChallengeChannel> missingChannels = getMissingChallengeChannels(chatId, activeChannels);
            if (!missingChannels.isEmpty()) {
                log.info("User {} left some required channels for challenge {}", chatId, finalChallenge.getName());
                challengeUserService.markUserAsUnverified(finalChallenge.getId(), chatId);
                
                if (referralCode != null && !referralCode.isEmpty()) {
                    sessionService.setPendingReferral(chatId, referralCode);
                }
                
                notifyUserMissingChannels(chatId, missingChannels);
                
                try {
                    SendMessage channelMessage = createChallengeChannelSubscriptionMessage(chatId, activeChannels);
                    execute(channelMessage);
                } catch (TelegramApiException e) {
                    log.error("Error resending channel subscription message after detecting missing channels", e);
                }
                return;
            }
        }
        
        // Check if user has verified channels
        if (!challengeUser.getHasVerifiedChannels()) {
            // Store referral code for later processing (after channel verification)
            if (referralCode != null && !referralCode.isEmpty()) {
                sessionService.setPendingReferral(chatId, referralCode);
                log.info("Stored referral code for user {} to process after channel verification", chatId);
            }
            
            // Show channel subscription request (from challenge's channels)
            if (!activeChannels.isEmpty()) {
                try {
                    SendMessage channelMessage = createChallengeChannelSubscriptionMessage(chatId, activeChannels);
                    execute(channelMessage);
                    return;
                } catch (TelegramApiException e) {
                    log.error("Error sending channel subscription message", e);
                }
            } else {
                // No channels configured, mark as verified
                challengeUserService.markUserAsVerified(finalChallenge.getId(), chatId);
                challengeUser = challengeUserService.getUserInChallenge(finalChallenge.getId(), chatId).get();
            }
        }
        
        // Check if this is a new user with a referral code and process it
        if (referralCode != null && !referralCode.isEmpty()) {
            ChallengeUser referrer = challengeUserService.getUserByReferralCode(referralCode).orElse(null);
            
            if (referrer != null && referrer.getChallenge().getId().equals(finalChallenge.getId())) {
                // Add referral record - only proceed if successfully added
                boolean referralAdded = challengeUserService.addReferral(finalChallenge.getId(), referrer.getUserChatId(), chatId);
                
                // Validate referral and notify only if referral was successfully added
                if (referralAdded && challengeUser.getHasVerifiedChannels()) {
                    challengeUserService.validateReferral(finalChallenge.getId(), referrer.getUserChatId(), chatId);
                    
                    // Notify inviter
                    try {
                        long validReferrals = challengeUserService.getValidReferralCount(finalChallenge.getId(), referrer.getUserChatId());
                        SendMessage successMessage = messageService.createReferralSuccessMessage(
                            referrer.getUserChatId(),
                            (int) validReferrals,
                            finalChallenge.getRequiredReferralCount(),
                            firstName,
                            chatId
                        );
                        execute(successMessage);
                        
                        // Check if referrer now qualifies for private channel link (after this new referral)
                        // Refresh referrer data from database to get latest isCompleted status
                        ChallengeUser updatedReferrer = challengeUserService.getUserInChallenge(
                            finalChallenge.getId(), referrer.getUserChatId()).orElse(referrer);
                        
                        if ((updatedReferrer.getIsCompleted() || validReferrals >= finalChallenge.getRequiredReferralCount()) 
                                && !updatedReferrer.getHasReceivedReward()) {
                            
                            log.info("Referrer {} now has {} valid referrals (required: {}), checking channel subscriptions before sending link", 
                                referrer.getUserChatId(), validReferrals, finalChallenge.getRequiredReferralCount());
                            
                            // Verify all referred users are still in channels before sending reward
                            ReferralVerificationResult verification = verifyReferredUsersWithCounts(finalChallenge, referrer.getUserChatId());
                            
                            if (verification.allStillInChannels) {
                                // Small delay before sending private channel link
                                Thread.sleep(500);
                                
                                // Generate one-time invite link for the referrer
                                String inviteLink = inviteLinkService.createOneTimeInviteLink(
                                    this, finalChallenge.getId(), referrer.getUserChatId());
                                
                                if (inviteLink != null) {
                                    String linkMessage = messageService.createPrivateChannelMessage(
                                        inviteLink, 
                                        finalChallenge.getRequiredReferralCount()
                                    );
                                    sendMessage(referrer.getUserChatId(), linkMessage);
                                    challengeUserService.markRewardSent(finalChallenge.getId(), referrer.getUserChatId());
                                    
                                    log.info("Automatically sent one-time private channel link to referrer {} after reaching {} referrals", 
                                        referrer.getUserChatId(), validReferrals);
                                } else {
                                    log.error("Failed to generate invite link for referrer {}", referrer.getUserChatId());
                                    sendMessage(referrer.getUserChatId(), "❌ Xatolik yuz berdi. Iltimos, keyinroq qayta urinib ko'ring.");
                                }
                            } else {
                                log.warn("Some referred users left channels, not sending private link to referrer {}", 
                                    referrer.getUserChatId());
                                
                                // Calculate indirect referrals for referrer
                                long referrerIndirectReferrals = challengeUserService.getIndirectReferralCount(
                                    finalChallenge.getId(), referrer.getUserChatId());
                                
                                SendMessage insufficientMessage = messageService.createInsufficientActiveReferralsMessage(
                                    referrer.getUserChatId(),
                                    verification.totalReferrals,  // totalReferralCount
                                    (int) referrerIndirectReferrals,  // indirectReferralCount
                                    verification.activeReferrals,  // activeCount (users still in channels)
                                    verification.leftChannels,  // leftCount (users who left)
                                    finalChallenge.getRequiredReferralCount()  // requiredCount
                                );
                                execute(insufficientMessage);
                            }
                        }
                    } catch (TelegramApiException e) {
                        log.error("Error sending referral success message", e);
                    } catch (InterruptedException e) {
                        log.error("Thread interrupted", e);
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        
        // Send information messages in sequence (same for referral and direct users)
        sendInformationSequence(finalChallenge, chatId, firstName);
    }
    
    /**
     * Create channel subscription message for challenge channels
     */
    private SendMessage createChallengeChannelSubscriptionMessage(Long chatId, List<ChallengeChannel> channels) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setParseMode("Markdown");
        message.setDisableWebPagePreview(true);  // Disable preview to prevent channel cards
        
        StringBuilder text = new StringBuilder();
        text.append("📺 *Quyidagi kanallarga a'zo bo'ling:*\n\n");
        text.append("Keyin \"✅ A'zo bo'ldim\" tugmasini bosing!");
        message.setText(text.toString());
        
        // Create keyboard with channel buttons + verify button
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Add a button for each channel
        for (ChallengeChannel channel : channels) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton channelButton = new InlineKeyboardButton();
            channelButton.setText(channel.getChannelName());
            channelButton.setUrl("https://t.me/" + channel.getChannelUsername());
            row.add(channelButton);
            rows.add(row);
        }
        
        // Add verify button at the bottom
        List<InlineKeyboardButton> verifyRow = new ArrayList<>();
        InlineKeyboardButton verifyButton = new InlineKeyboardButton();
        verifyButton.setText("✅ A'zo bo'ldim");
        verifyButton.setCallbackData(CALLBACK_VERIFY_CHANNELS);
        verifyRow.add(verifyButton);
        rows.add(verifyRow);
        
        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);
        
        return message;
    }
    
    // Removed: checkAndSendPrivateChannelLinkForChallenge - private channel link now only sent via "Takliflarim" button
    
    /**
     * Handle /stats command (challenge-aware)
     */
    private void handleStatsCommand(Message message) {
        Long chatId = message.getChatId();
        
        // Get default challenge
        Challenge challenge = challengeService.getDefaultChallenge().orElse(null);
        if (challenge == null) {
            sendMessage(chatId, "❌ Hozircha faol challenge yo'q.");
            return;
        }
        
        // Get user from challenge
        ChallengeUser challengeUser = challengeUserService.getUserInChallenge(challenge.getId(), chatId).orElse(null);
        if (challengeUser == null) {
            sendMessage(chatId, "❌ Iltimos, avval /start buyrug'ini yuboring.");
            return;
        }
        
        String statsMessage = messageService.createStatsMessage(
            getBotUsername(),
            challengeUser.getReferralCode(),
            challengeUser.getReferralCount(),
            challenge.getRequiredReferralCount()
        );
        sendMessage(chatId, statsMessage);
    }
    
    // Admin commands removed - handled by AdminTelegramBot
    
    /**
     * Handle callback queries (button presses)
     */
    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        
        log.info("Received callback query from chatId={}: {}", chatId, data);
        
        if (CALLBACK_START_BOT.equals(data)) {
            handleStartButton(chatId, messageId, callbackQuery.getFrom().getUserName(),
                callbackQuery.getFrom().getFirstName(), callbackQuery.getFrom().getLastName());
        } else if (CALLBACK_VERIFY_CHANNELS.equals(data)) {
            handleChannelVerification(chatId, messageId, callbackQuery.getFrom().getUserName(),
                callbackQuery.getFrom().getFirstName(), callbackQuery.getFrom().getLastName());
        } else if (CALLBACK_MY_REFERRALS.equals(data)) {
            handleMyReferrals(chatId, callbackQuery.getFrom().getUserName(),
                callbackQuery.getFrom().getFirstName(), callbackQuery.getFrom().getLastName());
        }
    }
    
    /**
     * Send information message sequence: greeting -> main message (with Takliflarim button)
     * If an image exists for information message, sends it as photo with caption
     */
    private void sendInformationSequence(Challenge challenge, Long chatId, String firstName) {
        try {
            // Store the challenge context for this user
            sessionService.setChallengeContext(chatId, challenge.getId());
            
            // 1. Send greeting message first
            SendMessage greetingMessage = messageService.createGreetingMessage(chatId, firstName);
            execute(greetingMessage);
            
            // Small delay for better UX
            Thread.sleep(INFORMATION_SEQUENCE_DELAY_MS);
            
            // 2. Send main information message with Takliflarim button (with image if available)
            messageService.sendInformationMessageWithImage(this, challenge.getId(), chatId);
            
        } catch (TelegramApiException | InterruptedException e) {
            log.error("Error sending information sequence", e);
        }
    }
    
    /**
     * Handle START button press - show channel subscription
     */
    private void handleStartButton(Long chatId, Integer messageId, String username, String firstName, String lastName) {
        // Determine which challenge to use
        // Priority: 1) userChallengeContext (from broadcast/Takliflarim click)
        //          2) pendingReferrals (from referral link)
        //          3) default challenge
        Challenge challenge = null;
        
        // First check if user has a challenge context (from broadcast or Takliflarim)
        Optional<Long> challengeIdOpt = sessionService.getChallengeContext(chatId);
        if (challengeIdOpt.isPresent()) {
            challenge = challengeService.getChallengeById(challengeIdOpt.get()).orElse(null);
            if (challenge != null) {
                log.info("User {} joining challenge {} via START button from context", chatId, challenge.getName());
            }
        }
        
        // If no context, check for referral code
        if (challenge == null) {
            Optional<String> referralCodeOpt = sessionService.getPendingReferral(chatId);
            if (referralCodeOpt.isPresent()) {
                String referralCode = referralCodeOpt.get();
                ChallengeUser referrer = challengeUserService.getUserByReferralCode(referralCode).orElse(null);
                if (referrer != null) {
                    Long refChallengeId = referrer.getChallenge().getId();
                    challenge = challengeService.getChallengeById(refChallengeId).orElse(null);
                    if (challenge != null) {
                        log.info("User {} joining challenge {} via START button with referral code {}", chatId, challenge.getName(), referralCode);
                    }
                }
            }
        }
        
        // Final fallback to default challenge
        if (challenge == null) {
            challenge = challengeService.getDefaultChallenge().orElse(null);
        }
        
        if (challenge == null) {
            sendMessage(chatId, "❌ Hozircha faol challenge yo'q.");
            return;
        }
        
        // Make final copy for use in lambda
        final Challenge finalChallenge = challenge;
        
        // Get or create challenge user
        ChallengeUser challengeUser = challengeUserService.getUserInChallenge(finalChallenge.getId(), chatId)
                .orElseGet(() -> challengeUserService.addUserToChallenge(
                        finalChallenge.getId(), chatId, username, firstName, lastName));
        
        // Check if user has already verified channels
        if (challengeUser.getHasVerifiedChannels()) {
            // User already verified, show information sequence
            sendInformationSequence(finalChallenge, chatId, firstName);
            return;
        }
        
        // Delete the intro message
        deleteMessage(chatId, messageId);
        
        // Show channel subscription request from challenge
        List<ChallengeChannel> activeChannels = challengeService.getActiveChannelsForChallenge(finalChallenge.getId());
        if (!activeChannels.isEmpty()) {
            try {
                SendMessage channelMessage = createChallengeChannelSubscriptionMessage(chatId, activeChannels);
                execute(channelMessage);
            } catch (TelegramApiException e) {
                log.error("Error sending channel subscription message", e);
            }
        } else {
            // No channels configured, mark as verified and show information
            challengeUserService.markUserAsVerified(finalChallenge.getId(), chatId);
            sendInformationMessageAfterVerification(chatId, username, firstName, lastName);
        }
    }
    
    /**
     * Handle "My Referrals" button press - show stats with share button
     */
    private void handleMyReferrals(Long chatId, String username, String firstName, String lastName) {
        // Get challenge from user context (which information message they received)
        Optional<Long> challengeIdOpt = sessionService.getChallengeContext(chatId);
        
        Challenge challenge;
        if (challengeIdOpt.isPresent()) {
            // User has a challenge context - use it
            challenge = challengeService.getChallengeById(challengeIdOpt.get()).orElse(null);
            if (challenge == null) {
                // Challenge no longer exists, fall back to default
                challenge = challengeService.getDefaultChallenge().orElse(null);
            }
        } else {
            // No context, use default challenge
            challenge = challengeService.getDefaultChallenge().orElse(null);
        }
        
        if (challenge == null) {
            sendMessage(chatId, "❌ Hozircha faol challenge yo'q.");
            return;
        }
        
        // Get or create challenge user
        ChallengeUser challengeUser = challengeUserService.getUserInChallenge(challenge.getId(), chatId).orElse(null);
        
        if (challengeUser == null) {
            // User not in this challenge yet - start the registration process
            log.info("User {} not in challenge {}, starting registration process", chatId, challenge.getName());
            
            // Add user to challenge
            challengeUser = challengeUserService.addUserToChallenge(
                challenge.getId(), chatId, username, firstName, lastName);
            
            // Check if user needs to verify channels
            if (!challengeUser.getHasVerifiedChannels()) {
                // Show channel subscription requirement
                List<ChallengeChannel> activeChannels = challengeService.getActiveChannelsForChallenge(challenge.getId());
                
                if (activeChannels.isEmpty()) {
                    // No channels to verify, mark as verified and show stats
                    challengeUserService.markUserAsVerified(challenge.getId(), chatId);
                } else {
                    // Show channels to join
                    SendMessage channelMessage = messageService.createChannelSubscriptionMessage(chatId, activeChannels);
                    try {
                        execute(channelMessage);
                        return; // Exit after showing channels
                    } catch (TelegramApiException e) {
                        log.error("Error sending channel subscription message", e);
                        sendMessage(chatId, "❌ Xatolik yuz berdi. Iltimos, qaytadan urinib ko'ring.");
                        return;
                    }
                }
            }
        }
        
        try {
            // Get valid referral count
            long validReferrals = challengeUserService.getValidReferralCount(challenge.getId(), chatId);
            
            // Refresh challengeUser from database to get latest isCompleted status
            challengeUser = challengeUserService.getUserInChallenge(challenge.getId(), chatId).orElse(challengeUser);
            
            log.info("User {} stats: validReferrals={}, isCompleted={}, hasReceivedReward={}, required={}", 
                chatId, validReferrals, challengeUser.getIsCompleted(), 
                challengeUser.getHasReceivedReward(), challenge.getRequiredReferralCount());
            
            // Send optional stats header image first
            messageTemplateService.getImagePath(challenge.getId(), MessageKey.STATS_HEADER).ifPresent(imagePath -> {
                try {
                    sendPhotoFromFile(chatId, imagePath);
                } catch (Exception e) {
                    log.error("Error sending stats header image", e);
                }
            });
            
            // Calculate indirect referrals (referrals made by user's direct referrals)
            long indirectReferrals = challengeUserService.getIndirectReferralCount(challenge.getId(), chatId);
            
            SendMessage statsMessage = messageService.createStatsMessageWithButton(
                challenge.getId(),
                chatId,
                getBotUsername(),
                challengeUser.getReferralCode(),
                (int) validReferrals,
                (int) indirectReferrals,
                (int) validReferrals,
                0,  // No left count tracked
                challenge.getRequiredReferralCount()
            );
            execute(statsMessage);
            
            // Small delay for better UX
            Thread.sleep(500);
            
            // Check if user qualifies for private channel link
            // Use isCompleted flag which is set when user reaches required referral count
            if (challengeUser.getIsCompleted() || validReferrals >= challenge.getRequiredReferralCount()) {
                if (!challengeUser.getHasReceivedReward()) {
                    // Verify all referred users are still in channels before sending reward
                    ReferralVerificationResult verification = verifyReferredUsersWithCounts(challenge, chatId);
                    
                    if (verification.allStillInChannels && verification.activeReferrals >= challenge.getRequiredReferralCount()) {
                        // User has enough referrals and all are still active - generate one-time invite link
                        String inviteLink = inviteLinkService.createOneTimeInviteLink(
                            this, challenge.getId(), chatId);
                        
                        if (inviteLink != null) {
                            String linkMessage = messageService.createPrivateChannelMessage(
                                inviteLink, 
                                challenge.getRequiredReferralCount()
                            );
                            sendMessage(chatId, linkMessage);
                            challengeUserService.markRewardSent(challenge.getId(), chatId);
                            log.info("User {} received one-time private channel link for challenge {} with {} active referrals", 
                                chatId, challenge.getId(), verification.activeReferrals);
                        } else {
                            log.error("Failed to generate invite link for user {}", chatId);
                            sendMessage(chatId, "❌ Xatolik yuz berdi. Iltimos, keyinroq qayta urinib ko'ring.");
                        }
                    } else {
                        // Some referred users left channels or not enough active referrals
                        log.warn("User {} has {} total referrals but only {} active (left: {})", 
                            chatId, verification.totalReferrals, verification.activeReferrals, verification.leftChannels);
                        
                        SendMessage insufficientMessage = messageService.createInsufficientActiveReferralsMessage(
                            chatId,
                            verification.totalReferrals,  // totalReferralCount
                            (int) indirectReferrals,  // indirectReferralCount (already calculated above)
                            verification.activeReferrals,  // activeCount (users still in channels)
                            verification.leftChannels,  // leftCount (users who left)
                            challenge.getRequiredReferralCount()  // requiredCount
                        );
                        execute(insufficientMessage);
                    }
                } else {
                    // User already received the reward
                    sendMessage(chatId, "✅ Siz allaqachon shaxsiy kanal havolasini olgansiz!");
                }
            } else {
                // User doesn't have enough referrals yet
                int needed = challenge.getRequiredReferralCount() - (int) validReferrals;
//                sendMessage(chatId, String.format(
//                    "📊 Shaxsiy kanalga kirish uchun yana *%d* ta do'stingizni taklif qilishingiz kerak!\n\n" +
//                    "💡 Havola orqali taklif qiling va do'stlaringiz barcha kanallarga obuna bo'lganidan so'ng, " +
//                    "ular sizning hisobingizga qo'shiladi.",
//                    needed
//                ));
            }
        } catch (TelegramApiException e) {
            log.error("Error sending stats message", e);
        } catch (InterruptedException e) {
            log.error("Thread interrupted while sending stats", e);
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Handle inline query for sharing referral links
     */
    private void handleInlineQuery(InlineQuery inlineQuery) {
        Long userId = inlineQuery.getFrom().getId();
        String queryId = inlineQuery.getId();
        String query = inlineQuery.getQuery();
        
        log.info("Received inline query from userId={}: {}", userId, query);
        
        try {
            // Get main challenge and user
            Challenge challenge = challengeService.getDefaultChallenge().orElse(null);
            if (challenge == null) {
                AnswerInlineQuery answer = new AnswerInlineQuery();
                answer.setInlineQueryId(queryId);
                answer.setResults(List.of());
                execute(answer);
                return;
            }
            
            ChallengeUser challengeUser = challengeUserService.getUserInChallenge(challenge.getId(), userId).orElse(null);
            if (challengeUser == null) {
                AnswerInlineQuery answer = new AnswerInlineQuery();
                answer.setInlineQueryId(queryId);
                answer.setResults(List.of());
                execute(answer);
                return;
            }
            
            String referralLink = String.format("https://t.me/%s?start=%s", getBotUsername(), challengeUser.getReferralCode());
            
            // Create inline query result article
            InlineQueryResultArticle article = new InlineQueryResultArticle();
            article.setId("referral_share");
            article.setTitle("🎉 Botga taklif qiling!");
            article.setDescription("Do'stlaringizni botga taklif qiling va bonuslar oling");
            
            // The message content that will be sent
            InputTextMessageContent messageContent = new InputTextMessageContent();
            
            // Use the query text if provided, otherwise use default message
            String shareText;
            if (query != null && !query.trim().isEmpty()) {
                shareText = query;
            } else {
                shareText = String.format(
                    "Assalomu alaykum! Yopiq kanalga bepul kirish uchun shu botga kiring 👉\n%s",
                    referralLink
                );
            }
            
            messageContent.setMessageText(shareText);
            messageContent.setDisableWebPagePreview(false); // Enable preview
            article.setInputMessageContent(messageContent);
            
            // Answer the inline query
            AnswerInlineQuery answer = new AnswerInlineQuery();
            answer.setInlineQueryId(queryId);
            answer.setResults(List.of(article));
            answer.setCacheTime(300); // Cache for 5 minutes
            
            execute(answer);
            log.info("Answered inline query for userId={}", userId);
            
        } catch (TelegramApiException e) {
            log.error("Error handling inline query", e);
        }
    }
    
    /**
     * Handle channel verification
     */
    private void handleChannelVerification(Long chatId, Integer messageId, String username, String firstName, String lastName) {
        // Determine which challenge to use
        // Priority: 1) userChallengeContext (from broadcast/Takliflarim click)
        //          2) pendingReferrals (from referral link)
        //          3) default challenge
        Challenge challenge = null;
        
        // First check if user has a challenge context (from broadcast or Takliflarim)
        Optional<Long> challengeIdOpt = sessionService.getChallengeContext(chatId);
        if (challengeIdOpt.isPresent()) {
            challenge = challengeService.getChallengeById(challengeIdOpt.get()).orElse(null);
            if (challenge != null) {
                log.info("User {} verifying channels for challenge {} from context", chatId, challenge.getName());
            }
        }
        
        // If no context, check for referral code
        if (challenge == null) {
            Optional<String> referralCodeOpt = sessionService.getPendingReferral(chatId);
            if (referralCodeOpt.isPresent()) {
                String referralCode = referralCodeOpt.get();
                ChallengeUser referrer = challengeUserService.getUserByReferralCode(referralCode).orElse(null);
                if (referrer != null) {
                    // Fetch the full challenge object to avoid LazyInitializationException
                    Long refChallengeId = referrer.getChallenge().getId();
                    challenge = challengeService.getChallengeById(refChallengeId).orElse(null);
                    if (challenge != null) {
                        log.info("User {} verifying channels for challenge {} via referral code {}", chatId, challenge.getName(), referralCode);
                    }
                }
            }
        }
        
        // Final fallback to default challenge
        if (challenge == null) {
            challenge = challengeService.getDefaultChallenge().orElse(null);
        }
        
        if (challenge == null) {
            editMessage(chatId, messageId, "❌ Hozircha faol challenge yo'q.");
            return;
        }
        
        // Get challenge channels
        List<ChallengeChannel> activeChannels = challengeService.getActiveChannelsForChallenge(challenge.getId());
        
        if (activeChannels.isEmpty()) {
            editMessage(chatId, messageId, "✅ Verification complete!");
            challengeUserService.markUserAsVerified(challenge.getId(), chatId);
            sendInformationMessageAfterVerification(chatId, username, firstName, lastName);
            return;
        }
        
        // Check if user is member of all channels
        boolean allSubscribed = true;
        StringBuilder notSubscribedChannels = new StringBuilder();
        
        for (ChallengeChannel channel : activeChannels) {
            if (!isUserMemberOfChallengeChannel(chatId, channel)) {
                allSubscribed = false;
                notSubscribedChannels.append("• ").append(channel.getChannelName()).append("\n");
            }
        }
        
        if (allSubscribed) {
            // User is subscribed to all channels
            editMessage(chatId, messageId, "✅ Barcha kanallarga obuna bo'lganingiz tasdiqlandi!");
            
            // Mark user as verified
            challengeUserService.markUserAsVerified(challenge.getId(), chatId);
            
            // Send information message
            sendInformationMessageAfterVerification(chatId, username, firstName, lastName);
        } else {
            // User is not subscribed to all channels
            String errorMessage = "❌ Iltimos, avval quyidagi kanallarga obuna bo'ling:\n\n" + notSubscribedChannels.toString();
            editMessage(chatId, messageId, errorMessage);
            
            // Resend subscription buttons
            try {
                Thread.sleep(RETRY_DELAY_MS);
                SendMessage channelMessage = createChallengeChannelSubscriptionMessage(chatId, activeChannels);
                execute(channelMessage);
            } catch (Exception e) {
                log.error("Error resending channel subscription message", e);
            }
        }
    }
    
    /**
     * Check if user is member of a challenge channel
     * Tries both numeric ID and @username format
     */
    private boolean isUserMemberOfChallengeChannel(Long userId, ChallengeChannel channel) {
        // Try with numeric channel ID first
        ChatMember chatMember = tryGetChatMember(userId, channel.getChannelId());
        
        // If failed and we have a username, try with @username format
        if (chatMember == null && channel.getChannelUsername() != null && !channel.getChannelUsername().isEmpty()) {
            log.info("Retrying with @username format for channel: {}", channel.getChannelName());
            String usernameFormat = "@" + channel.getChannelUsername().replace("@", "");
            chatMember = tryGetChatMember(userId, usernameFormat);
        }
        
        if (chatMember != null) {
            String status = chatMember.getStatus();
            // Check if user is a member (not "left" or "kicked")
            boolean isMember = !"left".equals(status) && !"kicked".equals(status);
            log.info("User {} membership in channel {} ({}): {}", userId, channel.getChannelName(), status, isMember);
            return isMember;
        }
        
        log.error("⚠️ CONFIGURATION ERROR: Bot cannot access channel {} (ID: {}, Username: @{}). " +
            "The bot must be added as an ADMINISTRATOR to the channel! " +
            "Please add the bot as admin in the channel settings.", 
            channel.getChannelName(), channel.getChannelId(), channel.getChannelUsername());
        
        return false;
    }
    
    /**
     * Get list of channels the user is NOT a member of
     */
    private List<ChallengeChannel> getMissingChallengeChannels(Long userId, List<ChallengeChannel> channels) {
        List<ChallengeChannel> missing = new ArrayList<>();
        for (ChallengeChannel channel : channels) {
            if (!isUserMemberOfChallengeChannel(userId, channel)) {
                missing.add(channel);
            }
        }
        return missing;
    }
    
    /**
     * Notify user about missing channel subscriptions
     */
    private void notifyUserMissingChannels(Long chatId, List<ChallengeChannel> missingChannels) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ *Siz quyidagi kanallardan chiqib ketgansiz:*\n\n");
        for (ChallengeChannel channel : missingChannels) {
            sb.append("• ").append(channel.getChannelName()).append("\n");
        }
        sb.append("\n_Iltimos, yana obuna bo'ling va \"✅ A'zo bo'ldim\" tugmasini bosing._");
        sendMessage(chatId, sb.toString());
    }
    
    /**
     * Try to get chat member with specific chat identifier
     * Returns ChatMember if found, or a marker object if user not subscribed
     */
    private ChatMember tryGetChatMember(Long userId, String chatId) {
        try {
            GetChatMember getChatMember = new GetChatMember();
            getChatMember.setChatId(chatId);
            getChatMember.setUserId(userId);
            
            ChatMember chatMember = execute(getChatMember);
            log.debug("Successfully checked membership for userId={} in chatId={}", userId, chatId);
            return chatMember;
        } catch (TelegramApiException e) {
            String errorMsg = e.getMessage();
            
            if (errorMsg != null && errorMsg.contains("chat not found")) {
                // Bot cannot access this chat - needs to be admin
                log.warn("Chat not found with identifier '{}' - bot may not be admin or ID is incorrect", chatId);
                return null;
            } else if (errorMsg != null && (errorMsg.contains("USER_NOT_PARTICIPANT") || errorMsg.contains("user not found"))) {
                // User is definitely not a member - this is valid information
                log.debug("User {} is not a member of {}", userId, chatId);
                // Create a ChatMemberLeft to indicate user is not subscribed
                // ChatMemberLeft automatically has status "left"
                ChatMemberLeft leftMember = new ChatMemberLeft();
                org.telegram.telegrambots.meta.api.objects.User user = new org.telegram.telegrambots.meta.api.objects.User();
                user.setId(userId);
                user.setFirstName("");
                user.setIsBot(false);
                leftMember.setUser(user);
                // No need to set status - it's automatically "left" for ChatMemberLeft
                return leftMember;
            } else if (errorMsg != null && errorMsg.contains("Bad Request")) {
                log.warn("Bad request error for chatId '{}': {}", chatId, errorMsg);
                return null;
            } else {
                log.debug("Unexpected error checking membership for userId={}, chatId={}: {}", userId, chatId, errorMsg);
                return null;
            }
        }
    }
    
    /**
     * Send information message after channel verification
     * Also processes pending referrals
     */
    private void sendInformationMessageAfterVerification(Long chatId, String username, String firstName, String lastName) {
        // Determine which challenge to use
        // Priority: 1) userChallengeContext (from broadcast/Takliflarim click)
        //          2) pendingReferrals (from referral link)
        //          3) default challenge
        Challenge challenge = null;
        Optional<String> referralCodeOpt = sessionService.getPendingReferral(chatId); // Declare at method level for later use
        String referralCode = referralCodeOpt.orElse(null);
        
        // First check if user has a challenge context (from broadcast or Takliflarim)
        Optional<Long> challengeIdOpt = sessionService.getChallengeContext(chatId);
        if (challengeIdOpt.isPresent()) {
            challenge = challengeService.getChallengeById(challengeIdOpt.get()).orElse(null);
            if (challenge != null) {
                log.info("User {} sending information after verification for challenge {} from context", chatId, challenge.getName());
            }
        }
        
        // If no context, check for referral code
        if (challenge == null && referralCode != null && !referralCode.isEmpty()) {
            ChallengeUser referrer = challengeUserService.getUserByReferralCode(referralCode).orElse(null);
            if (referrer != null) {
                // Fetch the full challenge object to avoid LazyInitializationException
                Long refChallengeId = referrer.getChallenge().getId();
                challenge = challengeService.getChallengeById(refChallengeId).orElse(null);
                if (challenge != null) {
                    log.info("User {} sending information after verification for challenge {} via referral code {}", chatId, challenge.getName(), referralCode);
                }
            }
        }
        
        // Final fallback to default challenge
        if (challenge == null) {
            challenge = challengeService.getDefaultChallenge().orElse(null);
        }
        
        if (challenge == null) {
            sendMessage(chatId, "❌ Hozircha faol challenge yo'q.");
            return;
        }
        
        // Process pending referral if exists
        if (referralCode != null && !referralCode.isEmpty()) {
            log.info("Processing pending referral for user {} with code {}", chatId, referralCode);
            ChallengeUser referrer = challengeUserService.getUserByReferralCode(referralCode).orElse(null);
            
            if (referrer != null && referrer.getChallenge().getId().equals(challenge.getId())) {
                // Add referral
                challengeUserService.addReferral(challenge.getId(), referrer.getUserChatId(), chatId);
                // Validate immediately since user has verified channels
                challengeUserService.validateReferral(challenge.getId(), referrer.getUserChatId(), chatId);
                sessionService.clearPendingReferral(chatId);
                
                // Notify referrer
                try {
                    long validReferrals = challengeUserService.getValidReferralCount(challenge.getId(), referrer.getUserChatId());
                    SendMessage successMessage = messageService.createReferralSuccessMessage(
                        referrer.getUserChatId(),
                        (int) validReferrals,
                        challenge.getRequiredReferralCount(),
                        firstName,
                        chatId
                    );
                    execute(successMessage);
                    
                    // Check if referrer now qualifies for private channel link (after this new referral)
                    // Refresh referrer data from database to get latest isCompleted status
                    ChallengeUser updatedReferrer = challengeUserService.getUserInChallenge(
                        challenge.getId(), referrer.getUserChatId()).orElse(referrer);
                    
                    if ((updatedReferrer.getIsCompleted() || validReferrals >= challenge.getRequiredReferralCount()) 
                            && !updatedReferrer.getHasReceivedReward()) {
                        
                        log.info("Referrer {} now has {} valid referrals (required: {}), checking channel subscriptions before sending link", 
                            referrer.getUserChatId(), validReferrals, challenge.getRequiredReferralCount());
                        
                        // Verify all referred users are still in channels before sending reward
                        ReferralVerificationResult verification = verifyReferredUsersWithCounts(challenge, referrer.getUserChatId());
                        
                        if (verification.allStillInChannels) {
                            // Small delay before sending private channel link
                            Thread.sleep(500);
                            
                            // Generate one-time invite link for the referrer
                            String inviteLink = inviteLinkService.createOneTimeInviteLink(
                                this, challenge.getId(), referrer.getUserChatId());
                            
                            if (inviteLink != null) {
                                String linkMessage = messageService.createPrivateChannelMessage(
                                    inviteLink, 
                                    challenge.getRequiredReferralCount()
                                );
                                sendMessage(referrer.getUserChatId(), linkMessage);
                                challengeUserService.markRewardSent(challenge.getId(), referrer.getUserChatId());
                                
                                log.info("Automatically sent one-time private channel link to referrer {} after reaching {} referrals", 
                                    referrer.getUserChatId(), validReferrals);
                            } else {
                                log.error("Failed to generate invite link for referrer {}", referrer.getUserChatId());
                                sendMessage(referrer.getUserChatId(), "❌ Xatolik yuz berdi. Iltimos, keyinroq qayta urinib ko'ring.");
                            }
                        } else {
                            log.warn("Some referred users left channels, not sending private link to referrer {}", 
                                referrer.getUserChatId());
                            
                            // Calculate indirect referrals for referrer
                            long referrerIndirectReferrals = challengeUserService.getIndirectReferralCount(
                                challenge.getId(), referrer.getUserChatId());
                            
                            SendMessage insufficientMessage = messageService.createInsufficientActiveReferralsMessage(
                                referrer.getUserChatId(),
                                verification.totalReferrals,  // totalReferralCount
                                (int) referrerIndirectReferrals,  // indirectReferralCount
                                verification.activeReferrals,  // activeCount (users still in channels)
                                verification.leftChannels,  // leftCount (users who left)
                                challenge.getRequiredReferralCount()  // requiredCount
                            );
                            execute(insufficientMessage);
                        }
                    }
                } catch (TelegramApiException e) {
                    log.error("Error sending referral success message", e);
                } catch (InterruptedException e) {
                    log.error("Thread interrupted", e);
                    Thread.currentThread().interrupt();
                }
            } else {
                // Referral failed, remove from pending
                sessionService.clearPendingReferral(chatId);
            }
        }
        
        // Send the same information sequence for everyone (referral or direct)
        sendInformationSequence(challenge, chatId, firstName);
    }
    
    /**
     * Result of verifying referred users' channel subscriptions
     */
    private static class ReferralVerificationResult {
        final boolean allStillInChannels;
        final int totalReferrals;
        final int activeReferrals;
        final int leftChannels;
        
        ReferralVerificationResult(boolean allStillInChannels, int totalReferrals, int activeReferrals, int leftChannels) {
            this.allStillInChannels = allStillInChannels;
            this.totalReferrals = totalReferrals;
            this.activeReferrals = activeReferrals;
            this.leftChannels = leftChannels;
        }
    }
    
    /**
     * Verify that all referred users are still subscribed to all challenge channels
     */
    private boolean verifyReferredUsersStillInChannels(Challenge challenge, Long referrerChatId) {
        ReferralVerificationResult result = verifyReferredUsersWithCounts(challenge, referrerChatId);
        return result.allStillInChannels;
    }
    
    /**
     * Verify referred users and return detailed counts
     */
    private ReferralVerificationResult verifyReferredUsersWithCounts(Challenge challenge, Long referrerChatId) {
        try {
            // Get all valid referred user chat IDs
            List<Long> referredUserIds = challengeUserService.getValidReferredUserIds(
                challenge.getId(), referrerChatId);
            
            int totalReferrals = referredUserIds.size();
            
            if (referredUserIds.isEmpty()) {
                log.warn("No valid referred users found for referrer {}", referrerChatId);
                return new ReferralVerificationResult(false, 0, 0, 0);
            }
            
            // Get challenge channels
            List<ChallengeChannel> channels = challengeService.getActiveChannelsForChallenge(challenge.getId());
            
            if (channels.isEmpty()) {
                // No channels to verify
                return new ReferralVerificationResult(true, totalReferrals, totalReferrals, 0);
            }
            
            // Check each referred user is still in all channels
            int activeCount = 0;
            int leftCount = 0;
            
            for (Long referredUserId : referredUserIds) {
                boolean userStillInAllChannels = true;
                
                for (ChallengeChannel channel : channels) {
                    if (!isUserMemberOfChallengeChannel(referredUserId, channel)) {
                        log.warn("Referred user {} is no longer in channel {} (referrer: {})", 
                            referredUserId, channel.getChannelName(), referrerChatId);
                        userStillInAllChannels = false;
                        break;
                    }
                }
                
                if (userStillInAllChannels) {
                    activeCount++;
                } else {
                    leftCount++;
                }
            }
            
            boolean allStillInChannels = (leftCount == 0);
            
            log.info("Referrer {} verification: total={}, active={}, left={}", 
                referrerChatId, totalReferrals, activeCount, leftCount);
            
            return new ReferralVerificationResult(allStillInChannels, totalReferrals, activeCount, leftCount);
            
        } catch (Exception e) {
            log.error("Error verifying referred users still in channels for referrer {}", referrerChatId, e);
            // On error, be lenient and return true to not block legitimate users
            return new ReferralVerificationResult(true, 0, 0, 0);
        }
    }
    
    // Removed: checkAndSendPrivateChannelLink and checkUserInChannel - now using challenge-specific methods
    
    /**
     * Check if chat ID is admin (delegates to TelegramBotUtils)
     */
    private boolean isAdmin(Long chatId) {
        return botUtils.isAdmin(chatId);
    }
    
    /**
     * Send message to user (delegates to TelegramBotUtils)
     */
    private void sendMessage(Long chatId, String text) {
        botUtils.sendMessage(this, chatId, text);
    }
    
    /**
     * Get full image path (delegates to TelegramBotUtils)
     */
    private String getFullImagePath(String relativePath) {
        return botUtils.getFullImagePath(relativePath);
    }
    
    /**
     * Send photo from file to user (delegates to TelegramBotUtils)
     */
    private void sendPhotoFromFile(Long chatId, String filePath) throws TelegramApiException {
        botUtils.sendPhotoFromFile(this, chatId, filePath);
    }
    
    /**
     * Edit message (delegates to TelegramBotUtils)
     */
    private void editMessage(Long chatId, Integer messageId, String text) {
        botUtils.editMessage(this, chatId, messageId, text);
    }
    
    /**
     * Delete a message (delegates to TelegramBotUtils)
     */
    private void deleteMessage(Long chatId, Integer messageId) {
        botUtils.deleteMessage(this, chatId, messageId);
    }
    
    /**
     * Set the challenge context for a user (used by AdminTelegramBot for broadcasts)
     */
    public void setUserChallengeContext(Long chatId, Long challengeId) {
        sessionService.setChallengeContext(chatId, challengeId);
    }
    
    // Uses InviteLinkService.createOneTimeInviteLink() to generate unique single-use invite links per user
}
