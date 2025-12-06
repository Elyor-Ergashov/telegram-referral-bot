package com.muzaffar.telegram.repository;

import com.muzaffar.telegram.entity.Challenge;
import com.muzaffar.telegram.entity.ChallengeUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChallengeUserRepository extends JpaRepository<ChallengeUser, Long> {
    
    Optional<ChallengeUser> findByChallengeAndUserChatId(Challenge challenge, Long userChatId);
    
    Optional<ChallengeUser> findByReferralCode(String referralCode);
    
    List<ChallengeUser> findByChallenge(Challenge challenge);
    
    @Query("SELECT cu FROM ChallengeUser cu WHERE cu.challenge = :challenge AND cu.hasVerifiedChannels = true")
    List<ChallengeUser> findVerifiedUsersByChallenge(@Param("challenge") Challenge challenge);
    
    @Query("SELECT cu FROM ChallengeUser cu WHERE cu.challenge = :challenge AND cu.isCompleted = true")
    List<ChallengeUser> findCompletedUsersByChallenge(@Param("challenge") Challenge challenge);
    
    @Query("SELECT COUNT(cu) FROM ChallengeUser cu WHERE cu.challenge = :challenge")
    long countUsersByChallenge(@Param("challenge") Challenge challenge);
    
    @Query("SELECT COUNT(cu) FROM ChallengeUser cu WHERE cu.challenge = :challenge AND cu.isCompleted = true")
    long countCompletedUsersByChallenge(@Param("challenge") Challenge challenge);
    
    boolean existsByChallengeAndUserChatId(Challenge challenge, Long userChatId);
    
    List<ChallengeUser> findByUserChatId(Long userChatId);
}
