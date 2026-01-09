package com.muzaffar.telegram.bot.handler.user;

import static com.muzaffar.telegram.constants.BotConstants.*;

import com.muzaffar.telegram.bot.handler.BotHandler;
import com.muzaffar.telegram.entity.Challenge;
import com.muzaffar.telegram.entity.ChallengeUser;
import com.muzaffar.telegram.service.ChallengeService;
import com.muzaffar.telegram.service.ChallengeUserService;
import com.muzaffar.telegram.service.MessageService;
import com.muzaffar.telegram.util.TelegramBotUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.function.Supplier;

/**
 * Handler for /stats command - shows user statistics.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatsCommandHandler implements BotHandler {

    private final ChallengeService challengeService;
    private final ChallengeUserService challengeUserService;
    private final MessageService messageService;
    private final TelegramBotUtils botUtils;
    private final ChannelVerificationHandler channelVerificationHandler;

    private Supplier<TelegramLongPollingBot> botSupplier;
    private Supplier<String> botUsernameSupplier;

    /**
     * Initialize the handler with bot reference.
     */
    public void init(Supplier<TelegramLongPollingBot> botSupplier, Supplier<String> botUsernameSupplier) {
        this.botSupplier = botSupplier;
        this.botUsernameSupplier = botUsernameSupplier;
    }

    @Override
    public boolean canHandle(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return false;
        }
        return update.getMessage().getText().equals(STATS_COMMAND);
    }

    @Override
    public void handle(Update update) {
        handleStatsCommand(update.getMessage());
    }

    /**
     * Handle /stats command (challenge-aware)
     */
    private void handleStatsCommand(Message message) {
        Long chatId = message.getChatId();

        // Get default challenge
        Challenge challenge = challengeService.getDefaultChallenge().orElse(null);
        if (challenge == null) {
            botUtils.sendMessage(botSupplier.get(), chatId, "❌ Hozircha faol challenge yo'q.");
            return;
        }

        // Get user from challenge
        ChallengeUser challengeUser = challengeUserService.getUserInChallenge(challenge.getId(), chatId).orElse(null);
        if (challengeUser == null) {
            botUtils.sendMessage(botSupplier.get(), chatId, "❌ Iltimos, avval /start buyrug'ini yuboring.");
            return;
        }

        // Calculate detailed stats (Active vs Left)
        int totalReferrals = 0;
        int activeCount = 0;
        int leftCount = 0;

        try {
            java.util.List<Long> referredUserIds = challengeUserService.getValidReferredUserIds(
                    challenge.getId(), chatId);
            totalReferrals = referredUserIds.size();

            if (!referredUserIds.isEmpty()) {
                java.util.List<com.muzaffar.telegram.entity.ChallengeChannel> channels = challengeService
                        .getActiveChannelsForChallenge(challenge.getId());

                if (channels.isEmpty()) {
                    activeCount = totalReferrals;
                } else {
                    for (Long referredUserId : referredUserIds) {
                        boolean userStillInAllChannels = true;

                        for (com.muzaffar.telegram.entity.ChallengeChannel channel : channels) {
                            if (!channelVerificationHandler.isUserMemberOfChallengeChannel(referredUserId, channel)) {
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
                }
            }

            long indirectReferralCount = challengeUserService.getIndirectReferralCount(challenge.getId(), chatId);

            org.telegram.telegrambots.meta.api.methods.send.SendMessage statsMessage = messageService
                    .createStatsMessageWithButton(
                            challenge.getId(),
                            chatId,
                            botUsernameSupplier.get(),
                            challengeUser.getReferralCode(),
                            totalReferrals,
                            (int) indirectReferralCount,
                            activeCount,
                            leftCount,
                            challenge.getRequiredReferralCount());

            botSupplier.get().execute(statsMessage);

        } catch (Exception e) {
            log.error("Error calculating stats for user {}", chatId, e);
            botUtils.sendMessage(botSupplier.get(), chatId, "❌ Statistika hisoblashda xatolik yuz berdi.");
        }
    }
}
