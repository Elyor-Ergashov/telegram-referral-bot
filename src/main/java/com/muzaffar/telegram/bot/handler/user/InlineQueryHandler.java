package com.muzaffar.telegram.bot.handler.user;

import static com.muzaffar.telegram.constants.BotConstants.*;

import com.muzaffar.telegram.bot.handler.BotHandler;
import com.muzaffar.telegram.entity.Challenge;
import com.muzaffar.telegram.entity.ChallengeUser;
import com.muzaffar.telegram.service.ChallengeService;
import com.muzaffar.telegram.service.ChallengeUserService;
import com.muzaffar.telegram.util.TelegramBotUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.function.Supplier;

/**
 * Handler for inline queries - allows users to share referral links inline.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InlineQueryHandler implements BotHandler {

    private final ChallengeService challengeService;
    private final ChallengeUserService challengeUserService;
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
        return update.hasInlineQuery();
    }

    @Override
    public void handle(Update update) {
        handleInlineQuery(update.getInlineQuery());
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
                answerEmptyQuery(queryId);
                return;
            }

            ChallengeUser challengeUser = challengeUserService.getUserInChallenge(challenge.getId(), userId)
                    .orElse(null);
            if (challengeUser == null) {
                answerEmptyQuery(queryId);
                return;
            }

            String botUsername = botUsernameSupplier.get();
            String referralLink = String.format("https://t.me/%s?start=%s", botUsername,
                    challengeUser.getReferralCode());

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
                        referralLink);
            }

            messageContent.setMessageText(shareText);
            messageContent.setDisableWebPagePreview(false); // Enable preview
            article.setInputMessageContent(messageContent);

            // Answer the inline query
            AnswerInlineQuery answer = new AnswerInlineQuery();
            answer.setInlineQueryId(queryId);
            answer.setResults(List.of(article));
            answer.setCacheTime(300); // Cache for 5 minutes

            botSupplier.get().execute(answer);
            log.info("Answered inline query for userId={}", userId);

        } catch (TelegramApiException e) {
            log.error("Error handling inline query", e);
        }
    }

    private void answerEmptyQuery(String queryId) throws TelegramApiException {
        AnswerInlineQuery answer = new AnswerInlineQuery();
        answer.setInlineQueryId(queryId);
        answer.setResults(List.of());
        botSupplier.get().execute(answer);
    }
}
