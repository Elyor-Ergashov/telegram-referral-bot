package com.muzaffar.telegram.repository;

import com.muzaffar.telegram.entity.Challenge;
import com.muzaffar.telegram.entity.ChallengeChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChallengeChannelRepository extends JpaRepository<ChallengeChannel, Long> {
    
    List<ChallengeChannel> findByChallenge(Challenge challenge);
    
    @Query("SELECT cc FROM ChallengeChannel cc WHERE cc.challenge = :challenge AND cc.isActive = true")
    List<ChallengeChannel> findActiveChannelsByChallenge(@Param("challenge") Challenge challenge);
    
    @Query("SELECT cc FROM ChallengeChannel cc WHERE cc.challenge.id = :challengeId AND cc.channelId = :channelId")
    Optional<ChallengeChannel> findByChallengeIdAndChannelId(@Param("challengeId") Long challengeId, 
                                                               @Param("channelId") String channelId);
    
    boolean existsByChallengeAndChannelId(Challenge challenge, String channelId);
    
    @Query("SELECT COUNT(cc) FROM ChallengeChannel cc WHERE cc.challenge = :challenge AND cc.isActive = true")
    long countActiveChannelsByChallenge(@Param("challenge") Challenge challenge);
}
