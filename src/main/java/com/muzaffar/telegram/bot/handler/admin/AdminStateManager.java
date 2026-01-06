package com.muzaffar.telegram.bot.handler.admin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * Manages admin state for the admin bot.
 * Handles user states, user data, temp messages, and current challenge context.
 */
@Slf4j
@Component
public class AdminStateManager {

    private final Map<Long, String> userStates = new HashMap<>();
    private final Map<Long, Map<String, String>> userData = new HashMap<>();
    private final Map<Long, List<Integer>> tempMessageIds = new HashMap<>();
    private final Map<Long, Integer> currentMenuMessageIds = new HashMap<>();
    private final Map<Long, Long> currentChallengeId = new HashMap<>();

    // ==================== User States ====================

    public void setState(Long chatId, String state) {
        userStates.put(chatId, state);
        log.debug("Set state for chatId={}: {}", chatId, state);
    }

    public String getState(Long chatId) {
        return userStates.get(chatId);
    }

    public void clearState(Long chatId) {
        userStates.remove(chatId);
        log.debug("Cleared state for chatId={}", chatId);
    }

    public boolean hasState(Long chatId) {
        return userStates.containsKey(chatId);
    }

    // ==================== User Data ====================

    public void initUserData(Long chatId) {
        userData.put(chatId, new HashMap<>());
    }

    public void setUserData(Long chatId, String key, String value) {
        Map<String, String> data = userData.computeIfAbsent(chatId, k -> new HashMap<>());
        data.put(key, value);
    }

    public String getUserData(Long chatId, String key) {
        Map<String, String> data = userData.get(chatId);
        return data != null ? data.get(key) : null;
    }

    public Map<String, String> getAllUserData(Long chatId) {
        return userData.get(chatId);
    }

    public void clearUserData(Long chatId) {
        userData.remove(chatId);
        log.debug("Cleared user data for chatId={}", chatId);
    }

    // ==================== Menu Message IDs ====================

    public void setMenuMessageId(Long chatId, Integer messageId) {
        currentMenuMessageIds.put(chatId, messageId);
    }

    public Integer getMenuMessageId(Long chatId) {
        return currentMenuMessageIds.get(chatId);
    }

    public void clearMenuMessageId(Long chatId) {
        currentMenuMessageIds.remove(chatId);
    }

    // ==================== Temp Message Tracking ====================

    public void trackTempMessage(Long chatId, Integer messageId) {
        tempMessageIds.computeIfAbsent(chatId, k -> new ArrayList<>()).add(messageId);
    }

    public List<Integer> getTempMessageIds(Long chatId) {
        return tempMessageIds.getOrDefault(chatId, new ArrayList<>());
    }

    public void clearTempMessages(Long chatId) {
        tempMessageIds.remove(chatId);
    }

    // ==================== Current Challenge ====================

    public void setCurrentChallengeId(Long chatId, Long challengeId) {
        currentChallengeId.put(chatId, challengeId);
        log.debug("Set current challenge for chatId={}: {}", chatId, challengeId);
    }

    public Long getCurrentChallengeId(Long chatId) {
        return currentChallengeId.get(chatId);
    }

    public void clearCurrentChallenge(Long chatId) {
        currentChallengeId.remove(chatId);
    }

    // ==================== Full Cleanup ====================

    /**
     * Clear all state and data for a user
     */
    public void clearAll(Long chatId) {
        clearState(chatId);
        clearUserData(chatId);
        clearMenuMessageId(chatId);
        clearTempMessages(chatId);
        // Note: currentChallengeId is not cleared as it's used for context
        log.info("Cleared all state for chatId={}", chatId);
    }
}
