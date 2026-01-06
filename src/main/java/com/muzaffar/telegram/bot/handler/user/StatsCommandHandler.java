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

        String statsMessage = messageService.createStatsMessage(
                botUsernameSupplier.get(),
                challengeUser.getReferralCode(),
                challengeUser.getReferralCount(),
                challenge.getRequiredReferralCount());
        botUtils.sendMessage(botSupplier.get(), chatId, statsMessage);
    }
}
