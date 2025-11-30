package com.muzaffar.telegram.repository;

import com.muzaffar.telegram.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * Find user by Telegram chat ID
     */
    Optional<User> findByChatId(Long chatId);
    
    /**
     * Find user by referral code
     */
    Optional<User> findByReferralCode(String referralCode);
    
    /**
     * Check if user exists by chat ID
     */
    boolean existsByChatId(Long chatId);
    
    /**
     * Find all users invited by a specific user
     */
    List<User> findByInvitedByUserId(Long invitedByUserId);
    
    /**
     * Count referrals for a user
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.invitedByUserId = :userId")
    long countReferralsByUserId(Long userId);
    
    /**
     * Get all users for broadcast
     */
    @Query("SELECT u FROM User u ORDER BY u.createdAt DESC")
    List<User> findAllUsers();
}
