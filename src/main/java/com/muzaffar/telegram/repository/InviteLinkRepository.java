package com.muzaffar.telegram.repository;

import com.muzaffar.telegram.entity.Challenge;
import com.muzaffar.telegram.entity.InviteLink;
import com.muzaffar.telegram.entity.InviteLink.InviteLinkStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InviteLinkRepository extends JpaRepository<InviteLink, Long> {
    
    /**
     * Find all invite links for a user in a challenge
     */
    List<InviteLink> findByChallengeAndUserChatId(Challenge challenge, Long userChatId);
    
    /**
     * Find unused invite link for a user in a challenge
     */
    Optional<InviteLink> findByChallengeAndUserChatIdAndStatus(
            Challenge challenge, Long userChatId, InviteLinkStatus status);
    
    /**
     * Find invite link by the link string
     */
    Optional<InviteLink> findByInviteLink(String inviteLink);
    
    /**
     * Find all unused invite links for a challenge
     */
    @Query("SELECT i FROM InviteLink i WHERE i.challenge = :challenge AND i.status = 'UNUSED'")
    List<InviteLink> findUnusedLinksByChallenge(@Param("challenge") Challenge challenge);
    
    /**
     * Check if user already has an unused invite link for a challenge
     */
    boolean existsByChallengeAndUserChatIdAndStatus(
            Challenge challenge, Long userChatId, InviteLinkStatus status);
    
    /**
     * Count used invite links for a challenge
     */
    @Query("SELECT COUNT(i) FROM InviteLink i WHERE i.challenge = :challenge AND i.status = 'USED'")
    long countUsedLinksByChallenge(@Param("challenge") Challenge challenge);
}
