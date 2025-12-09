package com.muzaffar.telegram.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "bot")
public class AppConfig {
    
    private AdminProperties admin = new AdminProperties();
    private ImagesProperties images = new ImagesProperties();
    private InviteLinkProperties inviteLink = new InviteLinkProperties();
    
    @Data
    public static class AdminProperties {
        private List<Long> chatIds = new ArrayList<>();
    }
    
    @Data
    public static class ImagesProperties {
        private String basePath = "";
    }
    
    @Data
    public static class InviteLinkProperties {
        /**
         * Expiration time in hours for one-time invite links.
         * Set to 0 or null for no expiration.
         */
        private Integer expireHours = 24;
        
        /**
         * Maximum number of users that can use the invite link.
         * Telegram allows 1-99999. Set to 1 for true one-time links.
         */
        private Integer memberLimit = 1;
    }
}
