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
    private ReferralProperties referral = new ReferralProperties();
    
    @Data
    public static class AdminProperties {
        private List<Long> chatIds = new ArrayList<>();
    }
    
    @Data
    public static class ReferralProperties {
        private int requiredCount = 5;
    }
}
