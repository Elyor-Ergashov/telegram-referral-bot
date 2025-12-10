package com.muzaffar.telegram.repository;

import com.muzaffar.telegram.entity.UserSessionState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserSessionStateRepository extends JpaRepository<UserSessionState, Long> {
    
    Optional<UserSessionState> findByChatId(Long chatId);
    
    @Modifying
    @Query("DELETE FROM UserSessionState u WHERE u.expiresAt < :dateTime")
    void deleteByExpiresAtBefore(@Param("dateTime") LocalDateTime dateTime);
}
