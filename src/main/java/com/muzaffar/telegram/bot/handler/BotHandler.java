package com.muzaffar.telegram.bot.handler;

import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Base interface for all bot handlers.
 * Handlers are responsible for processing specific types of updates.
 */
public interface BotHandler {

    /**
     * Check if this handler can process the given update.
     * 
     * @param update The Telegram update to check
     * @return true if this handler can process the update, false otherwise
     */
    boolean canHandle(Update update);

    /**
     * Process the given update.
     * This method should only be called if canHandle() returns true.
     * 
     * @param update The Telegram update to process
     */
    void handle(Update update);
}
