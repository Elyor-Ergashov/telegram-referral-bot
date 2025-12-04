package com.muzaffar.telegram.util;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent API builder for creating Telegram inline keyboards.
 * Eliminates repetitive keyboard creation code throughout the application.
 * 
 * Example usage:
 * <pre>
 * InlineKeyboardMarkup keyboard = new KeyboardBuilder()
 *     .addButton("Button 1", "callback_1")
 *     .addButton("Button 2", "callback_2")
 *     .newRow()
 *     .addUrlButton("Visit Website", "https://example.com")
 *     .build();
 * </pre>
 */
public class KeyboardBuilder {
    
    private final List<List<InlineKeyboardButton>> rows = new ArrayList<>();
    private List<InlineKeyboardButton> currentRow = new ArrayList<>();
    
    /**
     * Add a callback button to the current row.
     * 
     * @param text The button text to display
     * @param callbackData The callback data to send when clicked
     * @return this builder for method chaining
     */
    public KeyboardBuilder addButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        currentRow.add(button);
        return this;
    }
    
    /**
     * Add a URL button to the current row.
     * 
     * @param text The button text to display
     * @param url The URL to open when clicked
     * @return this builder for method chaining
     */
    public KeyboardBuilder addUrlButton(String text, String url) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setUrl(url);
        currentRow.add(button);
        return this;
    }
    
    /**
     * Add a switch inline query button to the current row.
     * 
     * @param text The button text to display
     * @param query The inline query to switch to
     * @return this builder for method chaining
     */
    public KeyboardBuilder addSwitchInlineButton(String text, String query) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setSwitchInlineQuery(query);
        currentRow.add(button);
        return this;
    }
    
    /**
     * Finish the current row and start a new one.
     * 
     * @return this builder for method chaining
     */
    public KeyboardBuilder newRow() {
        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
            currentRow = new ArrayList<>();
        }
        return this;
    }
    
    /**
     * Add a row with a single button (convenience method).
     * 
     * @param text The button text to display
     * @param callbackData The callback data to send when clicked
     * @return this builder for method chaining
     */
    public KeyboardBuilder addRow(String text, String callbackData) {
        newRow();
        return addButton(text, callbackData);
    }
    
    /**
     * Add a row with a single URL button (convenience method).
     * 
     * @param text The button text to display
     * @param url The URL to open when clicked
     * @return this builder for method chaining
     */
    public KeyboardBuilder addUrlRow(String text, String url) {
        newRow();
        return addUrlButton(text, url);
    }
    
    /**
     * Build the final InlineKeyboardMarkup.
     * Automatically adds the current row if it's not empty.
     * 
     * @return The constructed InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup build() {
        // Add current row if not empty
        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        keyboard.setKeyboard(rows);
        return keyboard;
    }
    
    /**
     * Create a simple keyboard with a single button.
     * 
     * @param text The button text
     * @param callbackData The callback data
     * @return The constructed InlineKeyboardMarkup
     */
    public static InlineKeyboardMarkup singleButton(String text, String callbackData) {
        return new KeyboardBuilder()
            .addButton(text, callbackData)
            .build();
    }
    
    /**
     * Create a simple keyboard with a single URL button.
     * 
     * @param text The button text
     * @param url The URL
     * @return The constructed InlineKeyboardMarkup
     */
    public static InlineKeyboardMarkup singleUrlButton(String text, String url) {
        return new KeyboardBuilder()
            .addUrlButton(text, url)
            .build();
    }
}
