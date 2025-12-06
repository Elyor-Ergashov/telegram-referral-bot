package com.muzaffar.telegram.repository;

import com.muzaffar.telegram.entity.Challenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChallengeRepository extends JpaRepository<Challenge, Long> {
    
    @Query("SELECT c FROM Challenge c WHERE c.isActive = true")
    List<Challenge> findAllActiveChallenges();
    
    @Query("SELECT c FROM Challenge c WHERE c.isActive = true ORDER BY c.createdAt DESC")
    Optional<Challenge> findFirstActiveChallenge();
    
    @Query("SELECT c FROM Challenge c WHERE c.isMain = true AND c.isActive = true")
    Optional<Challenge> findMainChallenge();
    
    @Query("SELECT c FROM Challenge c ORDER BY c.createdAt DESC")
    List<Challenge> findAllOrderByCreatedAtDesc();
}
