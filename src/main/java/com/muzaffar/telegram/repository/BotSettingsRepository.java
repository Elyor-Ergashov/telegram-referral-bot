package com.muzaffar.telegram.repository;

import com.muzaffar.telegram.entity.BotSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BotSettingsRepository extends JpaRepository<BotSettings, Long> {
}
