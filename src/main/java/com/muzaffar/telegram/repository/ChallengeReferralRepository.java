package com.muzaffar.telegram.repository;

import com.muzaffar.telegram.entity.Challenge;
import com.muzaffar.telegram.entity.ChallengeReferral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChallengeReferralRepository extends JpaRepository<ChallengeReferral, Long> {
    
    Optional<ChallengeReferral> findByChallengeAndReferrerChatIdAndReferredChatId(
            Challenge challenge, Long referrerChatId, Long referredChatId);
    
    @Query("SELECT cr FROM ChallengeReferral cr WHERE cr.challenge = :challenge AND cr.referrerChatId = :referrerChatId")
    List<ChallengeReferral> findByReferrerAndChallenge(@Param("challenge") Challenge challenge, 
                                                         @Param("referrerChatId") Long referrerChatId);
    
    @Query("SELECT cr FROM ChallengeReferral cr WHERE cr.challenge = :challenge AND cr.referrerChatId = :referrerChatId AND cr.isValid = true")
    List<ChallengeReferral> findValidReferralsByReferrerAndChallenge(@Param("challenge") Challenge challenge, 
                                                                       @Param("referrerChatId") Long referrerChatId);
    
    @Query("SELECT COUNT(cr) FROM ChallengeReferral cr WHERE cr.challenge = :challenge AND cr.referrerChatId = :referrerChatId AND cr.isValid = true")
    long countValidReferralsByReferrerAndChallenge(@Param("challenge") Challenge challenge, 
                                                     @Param("referrerChatId") Long referrerChatId);
    
    boolean existsByChallengeAndReferrerChatIdAndReferredChatId(Challenge challenge, Long referrerChatId, Long referredChatId);
    
    /**
     * Check if the referred user has already been referred by anyone in this challenge.
     * Used to ensure each invited friend is counted only once per challenge.
     */
    boolean existsByChallengeAndReferredChatId(Challenge challenge, Long referredChatId);
    
    /**
     * Find the existing referral for a referred user in a challenge (regardless of referrer).
     * Returns the original referral if the user was already referred.
     */
    Optional<ChallengeReferral> findByChallengeAndReferredChatId(Challenge challenge, Long referredChatId);

    boolean existsByChallengeIdAndReferredChatId(Long challengeId, Long referredChatId);
}
